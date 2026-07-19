# fjorth — Session Context

Snapshot for resuming work. State as of 2026-07-18. Companion documents:
[PLAN.md](PLAN.md) (phase plan, all phases `Done`), [IMPLEMENTATION.md](IMPLEMENTATION.md)
(per-phase execution log, learnings, deviations).

## What this is

A Forth implementation in Java 25, built in 7 phases (0–6), all complete.
Gradle project `fjorth`, group `com.github.kjetilv`, package `com.github.kjetilv.fjorth`.
115 tests, all passing. Working REPL. Post-plan work so far: ANS boundary-crossing
LOOP/+LOOP termination; BASE/HEX/DECIMAL, S", TYPE, .R (see IMPLEMENTATION.md
"Post-plan work").

## Build and run

```
JAVA_HOME=~/.sdkman/candidates/java/current ./gradlew test
JAVA_HOME=~/.sdkman/candidates/java/current ./gradlew -q run --console=plain
```

- The environment's `JAVA_HOME` points to a REMOVED JDK (`25.0.2-graalce`); the
  override above is required for every `./gradlew` invocation. Installed JDKs:
  25.0.3-graal, 25.0.3-tem, 26.0.1-oracle, 26.0.1-zulu (sdkman). Gradle toolchain
  is pinned to Java 25 in `build.gradle.kts`.
- REPL testing pattern: `printf '...\n...' | ./gradlew -q run --console=plain`.
- JUnit 6 (jupiter), `useJUnitPlatform()`.

## Git state

Initial commit does not exist yet. The Gradle skeleton files are staged (git status
`A`); all source, tests, resources, and the .md files are untracked. Nothing has
been committed. User has not asked for commits.

## Source inventory (src/main/java/com/github/kjetilv/fjorth/)

- **`Word.java`** — sealed interface, `name()` + `immediate()`. Variants (records):
  - `Primitive(String name, boolean immediate, Effect effect)`
  - `Colon(String name, boolean immediate, List<Word> body)` — compact constructor
    does `List.copyOf`
  - `Literal(long value)` — pushes value; name `(literal)`
  - `Branch(int target)`, `ZeroBranch(int target)` — target is an ABSOLUTE body
    index; `-1` = unresolved placeholder; `Integer.MAX_VALUE` = EXIT sentinel
  - Nested `@FunctionalInterface Effect { void apply(Interpreter); }` — takes
    Interpreter, NOT Machine (changed in Phase 2: parsing/IO words need it)
- **`Machine.java`** — mutable interpreter state: data stack + return stack
  (`long[]` + top index, default 256, ctor-configurable), cell memory
  (`long[]`, default 4096 cells) with `here`/`allot(int)`/`fetch`/`store`
  (bounds-checked, `allot` guards negative below zero), STATE flag
  (`compiling()`/`compiling(boolean)`), `peekReturn(int offset)` (0 = top; J uses
  offset 2), `stack()` returns copy bottom-first, `reset()` clears stacks + STATE
  but NOT memory/here.
- **`Dictionary.java`** — persistent chain (word + parent), `EMPTY` singleton.
  `define(Word)` returns new head; `lookup(String)` walks newest-first,
  case-insensitive (`equalsIgnoreCase`); `latest()`; `words()` returns
  `Stream<Word>` newest-first (used by WORDS). Shadowing works because compiled
  bodies hold direct Word references.
- **`Definition.java`** (package-private) — the open colon definition during
  compilation. Mutable `ArrayList<Word> body`; `append`, `size`, `resolve(index,
  target)` (re-creates the branch record at index, preserving its kind);
  `beginLoop()`/`addLeave(index)`/`endLoop()` — a `Deque<List<Integer>>` of LEAVE
  sites per DO-nesting level; `recurse()` returns a primitive closing over a
  one-element `Word[] self` that `seal()` fills with the finished Colon;
  `seal()` validates (no unterminated DO, no branch target < 0) then returns
  immutable `Word.Colon`.
