# fjorth — Session Context

Snapshot for resuming work. State as of 2026-07-19, verified against the actual
sources on that date. Companion documents: [PLAN.md](PLAN.md) (original phase
plan, all phases `Done`), [IMPLEMENTATION.md](IMPLEMENTATION.md) (per-phase
execution log, learnings, deviations, post-plan work log).

## What this is

A Forth implementation in Java 25. Gradle project `fjorth`, group
`com.github.kjetilv`, package `com.github.kjetilv.fjorth`. All 7 planned phases
(0–6) complete, plus post-plan work: fully ANS-conformant `LOOP`/`+LOOP`
(biased-index wraparound representation), `BASE`/`HEX`/`DECIMAL`/`OCTAL`, `S"`,
`TYPE`, `.R`, `DOES>`, `EVALUATE`, `?DO`. 141 tests, all passing. Working REPL.

## Working agreement

- **Git is managed by the user.** Do not commit unless asked.
- **The user refactors between sessions** (visibility tightening, renames, new
  abstractions, style modernization). Verify current file contents before
  editing; treat every inventory in this file as a starting point, not truth.
- Tone and code style rules: user's global CLAUDE.md (machine-like tone, no
  emojis; 4-space indent; functional style; immutability by default).
- Observed house style in the current code: `import module java.base;`, `var`
  for locals, package-private visibility unless public is required, static
  factory methods at the top of types, constants at the BOTTOM of classes,
  blank line between field declarations, JUnit assertions via
  `import static org.junit.jupiter.api.Assertions.*`.

## Build and run

```
JAVA_HOME=~/.sdkman/candidates/java/current ./gradlew test
printf '1 2 + .\n' | JAVA_HOME=~/.sdkman/candidates/java/current ./gradlew -q run --console=plain
# native binary (GraalVM), output at build/native/nativeCompile/fjorth:
JAVA_HOME=~/.sdkman/candidates/java/25.0.3-graal ./gradlew nativeCompile
```

- The environment's `JAVA_HOME` points to a REMOVED JDK (`25.0.2-graalce`); the
  override above is required for every `./gradlew` invocation. Installed JDKs
  (sdkman): 25.0.3-graal, 25.0.3-tem, 26.0.1-oracle, 26.0.1-zulu.
- Toolchain pinned to Java 25 in `build.gradle.kts`. The code uses Java 25
  features throughout: module imports (JEP 511), compact source files /
  instance main (JEP 512), unnamed patterns (`_`), record deconstruction.
- JUnit 6 (junit-bom 6.0.0, jupiter), `useJUnitPlatform()`.
- Native image: `org.graalvm.buildtools.native` plugin (0.11.0), `graalvmNative`
  block configures two binaries:
  - `main` → `fjorth`, mainClass `repl`, `--no-fallback`,
    `-H:IncludeResources=fjorth\.fs` (REQUIRED — Bootstrap loads fjorth.fs as a
    classpath resource; without this the binary starts then dies on a missing
    resource).
  - `test` → same `-H:IncludeResources=fjorth\.fs` (the test fixtures also go
    through Bootstrap), driving `nativeTest`.
  Needs a GraalVM 25 toolchain (`25.0.3-graal` has `native-image`); set
  JAVA_HOME to it for native tasks. Plugin MUST be 0.11.0, not 0.10.6: 0.10.6's
  bundled JUnit-native metadata predates JUnit Platform 6 and fails
  `nativeTestCompile` with cascading image-heap/initialize-at-build-time errors
  on `org.junit.platform.launcher.core.*`; 0.11.0 is JUnit-6-aware and needs no
  workaround flags. Verified 2026-07-19: `nativeCompile` ~19s / ~14MB binary
  (bootstrap/base/strings/error-recovery/file-args all work); `nativeTest`
  builds and runs all 141 tests green in native mode. No reflection config
  needed — the interpreter uses none; the resource was the only concern. Tasks:
  `nativeCompile`, `nativeRun`, `nativeTest`.

## Architecture

### Public API (3 types; everything else package-private)

