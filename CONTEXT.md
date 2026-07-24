# fjorth — Session Context

Snapshot for resuming work. State as of 2026-07-24, verified against the actual
sources. Companion documents: [PLAN.md](PLAN.md) (original phase plan, all phases
`Done`), [IMPLEMENTATION.md](IMPLEMENTATION.md) (execution log).

## Current state — green

Builds and passes. `Machine` now exposes a public static `create()` factory
(delegating to `HeapMachine`), and `repl.java` builds its interpreter via
`Machine.create().interpreter(CONSOLE)` — this closed the earlier entry-point
compile gap. Verified 2026-07-24: `./gradlew test` green, 147 tests; REPL runs.
(Native tasks not re-run this session but unaffected by the fix.)

## What this is

A Forth implementation in Java 25. Gradle project `fjorth`, group
`com.github.kjetilv`, package `com.github.kjetilv.fjorth`. All 7 planned phases
(0–6) complete, plus post-plan work: fully ANS-conformant `LOOP`/`+LOOP`
(biased-index wraparound), `BASE`/`HEX`/`DECIMAL`/`OCTAL`, `S"`, `TYPE`, `.R`,
`DOES>`, `EVALUATE`, `?DO`, `C@`/`C!`, `ABORT`/`ABORT"`, double-cell words. Since
the last snapshot the codebase was substantially **refactored for performance and
structure** (see "Performance refactor" below); the git log shows a run of
`Performance` and `Megamorphism vs. sealed` commits.

## Working agreement

- **Git is managed by the user.** Do not commit unless asked.
- **The user refactors between sessions** (visibility tightening, renames, new
  abstractions, style modernization, performance work). Verify current file
  contents before editing; treat every inventory here as a starting point, not
  truth. This document has been wrong-by-staleness at the start of multiple
  sessions — re-read the sources.
- Tone and code style: user's global CLAUDE.md (machine-like tone, no emojis;
  4-space indent; functional style; immutability by default).
- Observed house style: `import module java.base;`, `var` for locals,
  package-private visibility unless public is required, static factory methods at
  the top of types, constants at the BOTTOM of classes, ternary-chain expressions
  over if/else, blank line between field declarations, JUnit assertions via
  `import static ...Assertions.*`.

## Build and run

```
JAVA_HOME=~/.sdkman/candidates/java/current ./gradlew test          # green, 147 tests
printf '1 2 + .\n' | JAVA_HOME=~/.sdkman/candidates/java/current ./gradlew -q run --console=plain
JAVA_HOME=~/.sdkman/candidates/java/25.0.3-graal ./gradlew nativeCompile   # → build/native/nativeCompile/fjorth
```

- The environment's `JAVA_HOME` points to a REMOVED JDK (`25.0.2-graalce`); the
  override above is required for every `./gradlew` invocation. Installed JDKs
  (sdkman): 25.0.3-graal, 25.0.3-tem, 26.0.1-oracle, 26.0.1-zulu.
- Toolchain pinned to Java 25 in `build.gradle.kts`. The code uses Java 25
  features throughout: module imports (JEP 511), compact source files / instance
  main (JEP 512), unnamed patterns (`_`), record deconstruction.
- JUnit 6 (junit-bom 6.0.0, jupiter), `useJUnitPlatform()`.
- Native image: `org.graalvm.buildtools.native` plugin (0.11.0). `graalvmNative`
  block: `main` binary → `fjorth`, mainClass `repl`, `--no-fallback`,
  `-H:IncludeResources=fjorth\.fs` (REQUIRED — the interpreter loads fjorth.fs as
  a classpath resource; without it the binary starts then dies on a missing
  resource); `test` binary → same IncludeResources, driving `nativeTest`. Plugin
  MUST be 0.11.0, not 0.10.6 (0.10.6's JUnit-native metadata predates JUnit
  Platform 6 and fails `nativeTestCompile` with cascading
  initialize-at-build-time errors). Last green run 2026-07-19: `nativeCompile`
  ~19s / ~14MB, `nativeTest` all tests green. NOT re-verified 2026-07-24 (build
  red). Tasks: `nativeCompile`, `nativeRun`, `nativeTest`.

## Architecture

### Public surface (visible from the default package / callers)

