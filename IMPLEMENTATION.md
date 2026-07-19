# fjorth — Implementation Log

Companion to [PLAN.md](PLAN.md). One section per phase: what was executed, what was
learned, and where and why the implementation deviated from the plan. All phases are
complete; post-plan work is logged at the end. The suite stands at 134 tests.

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

## Post-plan work

### ANS-correct LOOP/+LOOP termination (2026-07-18)

`loopStep` replaced the sign-dependent test with the boundary-crossing rule:
terminate when `(index < limit) != (index + increment < limit)`. Details in the
Phase 4 "Changes from plan" entry. Observable fixes: down-counting loops include
the limit; termination is direction-independent. Known consequence: `0 0 DO`
now loops ~2^64 times, which is ANS-correct (`?DO` is the standard's remedy and
is not implemented).

### String/number polish (2026-07-19)

`BASE`, `HEX`, `DECIMAL`, `S"`, `TYPE`, `.R` added.

- `BASE` is a true variable: `Machine` reserves memory cell 0 at construction
  (so `here` starts at 1), initialized to 10, validated 2–36 on read. Because it
  is a real address, `HEX` and `DECIMAL` are one-liners in `fjorth.fs`, and all
  of parsing (`Interpreter.number`) and formatting (`.`, `.S`, `.R` via
  `Primitives.formatted`, uppercase) share it.
- `S"` needed no new `Word` variant: it copies the string into cell memory (one
  char per cell) at parse/compile time and pushes/compiles two plain `Literal`s
  (addr, len). Allocation is permanent in both modes — simple, predictable, and
  each *interpreted* use consumes cells; compiled uses allocate once.
- Learnings: compiled literals are immune to later `BASE` changes (values, not
  text — a test pins this); reserving cell 0 broke one `Machine` test that
  assumed all memory cells were free, the only fallout.

### DOES> (2026-07-19)

Implemented via **compile-time split**, avoiding the execution-frame refactor
that caused its Phase 5 deferral. The classic implementation patches the created
word's code field at run time, which requires the running `(does>)` to capture
"the rest of the currently executing body" — impossible here because `run()`'s
instruction pointer is a local. The split makes the problem disappear:

- `DOES>` is immediate; it calls `Definition.beginTail()`, redirecting all
  further compilation (including branch backpatching — `append`/`size`/`resolve`
  now operate on the active list) into a separate tail list.
- `seal()` wraps the tail in its own anonymous `Colon` and appends a `(does>)`
  primitive to the build body, closing over that Colon.
- At run time, `(does>)` redefines the latest dictionary word by composition:
  new behavior = execute old word (a CREATEd word pushes its data address),
  then execute the tail. Shadowing via the persistent dictionary — no mutation.

Because the tail never lives in the build body, `DOES>` needs no EXIT semantics:
the defining word simply ends after `(does>)`. Guards: one `DOES>` per
definition; `DO`..`LOOP` may not span the split; unresolved branches in the
tail are caught at seal. Not guarded: `IF` spanning the split (backpatch indices
would cross lists); composition is permissive about what the latest word is
(ANS restricts to CREATEd words).

Learnings: the "hard" classic formulation was an artifact of the threaded-code
model — in a structure where definitions are values, splitting at compile time
is both simpler and more in keeping with the codebase (the tail is just another
immutable `Colon`, and retrofit is dictionary shadowing, which fjorth already
had). All 11 new tests passed on the first run, including `: ARRAY CREATE CELLS
ALLOT DOES> + ;` and control flow inside the tail.

### EVALUATE (2026-07-19)

`EVALUATE ( addr u -- )` interprets a cell string as source. The tokenizer
became reentrant the simple way: `Interpreter.evaluate(String)` saves and
restores `input`/`pos`/`tokenStart` around the shared token loop, so
evaluations nest arbitrarily (a test runs EVALUATE inside a word invoked by an
outer EVALUATE). The primitive shares the cell-string reader (`poppedString`)
with `TYPE`.

Error location moved into `ForthException` as a once-only operation
(`locate(line, position)` is a no-op if already located), applied by the token
loop at every nesting level — innermost wins, so an error inside evaluated text
points into that text rather than the outer line.

Learnings: the one test failure was again a wrong expectation, and this time it
was semantically instructive — `S" 42 ;" EVALUATE` typed while a definition is
open does NOT close it, because EVALUATE is not immediate and gets compiled
into the open definition. That is faithful ANS behavior, not a bug; the test
was rewritten to assert what it actually meant (STATE set inside an evaluation
persists after it).

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