- **`Interpreter`** — the facade interface. `static getDefault()` /
  `getDefault(Out)` build a bootstrapped instance. `eval(String)` returns a
  sealed `Result` (`OK` | `Failed(message, closer)`); `Result` is
  `AutoCloseable`, and `Failed.close()` runs its closer — the REPL uses
  try-with-resources so a failure auto-resets the machine. Also `reset()`,
  `out()`.
- **`Out`** — output abstraction. `Out.std()` (System.out), `Out.to(StringWriter)`
  (tests). `print(String)`, `print(char)`, `println`, `flush`. Implemented by
  package-private `PrintWriterConsole` (PrintWriter-backed; `flush()` throws
  IllegalStateException on writer error).
- **`FjorthException`** — runtime error carrying a message; package-private
  constructor. `locate(line, position)` appends `\n<line>\n<spaces>^` caret
  context ONCE (no-op if already located) — innermost location wins, which is
  what makes EVALUATE errors point into the evaluated text.

### Entry point

`src/main/java/repl.java` — a compact source file (implicit class, instance
`main(String[] args)`, unnamed package). `build.gradle.kts` has
`mainClass = "repl"` and wires stdin into the `run` task. Uses
`Fjorth.getDefault()` and try-with-resources over `eval`'s Result (Failed
auto-resets via its closer). Behavior (user-implemented):

- Each arg is a `.fs` file path, announced (`*** evaluating file <arg>`) and
  evaluated line by line BEFORE the interactive loop; the first failing line
  aborts startup with `IllegalStateException` naming `<file>:<line-no> >>
  <line>`. Try it: `./gradlew -q run --console=plain --args="/tmp/lib.fs"`.
- The failure path prints a newline before the error message, so output
  flushed by the failing line does not share its line.
- Note: file lines echo ` ok` like interactive lines do.

### Core (package-private, in com.github.kjetilv.fjorth)

- **`Word`** — sealed interface, `name()` + `immediate()`, with static factory
  methods (`Word.primitive(name, effect)`, `primitive(name, immediate, effect)`,
  `colon`, `literal`, `branch`, `zeroBranch`). Variants (records):
  - `Primitive(name, immediate, Effect effect)`
  - `Colon(name, immediate, List<Word> body)` — compact constructor `List.copyOf`
  - `Literal(long)` — name `(literal)`
  - `Branch(int target)`, `ZeroBranch(int target)` — target is an ABSOLUTE body
    index; `-1` = unresolved placeholder; `Integer.MAX_VALUE` = EXIT sentinel
  - `Effect { void apply(Interpreter); }` — takes Interpreter (parsing/IO words
    need tokenizer and output access)
- **`Machine`** — mutable state: data + return stacks (`long[]` + top, default
  256, ctor-configurable), cell memory (`long[]`, default 4096). Memory cell 0
  is RESERVED for BASE (allotted in the constructor, initialized 10), so `here`
  starts at 1. `base()` validates 2–36. `allot` guards overflow and
  below-zero; `fetch`/`store` bounds-check. `peekReturn(int offset)` (0 = top).
  `stack()` returns a bottom-first copy. `reset()` clears stacks + compiling
  flag but NOT memory/here.
- **`Dictionary`** — persistent chain (word + parent), `Dictionary.empty()`
  singleton. `define` returns a new head; `lookup` walks newest-first,
  case-insensitive; `latest()`; `words()` streams newest-first. Shadowing is
  free; compiled bodies hold direct `Word` references, so redefinition never
  disturbs old callers.
- **`Definition`** — the open colon definition during compilation. Mutable
  body list + nullable `tail` list opened by `beginTail()` (DOES>);
  `append`/`size`/`resolve` operate on the ACTIVE list (tail once open).
  `resolve(index, target)` re-creates the branch record in place, preserving
  kind. Loop scoping: `beginLoop`/`addLeave`/`endLoop` over a
  `Deque<List<Integer>>` of LEAVE sites. `recurse()` returns a primitive
  closing over a one-element `Word[] self` filled by `seal()` (the RECURSE
  chicken-and-egg: a lambda must capture a final reference whose slot is
  assigned later; array over `this`-capture so compiled words do not retain
  the whole Definition). `seal()` validates (no unterminated DO, no branch
  target < 0 in body OR tail), appends the `(does>)` retrofit word if a tail
  exists, and returns the immutable `Word.Colon`.