- **`Machine`** — interface. Public static `create()` factory (delegates to
  `HeapMachine`). Factory-of-interpreter methods: `interpreter()` (stdout,
  canonical/cached), `interpreter(Console)`, `interpreter(Console, boolean
  canonical)`.
- **`Interpreter`** — interface. `interpret(String) → Result`, where `Result` is
  a sealed `OK | Failed(String message)`. NOTE: `Result` is no longer
  `AutoCloseable` and no longer carries a closer — reset-on-failure now happens
  inside `InterpreterImpl.interpret`'s catch block.
- **`Console`** — output abstraction (replaces the old `Out`). `print(String)`,
  `print(char)`, `println`, `flush`, and now `reset()`. **`Consoles`** — factory:
  `stdout()`, `to(PrintWriter)`, `to(StringWriter)`, `to(StringBuilder)`.
  Implementations `PrintWriterConsole`, `StringBuilderConsole` (package-private).
- `FjorthException` is now **package-private** (was public); message + once-only
  `locate(line, position)` caret context (innermost location wins — EVALUATE
  errors point into the evaluated text).

### Entry point

`src/main/java/repl.java` — Java 25 compact source file (implicit class, instance
`main(String[] args)`, default package). `build.gradle.kts` sets
`mainClass = "repl"`, stdin wired into `run`. Builds its interpreter via
`Machine.create().interpreter(CONSOLE)`. No args → interactive stdin loop (banner
`fjorth`, ` ok` per line, newline + message on failure). Args → each is a `.fs`
file evaluated line-by-line before the interactive loop; first failing line
aborts startup with `IllegalStateException` naming `<file>:<line> >> <line>`.

### Core (package-private, com.github.kjetilv.fjorth)

- **`MachineApi`** — internal interface extending `Machine`; the full stack/memory
  contract the interpreter uses: `push/pop/peek(offset)/depth`,
  `pushReturn/popReturn/peekReturn(offset)/returnDepth`, `allot/here/fetch/store`,
  `store(addr,count,value)` (fill), `base/baseAddress`, `compiling`, `reset`,
  `ipop()` (int-range-checked pop), `stack()` (bottom-first copy).
- **`HeapMachine`** — the `MachineApi` implementation (`create()` factory,
  package-private). `long[]` data/return/memory. Defaults GREW: stack 1024,
  memory 65536 cells (were 256/4096). Cell 0 reserved for BASE (init 10), so
  `here` starts at 1; `reset()` restores `here=1`. `base()` validates 2–36. Char
  ops mask to 16 bits (`CHAR_MASK 0xFFFF`). Caches the canonical `Interpreter` in
  an `AtomicReference`. Bounds/overflow via a generic `fail(msg)` thrower.
- **`InterpreterImpl`** — the `Interpreter` implementation and executor. Holds
  `MachineApi`, `Console`, mutable `Dictionary`, current `Definition`, tokenizer
  state, and a `sealed` flag. Built unsealed (`unsealed(machine, console)`),
  `loadLibrary("fjorth.fs")` interprets the library into the mutable dictionary,
  then `seal()` returns a sealed instance. `define`: unsealed → `dictionary.insert`
  (mutate in place); sealed → `dictionary.define` (new chained head).
  `interpret` catches `FjorthException`, returns `Failed`, and resets in a
  finally. `evaluate(text)` is REENTRANT (saves/restores tokenizer state) — backs
  EVALUATE and the library loader. `execute(Word)` switch: Primitive → apply
  effect; Colon → `executeAll(body)` (indexed pointer loop; Branch sets it,
  ZeroBranch pops+jumps on 0; nested colons recurse in Java). Tokenizer consumes
  exactly one trailing delimiter char (why `." x"` has no leading space).
- **`Dictionary`** — HYBRID (changed from the old pure persistent chain, for
  lookup speed): a sealed dictionary holds the base vocabulary in an immutable
  `Map<String,Word>` (O(1) case-insensitive lookup); runtime `define` prepends
  single-word parent nodes each caching its lowercased name, so `lookup` walks the
  (short) user-definition chain then hits the map. Lifecycle: `unsealed(words)`
  (mutable LinkedHashMap, `insert` in place for bootstrap) → `seal()`
  (`Map.copyOf`). `of(word)`/`empty()`/`define`/`latest`/`words()` retained.
  Shadowing still free; compiled bodies hold direct `Word` refs.
