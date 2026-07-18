# fjorth — Implementation Log

Companion to [PLAN.md](PLAN.md). One section per phase: what was executed, what was
learned, and where and why the implementation deviated from the plan. All phases are
complete; the suite stands at 98 tests.

## Phase 0 — Project scaffolding

**Executed.** `application` plugin with `Repl` as main class, stdin wired into the
`run` task, Java toolchain pinned to 25. Executed partly out of order: the toolchain
and application plugin landed during Phase 2, when the REPL first needed an entry
point — before that there was nothing to run.

**Learnings**

- The environment's `JAVA_HOME` pointed to a removed JDK (`25.0.2-graalce`), so
  `./gradlew` fails as-is; builds run with
  `JAVA_HOME=~/.sdkman/candidates/java/current`. Pinning the Gradle toolchain
  insulates compilation from this, but the wrapper itself still needs a valid
  `JAVA_HOME` to launch.

**Changes from plan**

- None in substance. The plan said "toolchain 21+"; 25 was chosen (LTS, installed).

## Phase 1 — Core types

**Executed.** `Word` (sealed interface over records: `Primitive`, `Colon`, `Branch`,
`ZeroBranch`), `Machine` (data/return stacks as `long[]` with pointers, STATE flag),
`Dictionary` (persistent chain, case-insensitive newest-first lookup),
`ForthException`. 16 tests: stack discipline, underflow/overflow, shadowing,
structural sharing.

**Learnings**

- The persistent-chain dictionary gives the two hard Forth requirements — shadowing
  and stable compiled references — for free, with no special cases. Each `define`
  returns a new head; compiled bodies hold direct `Word` references, so redefinition
  cannot disturb old callers. This decision never needed revisiting.
- Branch targets were made absolute body indices rather than relative offsets
  (plan sketch said "offset"). Absolute targets make backpatching a single
  `set(index, newBranch)` with no arithmetic.

**Changes from plan**

- The "definition under construction" reference planned for `Machine` was deferred
  to Phase 3, where its actual shape (name + mutable accumulator) is determined by
  the compiler. Adding it in Phase 1 would have been speculative. It ended up on
  `Interpreter`, not `Machine` — see Phase 3.

## Phase 2 — Outer interpreter + primitives

**Executed.** `Interpreter` (stateful per-line tokenizer, lookup → execute → number
fallback), `Primitives` (34 words as a `List<Word>` reduced onto an empty
dictionary, with `unary`/`binary` combinators), `Repl` (line loop, ` ok`, error
recovery). Verified end-to-end with a piped REPL session.

**Learnings**

- **`Word.Effect` had to change signature** from `apply(Machine)` to
  `apply(Interpreter)`. Words like `."`, `(`, `\` consume input, and `.`/`EMIT`
  produce output; `Machine` has neither. The interpreter, not the machine, is the
  real execution context. This is the one Phase 1 decision that did not survive
  contact with implementation.
- The tokenizer must consume exactly one trailing delimiter character after each
  token. That detail is what makes `." hello"` print without a leading space:
  the space after `."` is the token delimiter, not part of the string.
- The colon-body executor (indexed loop with branch handling) was written in this
  phase although nothing produced `Colon` words yet — the exhaustive switch over
  the sealed `Word` type forced all cases to be handled anyway, so the real
  implementation cost nothing extra and made Phases 3–4 lighter.
- A test expectation error, not an implementation error, caused the only failure:
  `2DUP` duplicates the top two cells (`a b -- a b a b` from the top pair), which
  was mis-asserted. Stack-effect tracing by hand is error-prone; small single-step
  assertions beat long chained expressions (relearned in Phase 6).

**Changes from plan**

- `Effect` signature change, as above.
- Word count 34 rather than ~30; `NIP`/`TUCK`/`2DUP` later moved to Forth in Phase 6.

## Phase 3 — Compilation

**Executed.** `:` / `;` / STATE-aware interpret loop, `Definition` accumulator
(mutable `ArrayList` while open, sealed to immutable `Word.Colon` by `;`),
`Word.Literal`, `IMMEDIATE`, `CONSTANT`, `VARIABLE`, plus `@`, `!` and cell memory
on `Machine`. Guards: `:` inside a definition, `;` outside, missing name.
Definitions may span lines.