- **`InterpreterImpl`** — implements `Interpreter`; outer interpreter + executor. Holds
  Machine, mutable Dictionary head, Out, current Definition, tokenizer state
  (`input`/`pos`/`tokenStart`).
  - `eval(line)`: `interpret` + catch → `Result.Failed(message, this::reset)`.
  - `interpret(line)`: token loop; flushes Out in finally.
  - `evaluate(text)`: REENTRANT — saves/restores tokenizer state around the
    token loop; used by EVALUATE, nests arbitrarily.
  - Token loop wraps FjorthException with `locate(input, tokenStart)`.
  - `handle(token)`: dictionary lookup → compiling && !immediate ? append :
    execute; else number via `Long.parseLong(token, machine.base())` → Literal
    when compiling, push otherwise; unknown → `token ?`.
  - `execute(Word...)`: Primitive applies effect; Colon runs via `run` (local
    indexed ip; Branch sets ip, ZeroBranch pops and jumps on 0; nested colons
    recurse in Java — the JVM call stack is the return-address stack; the
    explicit return stack serves >R/R>/loop params only).
  - Tokenizer: `nextToken()` consumes EXACTLY ONE trailing delimiter char
    (this is what makes `." x"` print without a leading space);
    `readUntil(char)`, `readRestOfLine()`, `word(requester)`.
  - Compiler API: `beginDefinition`/`endDefinition`/`append`/`open()`/
    `define`/`makeLatestImmediate` (shadows latest Colon with an immediate
    copy). `reset()` = machine reset + drop open definition.
- **`Primitives`** — all built-in words as a `List<Word>` reduced onto the
  empty dictionary. Helpers: `primitive`/`immediate`/`unary`/`binary` (binary
  pops b then a, pushes op(a,b)), `flag(boolean)` → -1/0, `formatted(m, value)`
  (BASE-aware, uppercase), `poppedString(m)` (addr+len cell string, shared by
  TYPE/EVALUATE). SEE rendering in `render`/`renderColon`/`cell`.

## Word inventory

Java primitives:

- Stack: `DUP DROP SWAP OVER ROT`
- Arithmetic: `+ - * / MOD` (division by zero → error)
- Comparison/logic: `= < > 0= AND OR XOR INVERT` (truth: -1/0)
- Return stack: `>R R> R@ I J` (note: `R@` inside a DO-loop exposes the BIASED
  slot, not the index — use `I`; see loop mechanics)
- I/O: `. .R .S EMIT CR TYPE ." S"` — `."`/`S"` immediate and state-dependent;
  `S"` copies into cell memory (one char per cell, PERMANENTLY allotted at
  parse/compile time) and yields/compiles addr + len as two Literals; `.R` is
  `( n width -- )` right-aligned, no trailing space; `.`/`.R`/`.S` format via
  BASE, uppercase