- **`Definition`** — open colon definition during compilation. Body list + nullable
  `tail` (opened by `beginTail()` for DOES>); `append/size/resolve` act on the
  active list. Loop scoping via a `Deque<List<Integer>>` of LEAVE sites.
  `recurse()` closes over a one-element `Word[] self` filled by `seal()`. `seal()`
  validates (no unterminated DO, no branch target < 0 in body or tail), appends
  the `(does>)` retrofit word if a tail exists, returns the immutable `Word.Colon`.
- **`Word`** — sealed interface, factory statics (`primitive/colon/literal/branch/
  zeroBranch`). `immediate()` is now a DEFAULT returning false; only `Primitive`
  and `Colon` carry it as a component; `Colon.asImmediate()` returns an immediate
  copy. Records: `Primitive(name, immediate, Effect)`, `Colon(name, immediate,
  List<Word> body)` (copyOf), `Literal(long)`, `Branch(int)`, `ZeroBranch(int)`
  (target ABSOLUTE; `-1` unresolved placeholder; `Integer.MAX_VALUE` = EXIT).
  `Effect { void apply(InterpreterImpl); }` — NOTE: no longer `@FunctionalInterface`
  and no longer lambda-based (see Performance refactor).
- **`Primitives`** — `public static final List<Word> WORDS`, each built-in as a
  NAMED record implementing `Effect` (not a lambda): `BinaryOp(LongOp)` with
  per-op records `Mul/Add/Sub/Div/LTh/Eql/GTh/And/Mod/Or/Xor`, `UnaryOp`, `Noop`,
  `Dot/DotR/DotS/Dup/Drop/Swap/Over/Rot/SQuote/Type/Evaluate/Create/Loop/PlusLoop/
  I/...` and the immediate compiler words (`If/Else/Then/Begin/Until/While/Repeat/
  Do/QDo/Loop/PlusLoop/Leave/Exit/ImmediateDoes/ImmediateRecurse/EndDefinition/
  SQuote/DotQuote/AbortQuote/...`). SEE rendering + number formatting live here.

## Performance refactor (git: "Performance", "Megamorphism vs. sealed")

Three structural changes aimed at hot-path dispatch and lookup:

1. **Effects are named types, not lambdas.** Each primitive is a distinct record
   implementing `Effect`, arithmetic factored through `BinaryOp(LongOp)` with a
   named op per operator. The intent (per the commit names): give the JIT
   monomorphic/bimorphic call sites at the `Effect.apply` / `LongOp` invocation
   points instead of one megamorphic site shared by hundreds of lambdas. There
   are `MegamorphicTest`/sealed comparison tests exploring this — treat the
   named-type structure as deliberate; do not "simplify" it back to lambdas.
2. **Dictionary map lookup.** Base vocabulary in an immutable `Map` (O(1)) instead
   of an O(n) linear scan down a persistent chain; user definitions still chain.
3. **Larger machine defaults** (stack 1024, memory 65536) to avoid overflow in the
   algorithmic benchmark tests (Bubble/Fib/Primes).

If touching these, benchmark rather than assume; the point of the structure is
measured dispatch behavior.

## Word inventory