- **`Interpreter.java`** — outer interpreter + executor. Holds `Machine`,
  mutable `Dictionary` head, `PrintWriter out`, current `Definition`, and
  tokenizer state (`input`, `pos`, `tokenStart`).
  - `interpret(String line)`: token loop; wraps any ForthException with context
    (`message\n<input line>\n<spaces>^` caret at `tokenStart`); flushes out.
  - `handle(token)`: lookup → if compiling && !immediate → append, else execute;
    number fallback (`Long.parseLong`) → Literal when compiling, push otherwise;
    unknown → `token ?`.
  - `execute(Word)`: switch — Primitive applies effect, Colon runs, Literal
    pushes, bare Branch/ZeroBranch throw.
  - `run(Colon)`: indexed loop, local `int ip`; Branch → ip = target;
    ZeroBranch → pop, jump if 0; nested Colons recurse in Java (JVM call stack
    is the return-address stack; explicit return stack only for >R/R>/loops).
  - Tokenizer: `nextToken()` skips leading whitespace, reads to whitespace,
    consumes EXACTLY ONE trailing delimiter char (this is what makes `." x"`
    not print a leading space); `readUntil(char)`, `readRestOfLine()`,
    `word(String requester)` (next token or "requester: missing name").
  - Compiler API: `beginDefinition(name)`, `endDefinition()`, `append(Word)`,
    `open()` (package-private, throws "compilation outside definition"),
    `define(Word)`, `makeLatestImmediate()` (shadows latest Colon with immediate
    copy; non-Colon → error), `dictionary()`, `reset()` (machine reset + drop
    open definition — REPL calls this on error).
- **`Primitives.java`** — all built-in words as a `List<Word>` reduced onto
  `Dictionary.empty()`. Helpers: `primitive(name, effect)`, `immediate(name,
  effect)`, `unary(LongUnaryOperator)`, `binary(LongBinaryOperator)` (pops b then
  a, pushes op(a,b)), `flag(boolean)` → -1/0. Static runtime words `DO_RUNTIME
  (do)`, `LOOP_RUNTIME (loop)`, `PLUS_LOOP_RUNTIME (+loop)`, `UNLOOP_RUNTIME
  (unloop)`; `loopStep(machine, index, ascending)` pushes 0 (continue) / -1
  (exit, drops params) consumed by a ZeroBranch back to body start;
  `closeLoop(interpreter, runtime)` shared by LOOP/+LOOP. `render`/`renderColon`/
  `cell` implement SEE.
- **`Bootstrap.java`** — `interpreter(Machine, PrintWriter)` builds an Interpreter
  over `Primitives.dictionary()` then interprets each line of the resource
  `fjorth.fs` (classpath, same package). ALL test fixtures and the REPL go through
  this, so the bootstrap is validated by the whole suite.
- **`Repl.java`** — main. Prints `fjorth` banner; per line: interpret + ` ok`, on
  ForthException print message (already has caret context) + `interpreter.reset()`.
  Dictionary/memory survive errors.
- **`ForthException.java`** — RuntimeException with message only.

## Resources