- `EVALUATE ( addr u -- )`
- Comments: `( \` (immediate)
- Compiler: `: ; IMMEDIATE CONSTANT VARIABLE DOES>` (`;`, `DOES>` immediate)
- Memory: `@ ! +! HERE ALLOT CELLS , CREATE BASE` — CELLS is identity
  (cell-addressed memory); VARIABLE allots 1 cell, word pushes its address;
  CREATE pushes creation-time HERE, allots nothing; `,` stores + allots 1;
  BASE pushes the reserved cell-0 address
- Control flow (immediate): `IF ELSE THEN BEGIN UNTIL WHILE REPEAT DO ?DO LOOP
  +LOOP LEAVE EXIT RECURSE`
- Tools: `WORDS` (deduplicated, newest first), `SEE` (single line for
  straight-line bodies, indexed listing when branches present, ` IMMEDIATE`
  suffix, `exit` for the MAX_VALUE branch, `NAME ( primitive )` for primitives)

Defined in Forth (`src/main/resources/fjorth.fs`; NOT in Primitives.java):
`2DUP 2DROP NIP TUCK NEGATE ABS MIN MAX 1+ 1- 0< 0> <> TRUE FALSE ?DUP CELL+
HEX DECIMAL OCTAL SPACE SPACES`

## Compile-time mechanics (for extending control flow)

Backpatch positions travel on the DATA stack at compile time: `IF`/`WHILE` push
the index of their placeholder `ZeroBranch(-1)`; `THEN`/`REPEAT` pop and
`resolve`; `ELSE` patches IF's and pushes its own `Branch(-1)` index; `BEGIN`
pushes a destination; `UNTIL`/`REPEAT` compile backward branches to it.

`DO`/`?DO` push the body-start index AND open a leave scope; `LOOP`/`+LOOP`
pop it, compile runtime word + `ZeroBranch` back, then resolve all LEAVE sites
to just past themselves. `LEAVE` compiles `(unloop)` + `Branch(-1)` and
registers the site. `?DO`'s skip is a `ZeroBranch(-1)` registered as a leave
site — resolved by the same mechanism.

Loop runtime (`(do)`/`(?do)`/`(loop)`/`(+loop)` in Primitives): parameters live
on the RETURN stack in BIASED form — limit, then
`slot = index - limit + Long.MIN_VALUE`. The ANS limit-1/limit boundary sits
exactly at MAX_VALUE/MIN_VALUE, so `(loop)`/`(+loop)` terminate on signed
overflow of `slot + increment` (`((slot ^ next) & (increment ^ next)) < 0`) —
exact even when the index wraps the 64-bit range (fully ANS-conformant).
`(loop)`/`(+loop)` push 0 (continue; consumed by the backward ZeroBranch) or
-1 (exit; parameters dropped). `I` = `slot + limit + MIN_VALUE` (wrapping
arithmetic undoes the bias; offsets 0,1), `J` same at offsets 2,3.

`DOES>` is a compile-time split (NOT the classic runtime code-field patch):
immediate, calls `Definition.beginTail()`; further compilation goes to the
tail; `seal()` wraps the tail in an anonymous Colon and appends a `(does>)`
primitive that at run time redefines the LATEST dictionary word as (old
behavior — a CREATEd word pushes its data address — then tail), via
dictionary shadowing. One DOES> per definition; DO..LOOP may not span the
split (guarded); IF spanning the split is NOT guarded; retrofit is permissive
about what the latest word is (ANS restricts to CREATEd; not enforced).

## Known limitations

- No `AGAIN`, no user-level `UNLOOP`, no `2SWAP`/`2OVER`. Strings: no
  `C@`/`C!`, no counted strings, no `COMPARE`; each interpreted `S"`
  permanently allots cells.
- Numbers: `Long.parseLong(token, BASE)` only — no `#`/`$`/`'c'` prefixes, no
  double-cell. Dictionary lookup shadows numbers (`BEEF` in HEX is a number
  only if no word `BEEF` exists).
- `0 0 DO ... LOOP` iterates ~2^64 times (ANS-correct; guard with `?DO`).
- `EXIT` inside DO..LOOP leaves loop params on the return stack (ANS requires
  UNLOOP first; user's responsibility).

## Tests (src/test/java/com/github/kjetilv/fjorth/, 141 total)

`MachineTest`, `DictionaryTest` (construct core types directly);
`InterpreterImplTest`, `CompilerTest`, `ControlFlowTest`, `MemoryTest`,
`PolishTest`, `StringNumberTest`, `DoesTest`, `EvaluateTest` — all use the
fixture: fields `Machine machine`, `StringWriter output`,
`Interpreter interpreter = Bootstrap.interpreter(machine, Out.to(output))`,
helper `long[] stackAfter(String line)`. Tests read as Forth transcripts.
Error-message assertions use `startsWith`/`contains` because messages carry
the line+caret suffix. Historical note: every implementation-side test failure
in this project (3 total) was a wrong hand-traced expectation, never an
implementation bug — prefer short single-step stack assertions.

## Natural next steps (none requested)

1. Optional vocabulary: `AGAIN`, `UNLOOP`, `2SWAP`/`2OVER`, `C@`/`C!`,
   `WITHIN`, `.(`.
2. Optional: suppress the per-line ` ok` echo while evaluating `.fs` files
   passed as args (currently they echo like interactive lines).