**Learnings**

- **`VARIABLE` forced the memory model forward.** A variable without an address
  cannot be read or written; "dictionary entries holding state" (the plan's Phase 3
  wording) is not implementable without either addresses or non-standard `@`/`!`.
  Conclusion: in Forth, variables and memory are one feature, not two phases.
- **Comment and string words must be immediate.** `(`, `\`, `."` executed fine in
  interpret mode but would have been *compiled* inside `: ... ;`, leaving comment
  text to be interpreted as code. Immediacy is not an optimization for these words;
  it is their correctness requirement. `."` additionally needs state-dependent
  behavior: print now when interpreting, compile a printing closure when compiling.
- `IMMEDIATE` composes cleanly with the persistent dictionary: rather than mutating
  the sealed word, it defines an immediate copy that shadows the original. No
  mutation, same observable semantics.
- Error recovery must discard an open definition (`Interpreter.reset()`), or a
  failed compilation leaves the interpreter wedged in compile state.

**Changes from plan**

- Memory substrate (`long[]` cells, `allot`, `fetch`/`store`, `@`, `!`) pulled
  forward from Phase 5, for the reason above. PLAN.md was updated at the time.
- The definition-under-construction lives on `Interpreter` (which owns the
  dictionary), not on `Machine` as Phase 1 originally sketched. Only the boolean
  STATE flag is on `Machine`.

## Phase 4 — Control flow

**Executed.** `IF`/`ELSE`/`THEN`, `BEGIN`/`UNTIL`/`WHILE`/`REPEAT`,
`DO`/`LOOP`/`+LOOP`/`I`/`J`/`LEAVE`, `EXIT`, `RECURSE`. Compile-time backpatch
positions travel on the data stack, as planned. Internal runtime words `(do)`,
`(loop)`, `(+loop)`, `(unloop)` handle loop parameters on the return stack.
Seal-time validation rejects unresolved branches and unterminated `DO`.
20 tests, including factorial, fibonacci, nested loops with `J`, all passing on
the first run.

**Learnings**

- The data-stack discipline for backpatching covers every structured construct
  except `LEAVE`. `LEAVE` needs an unknown number of forward patches per loop,
  scoped per nesting level — that requires a compile-time structure
  (`Deque<List<Integer>>` of leave sites in `Definition`), not a stack of single
  positions. The plan's "use the data stack" was right for 90% and silent on the
  rest.
- `LEAVE` must compile `(unloop)` before its branch, or it exits the loop with
  limit/index still on the return stack. Return-stack hygiene is asserted in tests.
- `EXIT` fell out of the executor's loop condition for free: compile
  `Branch(Integer.MAX_VALUE)`; `ip < body.size()` terminates the current word only.
  No exception, no sentinel type.
- `RECURSE` has a chicken-and-egg problem — the definition is not sealed when the
  self-call is compiled. Solved with a one-element array captured by a closure and
  filled in by `seal()`. A test pins the important semantics: `RECURSE` binds to
  the definition under construction, not to a later shadowing redefinition.
- Loop exit conditions can be encoded on the data stack: `(loop)` pushes a
  continue/exit flag consumed by an ordinary `ZeroBranch` back to the body start,
  so no new branch types were needed.

**Changes from plan**

- Leave-site tracking added to `Definition` (see above).
- Seal-time validation added (unresolved branch, unterminated `DO`) — not planned,
  but it converts wild runtime jumps from malformed definitions into compile errors.
- `+LOOP` initially used simplified sign-dependent termination rather than ANS
  boundary-crossing semantics. Corrected after Phase 6: termination is now
  `(index < limit) != (index + increment < limit)` — the index crossing the
  boundary between `limit-1` and `limit` in either direction. This is
  ANS-correct except at 64-bit wraparound (which would require the biased-index
  overflow trick and change `I`/`J`/`R@` representation). Observable fix:
  `0 10 DO I . -1 +LOOP` now includes 0, and termination no longer depends on
  the increment's sign.

## Phase 5 — Memory model