- **`src/main/resources/com/github/kjetilv/fjorth/fjorth.fs`** — bootstrap, one
  definition per line, `\`-comment header. Defines IN FORTH: `2DUP 2DROP NIP TUCK
  NEGATE ABS MIN MAX 1+ 1- 0< 0> <> TRUE FALSE ?DUP CELL+ HEX DECIMAL SPACE
  SPACES`.
  These are NOT in Primitives.java (moved out in Phase 6).

## Word inventory (Java primitives)

- Stack: `DUP DROP SWAP OVER ROT`
- Arithmetic: `+ - * / MOD` (division by zero → error)
- Comparison/logic: `= < > 0= AND OR XOR INVERT` (truth: -1/0)
- Return stack: `>R R> R@ I J`
- I/O: `. .R .S EMIT CR TYPE ." S" ( \` (`."`, `S"`, `(`, `\` immediate; `."` is
  state-dependent: prints when interpreting, compiles a printing closure when
  compiling; `S"` copies the string into cell memory — one char per cell,
  PERMANENTLY allotted at parse/compile time — and yields/compiles addr + len as
  two Literals; `TYPE` is `( addr u -- )`; `.R` is `( n width -- )` right-aligned,
  no trailing space; `.`/`.R`/`.S` format via BASE, uppercase)
- Compiler: `: ; IMMEDIATE CONSTANT VARIABLE` (`;` immediate)
- Memory: `@ ! +! HERE ALLOT CELLS , CREATE BASE` (CELLS is identity —
  cell-addressed memory; VARIABLE allots 1 cell and pushes address; CREATE pushes
  HERE at creation, allots nothing; `,` stores + allots 1; BASE pushes the
  reserved cell-0 address — `Machine` reserves it at construction, initialized
  to 10, so `here` starts at 1; `Machine.base()` validates 2–36 and is used by
  number parsing (`Interpreter.number` → `Long.parseLong(token, base)`) and
  output formatting (`Primitives.formatted`))
- Control flow (all immediate): `IF ELSE THEN BEGIN UNTIL WHILE REPEAT DO LOOP
  +LOOP LEAVE EXIT RECURSE`
- Tools: `WORDS` (deduplicated, newest first) `SEE` (one-line for straight-line
  bodies; indexed listing when body contains branches; `IMMEDIATE` suffix;
  `exit` for MAX_VALUE branch; `NAME ( primitive )` for primitives)

## Compile-time mechanics (for extending control flow)

Backpatch positions travel on the DATA stack at compile time: IF/WHILE push the
index of their placeholder ZeroBranch(-1); THEN/REPEAT pop and `resolve`; ELSE
patches IF's and pushes its own Branch(-1) index; BEGIN pushes a destination
index; UNTIL/REPEAT compile backward branches to it. DO pushes body-start index
AND opens a leave scope in Definition; LOOP/+LOOP pop the index, compile
runtime word + ZeroBranch back, then resolve all LEAVE sites to just past
themselves. LEAVE compiles `(unloop)` + Branch(-1) and registers the site.
Loop params live on the RETURN stack: `(do)` pushes limit then index (index on
top, so R@ = I; J = peekReturn(2)).

## Known limitations / deferred items

- **`DOES>` not implemented.** Blocker: its runtime must capture "rest of the
  currently executing colon body"; `run()`'s ip is a local variable invisible to
  primitives. Needs an exposed execution frame. Recorded in PLAN.md Phase 5.
- **`+LOOP`/`LOOP` termination**: boundary-crossing test
  `(index < limit) != (next < limit)` in `Primitives.loopStep` — ANS-correct
  except at 64-bit wraparound (full conformance would need the biased-index
  overflow trick, changing `I`/`J`/`R@` representation). Consequence:
  `0 0 DO ... LOOP` iterates ~2^64 times (ANS-correct; there is no `?DO`).
- No `EVALUATE`, no `AGAIN`, no `UNLOOP` user word (only internal `(unloop)`),
  no `2SWAP/2OVER`. Strings are `."`/`S"`/`TYPE` only — no `C@`/`C!`, no counted
  strings, no `S+`/`COMPARE`; each interpreted `S"` permanently allots its cells.
- Numbers parse via `Long.parseLong(token, BASE)`: no `#`/`$`/`'c'` prefixes,
  no double-cell numbers, dictionary lookup still shadows numbers (`BEEF` in HEX
  is a number only if no word `BEEF` exists).
- `interpret()` is not reentrant (single `input`/`pos`) — matters if EVALUATE is
  ever added.
- `EXIT` inside DO..LOOP leaves loop params on the return stack (standard Forth
  requires UNLOOP first; user's responsibility).

## Tests (src/test/java/..., 115 total)

- `MachineTest` — stacks, memory, bounds. Constructs Machine directly.
- `DictionaryTest` — shadowing, case, persistence. Uses raw `new Word.Primitive`.
- `InterpreterTest`, `CompilerTest`, `ControlFlowTest`, `MemoryTest`, `PolishTest`
  — all use the pattern: fields `Machine`, `StringWriter output`,
  `Interpreter interpreter = Bootstrap.interpreter(machine, new PrintWriter(output))`,
  helper `long[] stackAfter(String line)`. Tests read as Forth transcripts.
- Error-message assertions use `startsWith`/`contains` (messages carry a
  line+caret context suffix since Phase 6). Two historical test failures were both
  hand-traced stack-effect mistakes in expectations, never implementation bugs.

## Conventions in force (from user's global CLAUDE.md)

- Machine-like tone in all communication: no emojis, no colloquialisms, no
  humor; acknowledge with "OK"; say "Inconclusive" with reasons when no clear
  answer exists.
- Code: spaces, 4-space indent; functional style preferred; immutable data
  structures unless a critical loop demonstrably benefits; mutation is currently
  confined to Machine, open Definition, and Interpreter's dictionary head +
  tokenizer state.
- Project style: blank line between field declarations; `interpreter ->` lambda
  parameter naming in Primitives (`inner` for nested closures, `m` for local
  Machine); tests use JUnit static-import assertions.

## Natural next steps (none requested yet)

1. Initial git commit (user has not asked; everything is uncommitted).
2. `DOES>` via an execution-frame refactor of `run()` — or the compile-time
   split approach (immediate `DOES>` seals the build-time half and collects the
   tail; see discussion 2026-07-19).
3. `EVALUATE` (requires reentrant tokenizer state — save/restore `input`/`pos`).
4. Load `.fs` files from the command line (`Repl.main` args are ignored).
5. `?DO` (skip loop body when limit equals index — the usual guard now that
   `0 0 DO` loops ~2^64 times), or full wraparound conformance via the
   biased-index overflow representation.
