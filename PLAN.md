# fjorth — Implementation Plan

A Forth implementation in Java. Package: `com.github.kjetilv.fjorth`.

## Status

| Phase | Title                                  | Status      |
|-------|----------------------------------------|-------------|
| 0     | Project scaffolding                    | Done        |
| 1     | Core types                             | Done        |
| 2     | Outer interpreter + primitives         | Done        |
| 3     | Compilation                            | Done        |
| 4     | Control flow                           | Done        |
| 5     | Memory model                           | Done        |
| 6     | Polish                                 | Done        |

Status values: `Not started` | `In progress` | `Done`

## Phase 0 — Project scaffolding

- Set Java toolchain in `build.gradle.kts` (21+; records, sealed types, pattern matching).
- Add `application` plugin with a main class for the REPL.
- Package layout:
  ```
  com.github.kjetilv.fjorth
  ├── Machine.java        // stacks, state, memory — the interpreter context
  ├── Word.java           // word abstraction
  ├── Dictionary.java
  ├── Interpreter.java    // outer interpreter + tokenizer
  ├── Primitives.java     // built-in word registration
  └── Repl.java           // main
  ```

## Phase 1 — Core types

1. **`Word`** — sealed interface with:
   - `Primitive(String name, boolean immediate, Effect effect)` where `Effect` is `void apply(Machine m)`
   - `Colon(String name, boolean immediate, List<Word> body)` — flat list, executed by index so branch words can jump
   - Internal branch primitives: `Branch(int offset)` and `ZeroBranch(int offset)`
2. **`Machine`** — data stack (`long[]` + pointer), return stack, `STATE` flag, reference to the definition under construction. Underflow/overflow raise a `ForthException` carrying a message; the REPL catches it and resets stacks.
3. **`Dictionary`** — persistent chain (each entry links to the previous), lookup walks newest-first. Gives shadowing semantics for free; compiled `Colon` bodies hold direct `Word` references so old definitions stay valid after redefinition.

**Tests:** stack push/pop/underflow, dictionary shadowing.

## Phase 2 — Outer interpreter + primitives (working calculator)

1. Stateful tokenizer over a line of input: `nextToken()`, plus `readUntil(char)` for words that consume input (`."`, `(`).
2. Interpret loop: lookup → execute; miss → `Long.parseLong`; miss → error `X ?`.
3. Primitives, ~30 words:
   - Stack: `DUP DROP SWAP OVER ROT 2DUP NIP TUCK`
   - Arithmetic: `+ - * / MOD NEGATE ABS MIN MAX`
   - Comparison/logic: `= < > 0= AND OR XOR INVERT` (Forth truth: -1/0)
   - Return stack: `>R R> R@`
   - I/O: `. .S EMIT CR ." ( \`
4. `Repl` — read line, interpret, print `ok` or error; reset stacks on error, keep dictionary.

**Tests:** interpret strings and assert stack contents / captured output. This test harness (input string → expected stack/output) carries all later phases.

## Phase 3 — Compilation

1. `:` — read name, switch STATE to compiling, open a body accumulator.
2. `;` — immediate; close the definition, add to dictionary, switch STATE back.
3. Interpret loop honors STATE: compiling → append word to body unless immediate; numbers compile to a `Literal(long)` word.
4. `IMMEDIATE`, `VARIABLE`, `CONSTANT` (as dictionary entries holding state for now).
5. Colon execution: indexed loop over the body with an explicit instruction pointer on the machine — not recursive Java iteration — so Phase 4 branches work.

**Tests:** define and call words, nested definitions, redefinition shadowing with old callers unaffected.

## Phase 4 — Control flow

All words below are immediate and use the data stack at compile time to hold backpatch locations:

1. `IF` compiles `ZeroBranch(?)`, pushes its body index; `THEN` backpatches; `ELSE` compiles `Branch(?)`, backpatches the `IF`, pushes itself.
2. `BEGIN` / `UNTIL` / `WHILE` / `REPEAT` — backward branches, same mechanism.
3. `DO` / `LOOP` / `+LOOP` / `I` / `J` / `LEAVE` — loop parameters on the return stack.
4. `RECURSE`, `EXIT`.

Backpatching mutates the body under construction only — once `;` seals the definition into an immutable list, nothing mutates again. Mutability is confined to the compiler.

**Tests:** conditionals, nested loops, `LEAVE`, recursive factorial, fibonacci.

## Phase 5 — Memory model

1. `HERE`, `ALLOT`, `+!`, `CELLS` words. (The `long[]` cell memory on `Machine`, plus `@` and `!`, landed with Phase 3 — `VARIABLE` required real addresses to be usable.)
2. `CREATE` and `,` (comma). `DOES>` was deferred here (it seemed to require runtime access to the executing colon body) and later implemented post-plan via a compile-time split instead — see IMPLEMENTATION.md.

**Tests:** variable read/write, `ALLOT`-based arrays.

## Phase 6 — Polish

1. Bootstrap file: define non-essential words in Forth itself (`fjorth.fs` loaded at startup) — e.g. `2DUP`, `MAX` can move out of Java. Validates the implementation.
2. `WORDS`, `SEE` (decompiler) for introspection.
3. Error messages with input position.

**Tests:** bootstrap file loads cleanly; decompiler round-trips a definition.

## Sequencing rationale

Each phase ends runnable and tested; Phase 2 already yields a usable RPN calculator. The two decisions locked in early — flat threaded bodies with an explicit instruction pointer (Phase 3.5) and persistent dictionary chain (Phase 1.3) — are the ones that are expensive to retrofit. Everything else is additive.

Out of scope until a concrete need arises: cell size other than `long`; string handling beyond `."`.