**Executed.** `HERE`, `ALLOT`, `CELLS`, `+!`, `,` (comma), `CREATE`. Negative
`ALLOT` reclaims memory, guarded against going below address zero.

**Learnings**

- With cell-addressed memory, `CELLS` is the identity function. It was kept anyway
  so idiomatic source (`10 CELLS ALLOT`) reads normally — a vocabulary-compatibility
  decision, not a functional one.
- `CREATE` without `,` is half a feature: comma is what turns "name an address"
  into "build initialized data" (`CREATE PRIMES 2 , 3 , 5 ,`). The plan omitted it;
  it cost four lines.

**Changes from plan**

- Most of this phase had already migrated to Phase 3; what remained matched the
  (updated) plan plus `,`.
- `DOES>` deferred, as the plan explicitly allowed. Concrete blocker, recorded in
  PLAN.md: the runtime half of `DOES>` must capture "the rest of the currently
  executing colon body," and the executor's instruction pointer is a local variable
  invisible to primitives. Implementing it means exposing an execution frame —
  a deliberate architectural step, not a word definition.

## Phase 6 — Polish

**Executed.** Bootstrap file `fjorth.fs` loaded at startup via a new `Bootstrap`
factory; `2DUP`, `2DROP`, `NIP`, `TUCK`, `NEGATE`, `ABS`, `MIN`, `MAX` moved from
Java to Forth; new Forth-only words `1+`, `1-`, `0<`, `0>`, `<>`, `TRUE`, `FALSE`,
`?DUP`, `CELL+`, `SPACE`, `SPACES`. `WORDS` (deduplicated listing) and `SEE`
(decompiler) added. Errors now carry the source line and a caret under the failing
token.

**Learnings**

- Routing every test fixture through `Bootstrap` means the whole suite re-validates
  the bootstrap file on every run — the "define the language in itself" test the
  plan wanted, obtained structurally rather than as a separate test.
- Moving words from Java to Forth was a genuine validation step: `ABS`, `MIN`, `MAX`
  exercise `IF`/`THEN` inside the bootstrap, and `SPACES` exercises
  `BEGIN`/`WHILE`/`REPEAT`, before any user input is read.
- `SEE` is honest about the representation: straight-line words render as source
  (`: SQUARE DUP * ;`), branchy words render as an indexed listing
  (`3: 0branch -> 7`) because branch targets are body indices and reconstructing
  `IF`/`ELSE`/`THEN` from them is a decompilation problem not worth solving.
- Adding position context to error messages broke four exact-match test assertions —
  the cost of asserting on full message strings. `startsWith` on the semantic part
  is the better default.
- Second occurrence of the Phase 2 lesson: the only test failure in this phase was
  again a hand-traced stack-effect error in a long chained test expression, not an
  implementation bug.

**Changes from plan**

- Error position implemented as line + caret rather than a position number — more
  useful in a REPL.
- `\ ( ."` had already become immediate in Phase 3; the bootstrap relies on this
  for its stack-effect comments.

## Cross-phase observations

- **Phase discipline held where it mattered and bent where reality required.** The
  two early decisions the plan called expensive to retrofit — flat threaded bodies
  with an explicit instruction pointer, and the persistent dictionary — were both
  correct and unchanged. The deviations (memory model timing, `Effect` signature,
  leave-site tracking) were all cases where a planned boundary cut through a single
  feature.
- **The sealed `Word` hierarchy earned its keep.** Exhaustive switches forced every
  execution case to be handled when first written, which is why the Phase 2 executor
  was already Phase 4-ready and Phase 4's 20 tests passed on the first run.
- **Mutability stayed confined** to `Machine` (inherently stateful), the open
  `Definition`, and the `Interpreter`'s dictionary head reference. Everything
  published — words, bodies, dictionary nodes — is immutable, per the original
  design constraint.
- **Every implementation-side test failure across all phases (2 of 2) was a wrong
  test expectation from hand-tracing stack effects**, never a wrong implementation.
  The interpreter-driven test harness (`stackAfter(String)` → `long[]`) proved the
  right level of abstraction: tests read as Forth transcripts.