Java primitives (in `Primitives.WORDS`): stack `DUP DROP SWAP OVER ROT`;
arithmetic `+ - * / MOD`; comparison/logic `= < > 0= AND OR XOR INVERT`; return
stack `>R R> R@ I J`; I/O `. .R .S EMIT CR TYPE ." S"`; `EVALUATE`; char memory
`C@ C!`; error `ABORT ABORT"`; comments `( \`; compiler `: ; IMMEDIATE CONSTANT
VARIABLE CREATE DOES>`; memory `@ ! +! HERE ALLOT CELLS , BASE`; no-ops `ALIGN
CELLS+`; control flow (immediate) `IF ELSE THEN BEGIN UNTIL WHILE REPEAT DO ?DO
LOOP +LOOP LEAVE EXIT RECURSE`; tools `WORDS SEE`. Truth values -1/0. `R@` inside
a DO-loop exposes the BIASED slot, not the index — use `I`.

Defined in Forth (`src/main/resources/fjorth.fs`): `2DUP 2DROP NIP TUCK NEGATE ABS
MIN MAX 1+ 1- 0< 0> <> TRUE FALSE ?DUP CELL+ HEX DECIMAL OCTAL SPACE SPACES 2@ 2R@
2R> 2>R 2SWAP 2OVER`. (`2DUP` is defined twice — harmless redefinition.)

## Compile-time mechanics (unchanged; for extending control flow)

Backpatch positions travel on the DATA stack at compile time: `IF`/`WHILE` push
the index of their placeholder `ZeroBranch(-1)`; `THEN`/`REPEAT` pop and
`resolve`; `ELSE` patches IF's and pushes its own `Branch(-1)` index; `BEGIN`
pushes a destination; `UNTIL`/`REPEAT` compile backward branches to it.

`DO`/`?DO` push the body-start index AND open a leave scope; `LOOP`/`+LOOP` pop
it, compile runtime word + `ZeroBranch` back, then resolve all LEAVE sites to just
past themselves. `LEAVE` compiles `(unloop)` + `Branch(-1)` and registers the
site. `?DO`'s skip is a `ZeroBranch(-1)` registered as a leave site.

Loop runtime: parameters live on the RETURN stack in BIASED form — limit, then
`slot = index - limit + Long.MIN_VALUE`. The ANS limit-1/limit boundary sits
exactly at MAX_VALUE/MIN_VALUE, so `(loop)`/`(+loop)` terminate on signed overflow
of `slot + increment` (`((slot ^ next) & (increment ^ next)) < 0`) — exact even
across 64-bit wrap. `I` = `slot + limit + MIN_VALUE` (offsets 0,1), `J` offsets 2,3.

`DOES>` is a compile-time split (NOT the classic code-field patch): immediate,
`Definition.beginTail()`; `seal()` wraps the tail in an anonymous Colon and
appends a `(does>)` primitive that at run time redefines the LATEST word as (old
behavior — CREATEd word pushes its data address — then tail), via shadowing. One
DOES> per definition; DO..LOOP may not span the split (guarded); IF spanning the
split is NOT guarded; retrofit is permissive about the latest word (ANS restricts
to CREATEd; not enforced).

## Known limitations

- No `AGAIN`; no user-level `UNLOOP` (only internal `(unloop)`); no counted
  strings, no `COMPARE`; each interpreted `S"` permanently allots cells.
- Numbers: `Long.parseLong(token, BASE)` only — no `#`/`$`/`'c'` prefixes, no
  double-cell literals. Dictionary lookup shadows numbers.
- `0 0 DO ... LOOP` iterates ~2^64 times (ANS-correct; guard with `?DO`).
- `EXIT` inside DO..LOOP leaves loop params on the return stack (user's
  responsibility, per ANS).

## Tests (src/test/java/com/github/kjetilv/fjorth/, 15 files, 147 @Test methods)

Most extend **`InterpreterTestCase`** (base class): a shared `static HeapMachine`
+ `StringBuilder`-backed `Console`, a canonical `InterpreterImpl`, and a captured
`baseDictionary`; `@BeforeEach` calls `reset()` (console reset + interpreter reset
to `baseDictionary`). Helpers read as Forth transcripts: `interpret(line)`,
`stackAfter(line, long...)`, `emptyStackAfter`, `interpretFailed(line)→message`,
`output()`, `outputOf`. `DictionaryTest` and `HeapMachineTest` construct core
types directly (`Dictionary.of(...)`, `new HeapMachine()`, `new Primitives.Noop()`).
Suites: `CompilerTest ControlFlowTest DictionaryTest DoesTest EvaluateTest
HeapMachineTest InterpreterImplTest MemoryTest PolishTest StringNumberTest` plus
algorithm/perf suites `BubbleSortTest FibTest PrimesTest LibTest`. All 147 green
(2026-07-24).

## Natural next steps

1. Optional vocabulary: `AGAIN`, user `UNLOOP`, `WITHIN`, `.(`.
2. Optional: suppress the per-line ` ok` echo when evaluating `.fs` file args.
3. Re-run the native tasks to reconfirm they still build/pass after the refactor
   (last green 2026-07-19).
