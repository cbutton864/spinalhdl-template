# SpinalHDL Design Style Guide

> **Template version:** SpinalHDL 1.14.0 · Scala 2.13.16 · sbt 1.10.11
>
> **Scope:** This guide targets SpinalHDL designs built on the `spinalhdl-template`. It applies
> to both FPGA and ASIC targets. Where ASIC design diverges significantly, the relevant section
> carries an **ASIC:** callout. ASIC callouts supplement FPGA rules; they do not replace them.

---

## How to Read This Guide

Each rule carries a severity marker:

| Marker | Meaning |
|--------|---------|
| **MUST** | Hard rule — violation is a design error |
| **SHOULD** | Strong preference — deviation requires documented justification |
| **MAY** | Convention — follow for consistency, deviate if reason is clear |

Rules with no example code apply to the design process or toolchain. Rules with examples
show both the correct pattern and, where useful, the incorrect pattern that the rule prevents.

---

## Table of Contents

- [Part 0 — Development Environment and Toolchain](#part-0--development-environment-and-toolchain)
- [Part I — Language Concepts and Terminology](#part-i--language-concepts-and-terminology)
- [Part II — Project Structure and Naming](#part-ii--project-structure-and-naming)
- [Part III — The Flat-Plugin Architecture](#part-iii--the-flat-plugin-architecture)
- [Part IV — Design Architecture Patterns](#part-iv--design-architecture-patterns)
- [Part V — RTL Design Rules](#part-v--rtl-design-rules)
- [Part VI — Simulation and Testing](#part-vi--simulation-and-testing)
- [Part VII — Synthesis and Generation](#part-vii--synthesis-and-generation)
- [Part VIII — Design for Timing](#part-viii--design-for-timing)
- [Part IX — Design Review Checklists](#part-ix--design-review-checklists)

---

## Part 0 — Development Environment and Toolchain

### 1. JVM — Java Development Kit

#### 1.1 Required Version

**MUST** use JDK 11 or later. **SHOULD** use JDK 17 LTS. JDK 21 LTS is usable but verify
compatibility with your pinned sbt and Scala versions before adopting it on a team.

#### 1.2 Distribution

**SHOULD** use an OpenJDK distribution (Eclipse Temurin, Amazon Corretto, Microsoft Build of
OpenJDK) to avoid Oracle licensing concerns. Any conformant OpenJDK 17 build is acceptable.

#### 1.3 Environment Verification

**MUST** verify the JDK installation with `java -version` and `javac -version` before first
build. Both commands must report the same major version. If they disagree, `JAVA_HOME` is
misconfigured. Use `jenv`, `sdkman`, or system alternatives to manage multiple JDKs.

#### 1.4 JVM Heap for Large Elaborations

**SHOULD** increase heap for designs with many plugins or deep pipeline elaborations.
Set in `build.sbt`:

```scala
javaOptions ++= Seq("-Xmx4g", "-Xss4m")
```

The default sbt heap is often insufficient for large SpinalHDL designs; elaboration will
fail with an `OutOfMemoryError` rather than a meaningful SpinalHDL message.

---

### 2. sbt — Scala Build Tool

#### 2.1 Installation and Version Pinning

**MUST** pin the sbt version in `project/build.properties`. The template pins:

```
sbt.version=1.10.11
```

Never rely on a globally installed sbt matching the project pin. Coursier (`cs`) is the
recommended install method: `cs install sbt`.

#### 2.2 Essential `build.sbt` Structure

The template's `build.sbt` is the canonical reference. Key settings:

```scala
ThisBuild / scalaVersion := "2.13.16"

lazy val root = (project in file("."))
  .settings(
    name    := "my-project",
    version := "0.1.0",

    libraryDependencies ++= Seq(
      "com.github.spinalhdl" %% "spinalhdl-core" % "1.14.0",
      "com.github.spinalhdl" %% "spinalhdl-lib"  % "1.14.0",
      "com.github.spinalhdl" %% "spinalhdl-sim"  % "1.14.0",
      "org.scalatest"        %% "scalatest"       % "3.2.18" % Test,
      compilerPlugin("com.github.spinalhdl" %% "spinalhdl-idsl-plugin" % "1.14.0")
    ),

    Test / parallelExecution := false,
    Test / fork              := true
  )
```

**MUST** keep `Test / parallelExecution := false`. Verilator compiles to native `.so`
files per test class; parallel runs write to the same paths and corrupt each other.

**SHOULD** keep `Test / fork := true`. Forking isolates native library loading and gives
each test run a clean JVM state, preventing class-loader conflicts between simulation runs.

#### 2.3 Key sbt Commands

| Command | Purpose |
|---------|---------|
| `sbt compile` | Compile Scala; does not generate RTL |
| `sbt test` | Run all tests (elaboration + simulation) |
| `sbt "testOnly mydesign.TimerCoreTest"` | Run one test class |
| `sbt "runMain mydesign.GenVerilog"` | Generate Verilog to `rtl/` |
| `sbt ~test` | Watch mode: recompile and test on save |

**SHOULD** use the sbt shell (`sbt` with no arguments) for iterative work to avoid the
JVM startup cost on every command. Use per-command invocation in CI.

#### 2.4 Plugins in `project/plugins.sbt`

The template includes `sbt-scalafmt`:

```scala
addSbtPlugin("org.scalameta" % "sbt-scalafmt" % "2.5.2")
```

**SHOULD** version-pin every plugin. **MUST** commit `project/plugins.sbt`.

---

### 3. Scala Version

#### 3.1 Scala 2.13.x — Required

**MUST** use Scala 2.13.x. SpinalHDL 1.x depends on Scala 2 macros that are not available
in Scala 3. Do not attempt a Scala 3 migration on a project using SpinalHDL 1.x.

Pin the exact patch version:

```scala
ThisBuild / scalaVersion := "2.13.16"
```

Minor-version updates within 2.13.x are usually safe but **SHOULD** be tested before
rolling to all contributors.

#### 3.2 Scala Style in SpinalHDL Code

- **SHOULD** use `case class` over `class` for plugins and params — copy semantics and
  pattern matching are useful; avoid accidental shared mutable state.
- **SHOULD NOT** use advanced implicits (type classes, implicit conversions) in signal
  paths. Clarity is more important than cleverness in RTL-generating code.
- **MUST** use `def` (not `val`) for the plugin list in `Params` — see §16.2.

---

### 4. SpinalHDL Dependencies in `build.sbt`

#### 4.1 Required Dependency Set

```scala
val spinalVersion = "1.14.0"

libraryDependencies ++= Seq(
  "com.github.spinalhdl" %% "spinalhdl-core" % spinalVersion,
  "com.github.spinalhdl" %% "spinalhdl-lib"  % spinalVersion,
  "com.github.spinalhdl" %% "spinalhdl-sim"  % spinalVersion,
  compilerPlugin("com.github.spinalhdl" %% "spinalhdl-idsl-plugin" % spinalVersion)
)
```

**MUST** declare a `val spinalVersion` at the top of `build.sbt` and use it for all three
SpinalHDL artifacts plus the compiler plugin. Version skew between them causes subtle,
hard-to-diagnose failures.

#### 4.2 The `spinalhdl-idsl-plugin` — Not Optional

**MUST** include the IDSL compiler plugin. Without it, `Component` constructors never call
`postInitCallback()`, so child components (BlackBox instances, `StreamFifoCC`, etc.) never
pop themselves from the component stack. This produces hierarchy violations at elaboration
that look like unrelated crashes. The plugin also enables the `during build` / `during setup`
syntax used by `FiberPlugin`. It is a hard requirement, not a convenience.

#### 4.3 Version Pinning Strategy

**MUST NOT** use `latest.release`, snapshot versions, or `+` suffixes in production projects.
Pin to a specific release. The template pins to `1.14.0`.

#### 4.4 ScalaTest

```scala
"org.scalatest" %% "scalatest" % "3.2.18" % Test
```

Scope to `Test` only — ScalaTest must not appear on the compile classpath.

---

### 5. Verilator — RTL Simulation Backend

#### 5.1 Minimum Version

**MUST** use Verilator 4.x or later. **SHOULD** use Verilator 5.x. Verify with
`verilator --version`.

#### 5.2 Installation

- **Linux:** package manager (`apt`, `pacman`, `dnf`) or build from source for the latest.
  Distro packages lag the Verilator release schedule; prefer source builds for 5.x.
- **macOS:** Homebrew (`brew install verilator`).
- **Windows:** WSL2 with a Linux Verilator installation. Native Windows is not supported
  by SpinalHDL's simulation layer.

#### 5.3 How SpinalHDL Invokes Verilator

`SimConfig.compile()` generates a Verilog wrapper for the DUT and invokes Verilator via
subprocess. The compiled `.so` is placed in `simWorkspace/<TestClass>/.cache/<hash>/verilator/`.
Verilator recompilation only runs when the RTL changes; unchanged designs use the cached `.so`.
This is why `workspacePath` must be unique per test class (see §43.1).

#### 5.4 Verilator Compilation Options

**SHOULD NOT** add `--lint-only` bypasses to suppress Verilator warnings in production.
Suppress only after understanding what the warning means. `--Wall` on the generated Verilog
is a useful CI check — see §50.3.

---

### 6. Metals — Scala Language Server (LSP)

#### 6.1 What Metals Provides

Metals gives go-to-definition, find-references, inline error reporting, auto-import, and
symbol rename. For SpinalHDL projects it is the primary navigation tool.

#### 6.2 VS Code Setup

1. Install the **Metals** extension (Scalameta publisher).
2. Open the project root. Metals will prompt: "Import build" — accept.
3. Metals runs `sbt bspConfig` internally; wait for indexing before relying on navigation.
4. **Re-import** after every `build.sbt` change.

#### 6.3 IntelliJ IDEA

Install the **Scala** plugin (not Metals — IntelliJ uses its own BSP integration). Import
as an sbt project and let IntelliJ resolve dependencies.

#### 6.4 `.metals/` and `.bsp/` Directories

Both are generated by Metals and are gitignored by the template. Safe to delete and
re-import. `metals.lock.db` and `metals.mv.db` are Metals state; not source of truth.

#### 6.5 Known Limitations in SpinalHDL Projects

Metals may report false positives on SpinalHDL DSL operators (`<<`, `:=`, `#=`,
`during build`). These are valid — the operators are defined by SpinalHDL's type system
and the IDSL plugin. The `sbt compile` output is authoritative; if it compiles, it is correct.
Metals may also not fully resolve SpinalHDL macros; do not file Metals bugs for SpinalHDL
elaboration errors.

#### 6.6 Scalafmt Integration

The template includes `sbt-scalafmt`. **SHOULD** add a `.scalafmt.conf` at the project root
and configure Metals to auto-format on save. Key concern: align Scalafmt's alignment rules
with SpinalHDL's conventional aligned-assignment style:

```scala
// Preferred alignment — add to .scalafmt.conf
align.preset = more
align.tokens = [{code = ":=", owner = "Term.ApplyInfix"}, ...]
```

---

### 7. Waveform Viewers

#### 7.1 Purpose

SpinalHDL simulation produces VCD files when `SimConfig.withWave` is set. These files are
unreadable without a viewer and are the primary debugging tool for failed simulations.
VCDs land in `simWorkspace/<TestClass>/<HarnessName>/<runName>/wave.vcd`.

#### 7.2 GTKWave

Mature, widely available on Linux, macOS, and Windows. Open a VCD:

```bash
gtkwave simWorkspace/TimerCoreTest/TimerHarness/increment/wave.vcd
```

#### 7.3 Surfer

Modern Rust-based viewer with better performance on large traces. Available as a native
binary or VS Code extension. **MAY** be preferred for traces with many signals.

#### 7.4 Signal Naming in Waveforms

`setName()` calls on SpinalHDL signals control the names that appear in the VCD hierarchy.
`periphName` prefixes (e.g., `counter_countReg`) appear as the register name in waveforms.
Named `Area` blocks prefix their contained signals (e.g., `logic_enable`). Name signals
intentionally — waveform debugging is where naming pays off.

---

### 8. Recommended IDE Extensions

#### 8.1 VS Code (priority order)

1. **Metals** (Scalameta) — mandatory for navigation
2. **Scala Syntax (official)** — syntax highlighting when Metals is indexing
3. **GitLens** — inline blame; useful for tracking when a signal name changed
4. **Rewrap** — reflows comment blocks to line length
5. **Even Better TOML** — for `.scalafmt.conf` and TOML-format config files

#### 8.2 IntelliJ

1. **Scala** plugin — mandatory
2. **Rainbow Brackets** — nested-block clarity in deep `when`/`Area` trees
3. **SonarLint** — static analysis; catches Scala-level issues before sbt does

---

### 9. Project Initialization and First Build

#### 9.1 Creating a New Project from This Template

1. Clone or fork `spinalhdl-template`.
2. Rename the package: global search-and-replace `mydesign` → `yourproject`.
3. Update `build.sbt`: set `name` and `organization`.
4. Delete or retain the example files (`Timer*`, `Comparator*`, `EdgeDetector*`) as reference.

#### 9.2 Renaming Checklist

- [ ] `build.sbt` — `name` and `organization`
- [ ] All `package mydesign` statements in `src/`
- [ ] `object GenVerilog` — update the `MyTop(Params())` instantiation if `MyTop` is renamed
- [ ] `ElaborationTest` — update the `MyTop` reference
- [ ] `TopIoExportPlugin` — update the `asInstanceOf[MyTop]` cast

#### 9.3 First Build Verification Sequence

Run these in order; each must succeed before the next:

1. `sbt compile` — must produce no errors or warnings
2. `sbt test` — elaboration and unit tests must pass
3. `sbt "runMain mydesign.GenVerilog"` — `rtl/MyTop.v` must be produced
4. Open `rtl/MyTop.v` and confirm port names match your `setName()` calls

#### 9.4 CI Pipeline Minimum

A project using this template **SHOULD** run the following stages in CI, in order:

1. `sbt compile` — catches syntax and type errors
2. `sbt test` — runs elaboration + all unit/integration tests
3. `sbt "runMain mydesign.GenVerilog"` — confirms Verilog generation is clean
4. `verilator --lint-only --Wall rtl/MyTop.v` — catches Verilog-level issues

All stages **MUST** be clean (zero errors, zero suppressed warnings) before a merge.
Failing Verilator lint on generated Verilog is always a SpinalHDL-level issue to fix,
not a lint rule to suppress.

#### 9.5 Metals First Import

After `sbt compile`, open VS Code and run `Metals: Import Build` from the command palette.
Wait for indexing to complete (progress bar in the status bar) before relying on navigation.
Metals indexing is slow on first import for SpinalHDL projects due to the transitive
dependency graph.

---

### 10. `.gitignore` Conventions

#### 10.1 Always Exclude

```gitignore
# sbt / Scala
target/
project/target/
project/project/
project/metals.sbt
.bsp/

# IDE
.idea/
.vscode/
.metals/
.bloop/

# SpinalHDL simulation workspace (large; always regenerated by tests)
simWorkspace/

# OS
.DS_Store
Thumbs.db
```

#### 10.2 The `rtl/` Directory

The template **tracks** `rtl/` (only `*.v.bak` is excluded). This is a valid choice when:

- Downstream consumers (board bring-up scripts, synthesis flows) pull the Verilog directly
  from the repo without an sbt environment.
- CI artifact pinning requires a known-good Verilog snapshot.

**SHOULD NOT** track `rtl/` when:

- The Verilog is always regenerated from source before use.
- The team wants the repo to be the single source of truth with no derived files.

**MUST** establish a team convention and document it. A tracked `rtl/` is not wrong —
but it requires discipline: every `Params` change must be followed by regeneration and
a commit of the new Verilog.

#### 10.3 Never Exclude

**MUST** commit:

- `project/build.properties` — sbt version pin
- `project/plugins.sbt` — plugin versions
- `.scalafmt.conf` — shared formatting config (if used)
- `docs/` — this guide and `ARCHITECTURE.md`

---

## Part I — Language Concepts and Terminology

### 11. The Three Phases of a SpinalHDL Design

SpinalHDL code executes in three distinct phases. Placing code in the wrong phase produces
either a runtime exception or, worse, silently incorrect hardware.

#### 11.1 Scala / Elaboration Phase (JVM Construction Time)

When Scala class and object bodies execute on the JVM before Fiber starts. At this phase:

- `Params` case class bodies run; `require()` guards fire.
- Plugin instances are created and collected into the `Seq[FiberPlugin]`.
- `MyTop` is constructed: the `io` Bundle is built, `setName()` calls run.
- `PluginHost` is created; `host.asHostOf(params.plugins)` registers plugins.
- No hardware signal nodes exist yet.

Equivalent to "generic binding" in VHDL or parameter evaluation in SystemVerilog.

#### 11.2 Fiber Setup Phase (`during setup { }`)

The `PluginHost` runs all `during setup` tasks after all plugins are registered but before
any `during build` task runs. Used for late parameter negotiation between plugins — for
example, a plugin queries a peer plugin's width to size its own internal structures.
Handles cannot be `await`ed in this phase; only Scala values can be exchanged.

#### 11.3 Fiber Build Phase (`during build { }`)

The phase where RTL hardware nodes are constructed. At this phase:

- `Reg`, `RegInit`, `RegNext`, `Mem`, `when`, `switch` — all create hardware nodes.
- `handle.load(signal)` publishes a signal to waiting consumers.
- `handle.await` blocks the current Fiber task until the Handle is loaded by its producer.
- Output of this phase: a complete hardware description graph that SpinalHDL emits as Verilog.

#### 11.4 Phase Assignment Rules

| Construct | Correct Phase |
|-----------|--------------|
| `require(scalaBoolean)` | Scala / Elaboration |
| `case class Params(...)` body | Scala / Elaboration |
| Plugin instance construction | Scala / Elaboration |
| Parameter negotiation between plugins | Fiber Setup |
| `Reg(T) init x` | Fiber Build |
| `when(cond) { ... }` | Fiber Build |
| `handle.load(signal)` | Fiber Build |
| `handle.await` | Fiber Build |

#### 11.5 Common Phase-Confusion Mistakes

**Reading a Handle outside `during build`:**
```scala
// WRONG — Handle.await called at Scala/Elaboration time
case class BadPlugin() extends FiberPlugin {
  val myHandle: Handle[Bool] = Handle()
  val value = myHandle.await  // crashes: Fiber hasn't started
}
```

**Calling `require()` on a hardware `Bool`:**
```scala
// WRONG — require() tests Scala Boolean; hardware Bool is always truthy as a Scala value
require(enable != null, "...")   // CORRECT: testing for null (Scala-level)
require(enable,         "...")   // WRONG: enable is a hardware node, not a Scala Boolean
```

**Placing `RegInit` outside a Component scope:**
```scala
// WRONG — hardware node created outside any elaborating Component
val r = RegInit(False)  // at object-level: no Component context
```

---

### 12. The Hardware Type System

#### 12.1 Scalar Types

| Type | Use |
|------|-----|
| `Bool` | Single-bit signal; boolean conditions, flags, enables |
| `Bits(n bits)` | Uninterpreted bit vector; raw data, packed structs |
| `UInt(n bits)` | Unsigned integer; counters, addresses, pixel values |
| `SInt(n bits)` | Signed integer; signed arithmetic, signed offsets |

Choose `UInt` or `SInt` intentionally and stay consistent within a datapath. SpinalHDL
will not silently coerce between signed and unsigned; explicit casts are required.

#### 12.2 `Bundle` — Structured Hardware Types

`Bundle` groups named fields with directional semantics (`in`/`out`). Used for:

- Top-level IO (`val io = new Bundle { ... }` in a Component)
- Sub-module port declarations
- Bus and protocol interfaces passed between Components

**MUST NOT** use `Bundle` as the return type of `XxxCore.build()`. The Core pattern uses
a plain Scala `case class Io` — see §18.3.

#### 12.3 `Vec[T]` — Homogeneous Hardware Arrays

`Vec[T]` is a hardware array of elements with the same type. Useful for register banks,
channel arrays, and pipeline stage arrays. Index with `UInt` for hardware-selectable access.

#### 12.4 Register Types

| Type | Description |
|------|-------------|
| `Reg(T) init x` | Register with reset value |
| `RegInit(x)` | Equivalent; preferred when init value is the primary intent |
| `RegNext(signal)` | Register that captures `signal` every cycle |
| `RegNextWhen(signal, cond)` | Register that captures `signal` when `cond` is true |

**MUST** always provide an `init` value on every register. An uninitialized register has
undefined reset state, which causes simulation/synthesis divergence and is a synthesis
lint warning.

#### 12.5 Protocol Types

| Type | Protocol |
|------|---------|
| `Flow[T]` | Valid-only; no back-pressure |
| `Stream[T]` | Valid + Ready handshake |
| `Fragment[T]` | Stream with packet boundary (`last` flag) |

#### 12.6 Signal Directions

- `in T` — input to this Component
- `out T` — output from this Component
- `inout T` / `TriState[T]` — bidirectional; use only for pad-facing signals

#### 12.7 `Bundle` vs `case class Io`

`Bundle` is a SpinalHDL hardware type with direction semantics. `case class Io` is a plain
Scala data container. The Core pattern returns `case class Io` (not `Bundle`) because:

- The Core does not know or care about signal direction — it creates registers and wires them.
- The Plugin decides what to do with the returned signal references.
- A plain case class cannot accidentally acquire direction semantics, be assigned to a port,
  or cause direction-mismatch errors.

#### 12.8 Width Inference vs Explicit Widths

SpinalHDL infers widths through expressions (addition grows width by 1, multiplication
grows by the sum of operand widths). Width inference inside a Core is acceptable and
intentional. At Core boundaries (`case class Io` fields) and Component IO boundaries,
widths **MUST** be explicit.

---

### 13. Areas, Components, and Scope

#### 13.1 `Component` — The RTL Module Boundary

`Component` generates a Verilog `module`. In the flat-plugin architecture, only these
are Components:

- `MyTop` — the one real Component; all plugin logic lives inside it
- `BlackBox` instances — vendor hard-IP wrappers
- CDC primitives — `StreamFifoCC` (itself a Component)

**MUST NOT** create additional Component subclasses for internal logic organization.
Use `Area` instead (see §13.2).

**ASIC:** The Plugin IP Component pattern (§24) adds one Component per encapsulated IP.
This is acceptable and intentional when delivering a Verilog module boundary as an API.

#### 13.2 `Area` — Named Grouping Without Hierarchy

`Area` scopes signal names without creating a Verilog module. Use it inside `during build`
blocks to group related logic and give signals a meaningful name prefix in waveforms:

```scala
val logic = during build new Area {
  // signals here are prefixed "logic_" in waveforms
  val enable = enableIn.await
  ...
}
```

The convention in the template is `val logic = during build new Area { ... }` — a single
Area named `logic` per plugin. This name appears as `logic_xxx` in the VCD hierarchy.

#### 13.3 `ClockingArea` — Scoping RTL to a Specific Clock Domain

All signals declared inside a `ClockingArea` belong to the given `ClockDomain`:

```scala
val pixLogic = new ClockingArea(pixDomain) {
  val reg = RegInit(False)  // belongs to pixDomain, not the default domain
}
```

**MUST** wrap any logic that runs on a non-default clock in a `ClockingArea`. Logic
outside a `ClockingArea` inherits the default clock domain — silently, with no error.

#### 13.4 `Component.current` — Accessing the Enclosing Component

`TopIoExportPlugin` uses this to access `MyTop.io`:

```scala
val top = Component.current.asInstanceOf[MyTop]
```

This is the correct pattern for accessing the Top component's static IO from inside a
plugin's `during build` block. The explicit cast is required because `Component.current`
returns the base `Component` type.

#### 13.5 Signal Scope Lifetime

Hardware signals created in `during build` exist for the lifetime of elaboration and
are part of the output Verilog. Scala `val` inside `during build` holds a reference to a
hardware node — it does not copy the node. Reassigning the `val` does not remove the node
from the hardware graph.

---

## Part II — Project Structure and Naming

### 14. File and Package Organization

#### 14.1 Package Naming

**MUST** match the package name to the project name. Replace `mydesign` with the project
name across all files as the first step after cloning.

#### 14.2 One Logical Unit Per File

**SHOULD** place one logical unit per file:

- One `object XxxCore` per file → `XxxCore.scala`
- One `case class XxxPlugin` per file → `XxxPlugin.scala`
- One `class XxxHarness` per file → `XxxHarness.scala`

Small companion types (e.g., `case class Io` nested inside `object XxxCore`) live in the
same file as their primary type.

#### 14.3 Source Tree Layout

```
src/main/scala/<pkg>/
├── Params.scala              — parameters and plugin list (one per project)
├── Top.scala                 — Top Component (IO declarations only)
├── GenVerilog.scala          — Verilog generation entry point
├── TopIoExportPlugin.scala   — the single IO wiring hub
├── TimerPlugin.scala       — one file per plugin
├── TimerCore.scala         — one file per core
└── ComparatorPlugin.scala
    ComparatorCore.scala
```

#### 14.4 Test Tree Layout

```
src/test/scala/<pkg>/
├── ElaborationTest.scala           — smoke test: full design elaborates
├── TimerCoreTest.scala           — unit tests per core
├── ComparatorCoreTest.scala
└── testhelpers/
    ├── TimerHarness.scala        — simulation wrapper per core
    └── ComparatorHarness.scala
```

#### 14.5 Output Directory Conventions

- `rtl/` — generated Verilog; tracked in the template (see §10.2)
- `simWorkspace/` — Verilator compilation artifacts; always gitignored
- `docs/` — design documentation; always tracked

---

### 15. Naming Conventions

#### 15.1 Components — `PascalCase`

`MyTop`, `TimerHarness`, `ComparatorHarness`. Noun or noun phrase describing what the
module represents.

#### 15.2 Plugins — `XxxPlugin` suffix, `case class`

```scala
case class TimerPlugin(width: Int = 8) extends FiberPlugin
case class ComparatorPlugin(threshold: Int = 128) extends FiberPlugin
```

#### 15.3 Cores — `XxxCore`, stateless `object`

```scala
object TimerCore { ... }
object ComparatorCore { ... }
```

#### 15.4 Harnesses — `XxxHarness`

```scala
class TimerHarness(width: Int = 8) extends Component { ... }
```

#### 15.5 Signals and Wires — `camelCase`

`enable`, `countVal`, `pixelData`. No underscores in signal names (underscores are for
`periphName`-prefixed register names at the Verilog level).

#### 15.6 Registers — `xxxReg` suffix

```scala
val countReg  = Reg(UInt(width bits)) init 0
val aboveReg  = RegInit(False)
val stateReg  = RegInit(MyFsm.Idle)
```

#### 15.7 Memories — `xxxMem` or `xxxBuffer` suffix

```scala
val lineMem    = Mem(UInt(8 bits), 1920)
val coeffTable = Mem(SInt(16 bits), 256)
```

#### 15.8 Bundles — `PascalCase` noun phrase

```scala
class PixelBus extends Bundle { ... }
class AhbLitePort extends Bundle { ... }
```

#### 15.9 Top IO Ports — `snake_case` via `setName()`

**MUST** call `setName()` on every IO port. Without it, SpinalHDL generates `io_enable`,
`io_count`, etc. — the `io_` prefix will not match any constraints file.

```scala
io.enable.setName("enable")
io.count.setName("count")
io.aboveFlag.setName("above_flag")
```

The `setName()` call converts the Scala-side `camelCase` name to the Verilog/constraints
`snake_case` name. Do this in `Top.scala` immediately after the IO Bundle declaration.

#### 15.10 `periphName` Prefix for Flat Verilog Uniqueness

In the flat architecture, all registers share one Verilog namespace (there is only one
module — `MyTop`). Without prefixes, two cores both defining `countReg` would collide.
The `periphName` parameter ensures uniqueness:

```scala
val countReg = Reg(UInt(width bits)) init 0
countReg.setName(s"${periphName}_countReg")   // → "counter_countReg" in Verilog

val aboveReg = RegInit(False)
aboveReg.setName(s"${periphName}_aboveReg")   // → "threshold_aboveReg" in Verilog
```

**MUST** apply `periphName` prefix and `setName()` to every `Reg()` and `Mem()` created
inside a Core. No exceptions.

**Note:** This rule applies to the flat architecture only. If using the hierarchical
Component pattern (§23) or Plugin IP Component (§24), module-level namespacing handles
uniqueness automatically and `periphName` prefixing is not required.

#### 15.11 Clock and Reset Signal Names

- Clock signals: `xxxClk` (e.g., `sysClk`, `pixClk`)
- Reset signals: `xxxRst` (active-high) or `xxxRstn` (active-low)
- `ClockDomain` Scala objects: `xxxDomain` (e.g., `pixDomain`, `sysDomain`)

#### 15.12 State Machine State Names — `PascalCase`

`Idle`, `Running`, `WaitAck`, `Done`. One word or compound noun per state.

#### 15.13 Handle Fields — Noun Describing What Is Published

- Input Handles: `xxxIn` suffix (e.g., `enableIn`, `pixelIn`, `dataIn`)
- Output Handles: `xxxOut` or descriptive noun (e.g., `countOut`, `aboveFlag`, `filteredOut`)

#### 15.14 Constants and `Params` Fields — `camelCase` with units in name

```scala
case class Params(
    sysClkHz:     HertzNumber = 100 MHz,   // units in name
    timerWidth: Int         = 8,          // "width" implies bits
    threshold:    Int         = 128         // domain-specific unit
)
```

#### 15.15 Test Class Names — `XxxCoreTest`, `XxxChainTest`, `ElaborationTest`

```
TimerCoreTest, ComparatorCoreTest, ElaborationTest
```

---

## Part III — The Flat-Plugin Architecture

> The flat-plugin architecture is the primary pattern of this template. It produces a single
> Verilog module (`MyTop`) with all logic from all features visible to the synthesis tool as
> one flat namespace. Features are developed as independent `FiberPlugin`s and composed at
> elaboration time.
>
> This same architectural philosophy drives VexRiscv — a production RISC-V softcore written
> entirely in SpinalHDL — where each CPU feature (branch predictor, cache, CSR, MMU) is an
> independent plugin composed at elaboration time. The patterns here are directly applicable
> to that scale of design.

---

### 16. The `Params` Case Class

#### 16.1 Single Source of Truth

**MUST** centralise all hardware parameters in a single `case class Params`. No numeric
constant may appear in a Core or Plugin unless it came from `Params`.

```scala
case class Params(
    sysClkHz:     HertzNumber = 100 MHz,
    timerWidth: Int         = 8,
    threshold:    Int         = 128
)
```

#### 16.2 `def plugins` vs `val plugins` — `def` Is Required

**MUST** declare the plugin list as `def`, not `val`:

```scala
// CORRECT
def plugins: Seq[FiberPlugin] = Seq(
  TimerPlugin(width = timerWidth),
  ComparatorPlugin(threshold = threshold),
  TopIoExportPlugin()
)

// WRONG — val shares plugin instances across elaboration calls
val plugins: Seq[FiberPlugin] = Seq(...)
```

`def` constructs fresh plugin instances on every call. Tests call `Params().plugins` each
time they elaborate a new design. If `val` were used, the same plugin objects would be
reused across elaboration runs, accumulating stale state and causing incorrect or
non-deterministic test failures.

#### 16.3 `require()` Guards — Validate Parameters at Construction Time

**MUST** validate all numeric parameters in the `Params` body:

```scala
case class Params(...) {
  require(timerWidth >= 1 && timerWidth <= 32,
    s"timerWidth must be 1..32, got $timerWidth")
}
```

Validation at construction time gives a clear error message at the point of misuse.
Validating inside `build()` is acceptable as a secondary check but does not replace
Params-level validation.

#### 16.4 `HertzNumber` for Clock Frequencies

**SHOULD** express clock frequencies as `HertzNumber` using SpinalHDL's unit syntax:

```scala
sysClkHz: HertzNumber = 100 MHz
```

`HertzNumber` values are passed to `SpinalConfig(defaultClockDomainFrequency = FixedFrequency(params.sysClkHz))`
for timing annotation and `ClockDomain` configuration. Using raw `Int` or `Long` for
frequencies loses the unit and makes mismatches invisible.

#### 16.5 Plugin List Order Is Cosmetic

The Fiber dependency resolver determines execution order from `load`/`await` relationships.
The order of plugins in `Seq(...)` is irrelevant to correctness. By convention, place
`TopIoExportPlugin` last — not because it must be, but because it is the wiring hub and
reading the list top-to-bottom reads as "features first, then wiring."

#### 16.6 Nested or Derived Parameters

**SHOULD** derive computed parameters in the `Params` body rather than in individual cores:

```scala
case class Params(
    imageWidth:  Int = 1920,
    imageHeight: Int = 1080
) {
  val pixelsPerFrame: Int = imageWidth * imageHeight  // derived
}
```

This ensures the derivation is validated once, is visible to all plugins, and is testable
by inspecting `Params` directly.

---

### 17. The Top Component

#### 17.1 Static IO Only — No RTL Logic in Top

**MUST NOT** place any RTL logic (registers, combinational assignments, `when` blocks,
state machines) inside `MyTop`. The Top Component is a pure IO declaration and
`PluginHost` container.

```scala
class MyTop(params: Params = Params()) extends Component {
  val io = new Bundle {
    // Inputs ──────────────────────────────────────────
    val enable    = in  Bool()
    // Outputs ─────────────────────────────────────────
    val count     = out UInt(params.timerWidth bits)
    val aboveFlag = out Bool()
  }

  io.enable.setName("enable")
  io.count.setName("count")
  io.aboveFlag.setName("above_flag")

  val host = new PluginHost
  host.asHostOf(params.plugins)
}
```

#### 17.2 IO Bundle Structure

**SHOULD** declare inputs first, then outputs, separated by a comment header:

```scala
val io = new Bundle {
  // Inputs ──────────────────────────────
  val enable  = in Bool()
  val dataIn  = in UInt(8 bits)
  // Outputs ─────────────────────────────
  val dataOut = out UInt(8 bits)
  val valid   = out Bool()
}
```

#### 17.3 `setName()` on Every Port — No Exceptions

**MUST** call `setName()` on every port immediately after the Bundle declaration, in port
order. SpinalHDL's default naming adds `io_` prefix which will never match a pinout or
constraints file.

```scala
// Immediately after the Bundle
io.enable.setName("enable")
io.count.setName("count")
io.aboveFlag.setName("above_flag")
```

#### 17.4 `PluginHost` Construction

**MUST** construct the `PluginHost` and call `host.asHostOf(params.plugins)` as the last
item in the Top body:

```scala
val host = new PluginHost
host.asHostOf(params.plugins)
```

This is what starts the Fiber resolution process. Everything above it (IO, setName calls)
is guaranteed to be complete before any plugin's `during build` block begins.

#### 17.5 Allowed Sub-Items Inside Top

- `BlackBox` instances for vendor hard-IP (PLLs, SERDES, IO buffers)
- CDC primitives (`StreamFifoCC`) when the design is multi-clock
- Plugin IP Components (§24) when encapsulating a feature as a sub-module

No other Components may be instantiated inside Top in the flat-plugin pattern.

---

### 18. The Core Pattern (`object XxxCore`)

#### 18.1 `object`, Not `class` — Stateless by Construction

**MUST** declare Cores as Scala `object`, not `class`. An `object` is a singleton —
it carries no instance state. All state lives in the hardware nodes created by `build()`,
not in the Core itself.

```scala
object TimerCore {
  case class Io(count: UInt)

  def build(
      periphName: String = "timer",
      width:      Int    = 8,
      enable:     Bool   = null
  ): Io = { ... }
}
```

#### 18.2 `def build()` Signature Conventions

**MUST** follow this parameter convention:

- `periphName: String` as the first parameter with a default that matches the feature name.
- Hardware signal arguments typed as SpinalHDL types, defaulted to `null`.
- `require(signal != null, "...")` guard for every mandatory signal argument.
- Numeric parameters defaulted to sensible values (matching `Params` defaults).

```scala
def build(
    periphName: String = "timer",  // 1st: naming
    width:      Int    = 8,          // 2nd: numeric params
    enable:     Bool   = null        // 3rd: signal args, null-defaulted
): Io = {
  require(enable != null, "enable signal is required")
  require(width >= 1, s"width must be >= 1, got $width")
  ...
}
```

#### 18.3 `case class Io` Return Type — Not `Bundle`

**MUST** return a plain Scala `case class Io`, not a SpinalHDL `Bundle`:

```scala
// CORRECT
case class Io(count: UInt)

// WRONG — Bundle adds direction semantics the Core doesn't need
class Io extends Bundle { val count = out UInt(8 bits) }
```

The `case class` is a plain Scala container holding references to hardware nodes. It
carries no direction semantics, cannot cause direction-mismatch errors, and can hold any
type including non-hardware values.

#### 18.4 `periphName` Prefix on All Internal Registers and Memories

**MUST** call `setName(s"${periphName}_xxxReg")` on every `Reg()` and `Mem()`:

```scala
val countReg = Reg(UInt(width bits)) init 0
countReg.setName(s"${periphName}_countReg")

val aboveReg = RegInit(False)
aboveReg.setName(s"${periphName}_aboveReg")
```

Without this, two cores with identically-named registers produce duplicate signal names
in the flat Verilog module, causing a generation error.

#### 18.5 No Component Creation Inside `build()`

**MUST NOT** call `new SomeComponent(...)` inside `build()`. Component instantiation
requires a parent Component context and would insert a module boundary into the flat
design. Use `Area` for grouping instead.

#### 18.6 Bus-Agnostic Design

**MUST** accept raw SpinalHDL signals (`Bool`, `UInt`, `Stream[T]`) rather than bus-specific
bundles. The Plugin is responsible for extracting what the Core needs from whatever bus the
design uses. This makes Cores reusable across bus architectures.

```scala
// CORRECT — accepts a raw Bool
def build(enable: Bool = null): Io = { ... }

// WRONG — couples the Core to a specific bus type
def build(apb: Apb3 = null): Io = { ... }
```

#### 18.7 Fail-Fast Validation

**MUST** place `require()` guards at the top of `build()` before any hardware node is
created. A failed `require()` during elaboration produces a clear message; a hardware
node created from a `null` signal produces a NullPointerException with no useful context.

#### 18.8 Pipeline Stages as Named Areas

When a Core requires multiple clock cycles to compute its output, model each stage as a
named `Area`. This is the VexRiscv pattern: Fetch, Decode, Execute, Memory, WriteBack
are each a named `Area` within the single flat module.

**SHOULD** name each pipeline stage as a named `Area` rather than using anonymous signal
declarations. Named Areas appear as scope prefixes in waveforms, making the pipeline
visible to the debugger without adding hardware hierarchy.

```scala
object EdgeDetectorCore {

  case class Io(rising: Bool, falling: Bool)

  def build(
      periphName: String = "edgeDetector",
      input:      Bool   = null
  ): Io = {
    require(input != null, "input signal is required")

    // Stage 1 — sample the input
    val prevStage = new Area {
      val prev = RegNext(input) init False
      prev.setName(s"${periphName}_prev")
    }

    // Stage 2 — detect edges
    val edgeStage = new Area {
      val rising  = RegNext(input && !prevStage.prev) init False
      val falling = RegNext(!input && prevStage.prev) init False
      rising.setName(s"${periphName}_rising")
      falling.setName(s"${periphName}_falling")
    }

    Io(rising = edgeStage.rising, falling = edgeStage.falling)
  }
}
```

Waveform signal names: `prevStage_prev`, `edgeStage_rising`, `edgeStage_falling`. The
stage name prefix immediately locates each signal in the pipeline.

**1-cycle latency:** both registers update on the same rising edge. The detection
`input && !prev` uses the pre-edge value of `prev`, so `edgeStage_rising` pulses on the
same edge that `prevStage_prev` latches the new input. Simulation tests use `waitSampling(1); sleep(1)`.

**MUST** document the pipeline latency in a comment at the top of the Core or in the `Io`
case class, so callers know how many cycles to wait before sampling outputs.

**MUST** propagate the `periphName` prefix to registers in every stage, as shown above.
Without this, two EdgeDetector instances in the same flat module have colliding signal names.

**SHOULD** name `Area`s descriptively: `prevStage`, `edgeStage`, `decodeStage` rather
than `stage1`, `stage2`. Descriptive names survive pipeline refactors and are readable
in waveforms without the source code open.

#### 18.9 Testing Multi-Stage Cores — Latency Accounting

Unit tests for pipeline Cores must wait for the pipeline to drain before asserting outputs.

```scala
// EdgeDetectorCore has 1-cycle latency.
// Use waitSampling(1) then sleep(1) to observe the registered output.

test("rising edge detected") {
  compile().doSim("rising") { dut =>
    dut.clockDomain.forkStimulus(period = 10)
    dut.io.input #= false
    dut.clockDomain.waitSampling(3)
    dut.io.input #= true          // rising edge here
    dut.clockDomain.waitSampling(1)
    sleep(1)
    assert(dut.io.rising.toBoolean,  "Expected rising edge")
    assert(!dut.io.falling.toBoolean, "Expected no falling edge")
  }
}
```

**Rule:** for a Core with N registered pipeline stages, call `waitSampling(N); sleep(1)`
after the final input stimulus before asserting any output. Note: when detection logic
uses the pre-edge value of a registered signal (e.g. `input && !prev`), the effective
latency collapses — both registers fire at the same edge. Count observable output cycles,
not register depth.

For integration tests chaining multiple Cores, sum the latencies:
`waitSampling(latency_A + latency_B); sleep(1)`.

---

### 19. The Plugin Pattern (`FiberPlugin`)

#### 19.1 `case class XxxPlugin` — Why `case class`

**MUST** declare plugins as `case class`, not `class`. Case class semantics give:

- Constructor parameters directly accessible as fields.
- Value equality — useful for elaboration-time comparison.
- Immutability by default — plugin state is in Handles and the hardware graph, not in Scala fields.

#### 19.2 Declaring `Handle[T]` Fields

Every plugin declares its Handles as `val` fields on the class body (outside `during build`):

```scala
case class TimerPlugin(width: Int = 8) extends FiberPlugin {

  // Output Handle: this plugin produces this and loads it
  val countOut: Handle[UInt] = Handle[UInt]()

  // Input Handle: TopIoExportPlugin loads this; this plugin awaits it
  val enableIn: Handle[Bool] = Handle[Bool]()

  val logic = during build new Area { ... }
}
```

Handles declared on the class body are visible to peer plugins via `host[TimerPlugin]`
before the build phase begins. This is how `TopIoExportPlugin` accesses them in Phase 1.

#### 19.3 `val logic = during build new Area { }` — The Build Area

**SHOULD** use `val logic = during build new Area { ... }` as the single build block per
plugin. The name `logic` is conventional and scopes generated signal names:

```scala
val logic = during build new Area {
  val enable = enableIn.await   // appears as "logic_enable" in waveforms

  val core = TimerCore.build(
    periphName = "timer",
    width      = width,
    enable     = enable
  )

  countOut.load(core.count)
}
```

**SHOULD** use one `during build` block per plugin. Multiple `during build` blocks per
plugin are technically valid but make the signal naming less predictable and the flow
harder to follow.

**SHOULD** choose the Area name to aid waveform debugging. `logic` is the correct default
name for a single-area plugin. A plugin with distinct sub-regions (e.g., a FIFO plugin
with separate read and write domains) may use multiple named Areas.

#### 19.4 No RTL Logic in the Plugin Class Body

**MUST NOT** place RTL-constructing calls (any SpinalHDL hardware API) in the plugin class
body outside a `during build` or `during setup` block. The class body executes at
Scala/Elaboration time when no Component context exists.

```scala
// WRONG — Reg() called at Scala/Elaboration time
case class BadPlugin() extends FiberPlugin {
  val myReg = RegInit(False)   // crashes: no Component context
  val logic = during build new Area { ... }
}

// CORRECT — Reg() called inside during build
case class GoodPlugin() extends FiberPlugin {
  val logic = during build new Area {
    val myReg = RegInit(False)
  }
}
```

#### 19.5 Accessing Peer Plugins with `host[OtherPlugin]`

**MUST** access other plugins via `host[OtherPlugin]` inside the `during build` block:

```scala
val logic = during build new Area {
  val timerPlugin = host[TimerPlugin]         // get the plugin instance
  val countVal    = timerPlugin.countOut.await      // await its output
  ...
}
```

`host[T]` throws if the plugin is not present. Use `Try(host[T]).toOption` when the
plugin is optional (see §20.2).

#### 19.6 `during setup` vs `during build`

Use `during setup` only when a plugin needs to negotiate parameters with peers before RTL
construction begins — for example, agreeing on a data width. All RTL creation belongs in
`during build`. Most plugins never need `during setup`.

#### 19.7 Plugin Constructor Parameters vs `Params`

Plugin constructor parameters come from `Params`:

```scala
// In Params:
def plugins = Seq(
  TimerPlugin(width = timerWidth),   // parameter threaded in here
  ...
)

// Plugin receives it as a constructor parameter:
case class TimerPlugin(width: Int = 8) extends FiberPlugin { ... }
```

**MUST NOT** hard-code numeric values in plugin constructor arguments inside `Params.plugins`.
All values **MUST** come from `Params` fields.

#### 19.8 Optional Plugins — `Try(host[X]).toOption`

When a plugin may or may not be present in the plugin list, use `Try(host[X]).toOption`
to access it safely. This is the standard pattern for optional features.

```scala
// EdgeDetectorPlugin: requires ComparatorPlugin (mandatory dependency)
// but is itself optional in the plugin list.
case class EdgeDetectorPlugin() extends FiberPlugin {

  val risingEdge:  Handle[Bool] = Handle[Bool]()
  val fallingEdge: Handle[Bool] = Handle[Bool]()

  val logic = during build new Area {
    val comp = host[ComparatorPlugin]     // mandatory — throws if absent
    val flag = comp.aboveFlag.await

    val core = EdgeDetectorCore.build(
      periphName = "edgeDetector",
      input      = flag
    )

    risingEdge.load(core.rising)
    fallingEdge.load(core.falling)
  }
}
```

`TopIoExportPlugin` then accesses `EdgeDetectorPlugin` optionally:

```scala
Try(host[EdgeDetectorPlugin]).toOption match {
  case Some(edge) =>
    top.io.risingEdge  := edge.risingEdge.await
    top.io.fallingEdge := edge.fallingEdge.await
  case None =>
    top.io.risingEdge  := False
    top.io.fallingEdge := False
}
```

**Rule:** use `host[X]` (throws) for required dependencies; use `Try(host[X]).toOption`
for optional ones. A plugin that uses `host[X]` for a required peer must document that
peer as a prerequisite in a comment so users of `Params.plugins` know the ordering
constraint.

---

### 20. `TopIoExportPlugin` — The Wiring Hub

#### 20.1 The Two-Phase Discipline — Load Before Await

**MUST** structure `TopIoExportPlugin` in two phases with all loads before all awaits:

```scala
case class TopIoExportPlugin() extends FiberPlugin {
  val logic = during build new Area {
    val top = Component.current.asInstanceOf[MyTop]

    // ── Phase 1: LOAD all input Handles (non-blocking) ──────
    // Feeds pad signals into plugins. Completes immediately.
    Try(host[TimerPlugin]).toOption.foreach { timer =>
      timer.enableIn.load(top.io.enable)
    }

    // ── Phase 2: AWAIT all output Handles (blocking) ────────
    // Blocks until each plugin loads its output Handle.
    Try(host[TimerPlugin]).toOption match {
      case Some(timer) => top.io.count    := timer.countOut.await
      case None        => top.io.count    := 0
    }

    Try(host[ComparatorPlugin]).toOption match {
      case Some(comp) => top.io.aboveFlag := comp.aboveFlag.await
      case None       => top.io.aboveFlag := False
    }
  }
}
```

**Why the order matters:** If Phase 2 runs first, `TopIoExportPlugin` blocks waiting for
`timer.countOut`. But `TimerPlugin` is blocked waiting for `enableIn` — which Phase 1
hasn't loaded yet. Deadlock. Loads are non-blocking and always run first; this guarantees
every plugin's input Handles are satisfied before any plugin's output is awaited.

#### 20.2 `Try(host[X]).toOption` — Safe Optional Plugin Access

**MUST** use `Try(host[X]).toOption` when a plugin may or may not be in the plugin list:

```scala
// Safe — returns None if TimerPlugin is absent
Try(host[TimerPlugin]).toOption match {
  case Some(counter) => ...
  case None          => ...
}

// Unsafe — throws if plugin is absent
host[TimerPlugin]
```

This pattern is what makes the plugin list in `Params` modular: plugins can be added or
removed without changing `TopIoExportPlugin` for every combination.

#### 20.3 `case None` Branches — Always Provide Safe Defaults

**MUST** drive every output port to a safe constant when its plugin is absent:

```scala
case None => top.io.count    := 0      // CORRECT: drives to known value
case None => top.io.aboveFlag := False  // CORRECT: drives to known value
// WRONG: leaving a case None branch unimplemented leaves an undriven port
```

Document what "safe" means for each port. For a counter output, zero is safe. For an
enable flag, `False` (inactive) is safe. Choosing the wrong safe default can cause
board-level issues.

#### 20.4 One Wiring Hub Per Design

**MUST** have exactly one `TopIoExportPlugin` per `PluginHost`. In the Plugin IP Component
pattern (§24), each Plugin IP Component has its own scoped IO export plugin.

---

### 21. Managing Top-Level IO and Port Exposure

#### 21.1 The Static/Dynamic Mismatch

`MyTop.io` is a Scala `Bundle` constructed at Scala/Elaboration time — before any Fiber
task runs. Plugin signals are constructed during `during build`. This means:

**MUST** declare every IO port that might ever be needed upfront in `MyTop.io`. You cannot
conditionally declare a port based on which plugins are present at elaboration time.

#### 21.2 The Two-Site Rule — Adding a New Port

Adding a new plugin output to the top level requires changes in **exactly two places**:

1. **`Top.scala`** — add the port to the `io` Bundle and add a `setName()` call.
2. **`TopIoExportPlugin.scala`** — await the Handle in Phase 2 and drive the new port.

Forgetting site 1 means there is no port to drive. Forgetting site 2 means the port
is declared but undriven, which will be flagged by both SpinalHDL and the synthesis tool.

#### 21.3 Port Direction Is Declared at Top, Not in the Plugin

The plugin has no knowledge of whether its output becomes a top-level port or an internal
wire. `TopIoExportPlugin` decides. The plugin simply loads a Handle; `TopIoExportPlugin`
reads it and drives whatever destination is appropriate.

#### 21.4 Optional Ports — Strategy A vs Strategy B

When a port is driven only when its plugin is present:

- **Strategy A (preferred):** Always declare the port; provide a `case None` default.
  Simpler `TopIoExportPlugin`; no Verilog regeneration needed for different plugin configs.
- **Strategy B:** Gate the port behind a `Params` Boolean; only declare it when the plugin
  is enabled. Cleaner Verilog interface; requires regeneration for each configuration.

The template uses Strategy A.

#### 21.5 Passing Pad Inputs Into Plugins — Phase 1 Pattern

**MUST** load pad inputs into plugin Handles in Phase 1 of `TopIoExportPlugin`:

```scala
// Phase 1 — non-blocking load
Try(host[TimerPlugin]).toOption.foreach { timer =>
  timer.enableIn.load(top.io.enable)
}
```

**MUST NOT** have a plugin read a pad signal directly via `Component.current`. All pad
signals enter plugins through Handles loaded by `TopIoExportPlugin`. This preserves the
Handle contract and keeps plugins bus-agnostic.

#### 21.6 Exposing Debug / Observability Ports

Internal register values can be promoted to top-level ports for in-circuit probing:

```scala
// In Top.scala:
val debugCount = out UInt(8 bits)
io.debugCount.setName("debug_count")

// In TopIoExportPlugin, Phase 2:
Try(host[TimerPlugin]).toOption match {
  case Some(c) => top.io.debugCount := c.countOut.await
  case None    => top.io.debugCount := 0
}
```

**SHOULD** gate debug ports behind a `Params.debug: Boolean` flag so they are removed
from production builds. Debug ports not connected to physical pins consume routing resources
and appear in the constraints file as unplaced ports.

#### 21.7 Port Naming Discipline

**MUST** have an explicit `setName()` call for every port in `MyTop.io`. Port names must
match the pinout and constraints file exactly (case-sensitive on most tools). Renaming a
port is a breaking change — update XDC/SDC/QSF constraints and communicate the change
to anyone consuming the generated Verilog.

#### 21.8 APB3 Bus Interface — `ApbMonitorPlugin` Pattern

An optional plugin can expose plugin outputs as APB3-accessible registers using
`Apb3SlaveFactory` from `spinalhdl-lib`. This is the RISC-V peripheral integration
pattern: a processor reads design status through an APB3 bus without the plugin needing
to know anything about the processor.

**Declare individual APB3 signals in `Top.scala` as flat ports** rather than a native
`slave(Apb3(...))` bundle. Flat port names are explicit, transparent to synthesis tools,
and match what a RISC-V SoC bus matrix expects.

```scala
// In Top.scala — declare APB3 input and output ports individually:
val apb_PADDR    = in  UInt(8 bits)
val apb_PSEL     = in  Bits(1 bits)
val apb_PENABLE  = in  Bool()
val apb_PWRITE   = in  Bool()
val apb_PWDATA   = in  Bits(32 bits)
val apb_PREADY   = out Bool()
val apb_PRDATA   = out Bits(32 bits)
val apb_PSLVERR  = out Bool()
```

In `ApbMonitorPlugin`, reconstruct the `Apb3` bundle from those individual signals,
then use `Apb3SlaveFactory` to declare the register map:

```scala
case class ApbMonitorPlugin() extends FiberPlugin {

  val logic = during build new Area {
    val top = Component.current.asInstanceOf[MyTop]

    // Reconstruct Apb3 bundle from flat top-level signals
    val apbConfig = Apb3Config(addressWidth = 8, dataWidth = 32)
    val apb       = Apb3(apbConfig)

    apb.PADDR   := top.io.apb_PADDR
    apb.PSEL    := top.io.apb_PSEL
    apb.PENABLE := top.io.apb_PENABLE
    apb.PWRITE  := top.io.apb_PWRITE
    apb.PWDATA  := top.io.apb_PWDATA

    top.io.apb_PREADY  := apb.PREADY
    top.io.apb_PRDATA  := apb.PRDATA
    top.io.apb_PSLVERR := apb.PSLVERR

    // Register map via Apb3SlaveFactory
    val factory = Apb3SlaveFactory(apb)

    // 0x00 — Timer count (read only, zero-extended to 32 bits)
    Try(host[TimerPlugin]).toOption.foreach { timer =>
      factory.read(timer.countOut.await.resize(32), 0x00)
    }

    // 0x04 — Comparator above flag (read only, bit 0)
    Try(host[ComparatorPlugin]).toOption.foreach { comp =>
      factory.read(comp.aboveFlag.await.asUInt.resize(32), 0x04)
    }
  }
}
```

**Register map (byte addresses, 32-bit data):**

| Address | Content | Access |
|---------|---------|--------|
| 0x00    | Timer count value (zero-extended) | Read only |
| 0x04    | Comparator above flag (bit 0) | Read only |

Write transactions are silently ignored — `PREADY` is asserted with no state change.
`PSLVERR` is always deasserted. `Apb3SlaveFactory` handles these defaults automatically.

**Safe defaults when `ApbMonitorPlugin` is absent:** `TopIoExportPlugin` drives
`apb_PREADY := True`, `apb_PRDATA := 0`, `apb_PSLVERR := False` in its `case None` branch,
so the top-level ports are always driven regardless of which plugins are present.

**Adding registers:** call `factory.read(signal.resize(32), address)` for each new
read-only register. `Apb3SlaveFactory` from `spinalhdl-lib` handles the address decode,
PREADY generation, and PRDATA mux automatically.

**ASIC:** for ASIC integration, replace the `Apb3SlaveFactory` with a `RegIf` or custom
address-decode logic if the target SoC uses a proprietary register description format.
The flat port approach here remains valid regardless.

---

### 22. Handle Lifecycle and Fiber Mechanics

#### 22.1 Handle States: Created → Loaded → Awaited

```
Handle[T]()       →   handle.load(signal)   →   handle.await
(empty, blocking)     (signal bound)             (returns signal)
```

A Handle can be awaited by multiple consumers; all receive the same signal reference
once it is loaded. A Handle can only be loaded once; loading it twice is an error.

#### 22.2 The Load/Await Dependency Graph

Fiber builds a directed graph from `load`/`await` relationships:

- `TimerPlugin` awaits `enableIn` → blocks until `TopIoExportPlugin` loads it.
- `TimerPlugin` loads `countOut` → unblocks `ComparatorPlugin` which awaits it.
- `ComparatorPlugin` loads `aboveFlag` → unblocks `TopIoExportPlugin`'s Phase 2.

Fiber executes tasks in dependency order; no manual ordering is required or possible.

#### 22.3 The Missing `load()` Antipattern

If a Handle is awaited but never loaded, Fiber waits indefinitely. SpinalHDL detects this
as a timeout and reports which Handle is unresolved. The fix is always: find the plugin
that should call `load()` on that Handle and ensure it is in the plugin list.

Common cause: a plugin is removed from `Params.plugins` but another plugin still awaits
one of its Handles.

#### 22.4 Circular Dependency Detection

Fiber detects circular `await` dependencies and reports them as a deadlock. The most common
cause is two plugins each awaiting the other's Handle. Break the cycle by having one plugin
compute its output independently and the other await it — never the reverse.

#### 22.5 Debugging Fiber Deadlocks

1. Read the SpinalHDL error message — it names the blocking Handle.
2. Find which plugin should `load()` that Handle.
3. Confirm that plugin is in `Params.plugins`.
4. Confirm the plugin's `load()` call is in Phase 1 (for inputs) or will execute before
   the deadlocking `await` (for inter-plugin dependencies).

#### 22.6 When to Use `Handle` vs Direct Signal Passing

Use a Handle when crossing a plugin boundary. Within a single plugin's `during build`
block, use plain Scala `val` to hold signal references:

```scala
val logic = during build new Area {
  val enable  = enableIn.await        // Handle crossing: use await
  val core    = TimerCore.build(enable = enable)
  val delayed = RegNext(core.count)   // plain Scala val: fine within the Area
  countOut.load(delayed)              // Handle crossing: use load
}
```


---

## Part IV — Design Architecture Patterns

> This part describes the architectural choices available when building on the flat-plugin
> template. The flat-plugin architecture is the default; this part explains when and how to
> deviate from it.

### 23. Flat Monolithic vs Hierarchical Component Design

#### 23.1 The Two Fundamental Styles

**Flat Monolithic (this template's default)**
- Single `Component` (`MyTop`); all RTL lives in `FiberPlugin`s.
- Generates flat Verilog — one module, all signals visible to synthesis globally.
- Cross-plugin signal paths are unconstrained; the optimizer has full visibility.
- VexRiscv uses this style: one `VexRiscv` module, all pipeline stage logic visible.

**Hierarchical Component-Based**
- Multiple `Component` subclasses, each generating a Verilog `module`.
- Traditional HDL structure; necessary for IP delivery.
- Synthesis optimizer cannot move logic across module boundaries unless the tool flattens.

#### 23.2 Generated Verilog Implications

| Criterion | Flat | Hierarchical |
|-----------|------|-------------|
| Synthesis visibility | Full — optimizer sees everything | Bounded at module edges |
| Timing analysis | All paths in one timing domain | Cross-boundary paths need explicit constraints |
| IP delivery | Requires wrapping (§24) | Natural — each Component is a Verilog module |
| Signal naming | Requires `periphName` prefix | Module hierarchy handles uniqueness |
| Waveform hierarchy | Flat — all signals at one level | Mirrors module hierarchy |

#### 23.3 When Each Style Suits the Problem

Use flat when: single FPGA or ASIC, one team, no IP delivery, timing-critical paths that
benefit from global optimizer visibility.

Use hierarchical (via the Plugin IP Component pattern below) when: the block will be
delivered to another team as a Verilog module, will be reused across projects, or needs
an isolated testbench.

---

### 24. The Plugin IP Component — Encapsulating a Plugin as Reusable IP

#### 24.1 Motivation

Any `FiberPlugin` (or group of plugins) can be wrapped inside a `Component` with its own
`PluginHost` and wiring plugin. The resulting Component is instantiated like any
conventional HDL sub-module and delivers a clean Verilog module boundary.

Use this pattern when:

- The block will be reused across multiple projects.
- The block will be delivered as a Verilog module to another team or tool.
- The block needs to be verified in isolation with its own standalone testbench before
  integration into a larger design.

#### 24.2 Structure of a Plugin IP Component

```scala
class UartIpComponent(params: UartParams) extends Component {

  // ── External interface ─────────────────────────────────────────
  val io = new Bundle {
    val txd   = out Bool()
    val rxd   = in  Bool()
    val txData = slave(Stream(Bits(8 bits)))
    val rxData = master(Stream(Bits(8 bits)))
  }
  io.txd.setName("txd")
  io.rxd.setName("rxd")

  // ── Inner Fiber graph ──────────────────────────────────────────
  val host = new PluginHost
  host.asHostOf(Seq(
    UartTxPlugin(baudRate = params.baudRate),
    UartRxPlugin(baudRate = params.baudRate),
    UartIoExportPlugin()          // scoped to this Component
  ))
}
```

The inner `UartIoExportPlugin` wires inner Handles to `this.io`, following the same
two-phase load/await discipline as `TopIoExportPlugin`.

#### 24.3 Plugin IP Component vs Top Component

- A Plugin IP Component can be instantiated multiple times (e.g., two UARTs).
- Its inner Fiber graph is self-contained; the outer design has no visibility into
  its Handles.
- Its Verilog module port list is an API contract: treat port renames as breaking changes.

#### 24.4 Instantiating in the Flat Design

```scala
class MyTop(params: Params = Params()) extends Component {
  val io = new Bundle { ... }

  // Plugin IP Component instantiated inside Top
  val uart = new UartIpComponent(params.uartParams)
  uart.io.txd <> io.uart_txd
  uart.io.rxd <> io.uart_rxd

  // Remaining flat plugins
  val host = new PluginHost
  host.asHostOf(params.otherPlugins)
}
```

**Note:** Once a Plugin IP Component is instantiated, Handles cannot cross the Component
boundary. Signals must be declared as Bundle ports and connected with `:=` or `<>`.

#### 24.5 Trade-offs

| Pro | Con |
|-----|-----|
| Testable in isolation | Verilog module boundary limits synthesis visibility |
| Deliverable as a module | More boilerplate (IO Bundle, scoped export plugin) |
| Reusable across projects | Adds a hierarchy level; flat timing view is lost |

---

### 25. Mixing Both Styles

#### 25.1 Handles Stop at Component Boundaries

Handles are intra-Component. Once a Component boundary exists, signals cross via Bundle
ports connected with `:=` or `<>`. Use `Stream[T]` and `Flow[T]` in Bundle fields when
passing data between Components to preserve protocol semantics.

#### 25.2 Antipatterns to Avoid

- Wrapping a Component in a FiberPlugin just to get Fiber ordering — use `during setup`
  parameter negotiation instead.
- Creating Component hierarchy purely to organise code — use named `Area`s.
- Passing a plugin Handle reference through a Component boundary (it is a Scala reference
  to an intra-Component signal; it cannot be used from outside).

---

### 26. When to Choose Each Pattern

| Situation | Recommended Pattern |
|-----------|-------------------|
| Single FPGA/ASIC, one codebase, no IP delivery | Flat FiberPlugin (this template default) |
| Feature reused across ≥ 2 projects | Plugin IP Component (§24) |
| Block delivered to another team as Verilog | Plugin IP Component (§24) |
| VexRiscv peripheral integration | Flat plugin alongside `VexRiscv` Component |
| System with VexRiscv + peripherals + CDC | Hierarchical top + Plugin IP Components |
| Performance-critical path needing full synthesis visibility | Flat, no Component boundaries |


---

## Part V — RTL Design Rules

### 27. Registers and Combinational Logic

#### 27.1 Always Initialize Registers

**MUST** provide an `init` value on every register:

```scala
val countReg = Reg(UInt(width bits)) init 0   // CORRECT
val flagReg  = RegInit(False)                  // CORRECT
val dataReg  = Reg(UInt(8 bits))               // WRONG — no reset value
```

A register without `init` has undefined reset state. Simulation may initialize it
randomly (exposing bugs), but synthesis tools initialize flip-flops based on their reset
pin — which differs by vendor. The result is simulation/synthesis divergence that is
extremely hard to debug.

#### 27.2 `RegInit(x)` vs `Reg(T) init x`

Both are equivalent. **SHOULD** use `RegInit(x)` when the reset value is the primary
semantic (counters, flags, state machines). **SHOULD** use `Reg(T) init x` when the type
annotation adds clarity (e.g., when the width is large and needs to be visible).

#### 27.3 `RegNext` and `RegNextWhen` — Pipeline Register Shortcuts

```scala
val delayed  = RegNext(core.count)                    // captures every cycle
val gated    = RegNextWhen(core.count, valid)          // captures when valid is true
val delayedF = RegNext(core.count) init 0              // with reset value
```

**SHOULD** use these forms for pipeline stages rather than a bare `Reg` + `when` block —
they are more concise and explicitly document the pipeline intent.

#### 27.4 Combinational Loops — Prohibited

SpinalHDL detects combinational loops at elaboration time and reports them as an error.
If you see a loop error, the design is incorrect — do not attempt to suppress it. Find the
feedback path and break it with a register.

#### 27.5 `when` / `elsewhen` / `otherwise` Completeness

**MUST** either provide an `otherwise` branch or assign default values above the `when`
block for every signal assigned inside it:

```scala
// CORRECT — default assignment before when
val result = UInt(8 bits)
result := 0             // default
when(sel === 0) { result := a }
  .elsewhen(sel === 1) { result := b }

// CORRECT — otherwise branch
when(sel === 0) { result := a }
  .otherwise    { result := 0 }

// WRONG — result is undriven when sel > 1 (infers a latch)
when(sel === 0) { result := a }
  .elsewhen(sel === 1) { result := b }
```

SpinalHDL will warn about undriven signals; treat all such warnings as errors.

#### 27.6 Explicit Widths at Core and Component Boundaries

Within a Core, width inference is acceptable. At the `case class Io` return boundary
and at Component IO ports, **MUST** use explicit widths:

```scala
case class Io(count: UInt)              // WRONG — width inferred from signal
case class Io(count: UInt)              // inferred is fine inside the Core
// but at the Harness or Top level:
val count = out UInt(width bits)        // MUST be explicit
```

---

### 28. Clock Domains

#### 28.1 Default Clock Domain

SpinalHDL's defaults for the implicit clock domain: rising edge, asynchronous reset,
active-high reset. Know these defaults before using them. For a simple single-clock design
on a pad clock, the defaults are productive. For any multi-clock design, configure each
domain explicitly.

`ClockDomainConfig` fields:

```scala
ClockDomainConfig(
  resetKind        = ASYNC,    // or SYNC, BOOT
  resetActiveLevel = HIGH,     // or LOW
  clockEdge        = RISING    // or FALLING
)
```

#### 28.2 Creating Secondary Clock Domains

```scala
val pixDomain = ClockDomain(
  clock  = io.pix_clk,
  reset  = io.pix_rst,
  config = ClockDomainConfig(resetKind = ASYNC, resetActiveLevel = HIGH)
)
```

#### 28.3 `ClockingArea` — Scoping Logic to a Non-Default Domain

**MUST** wrap all logic running on a non-default clock in a `ClockingArea`:

```scala
val pixLogic = new ClockingArea(pixDomain) {
  val lineCounter = RegInit(U(0, 12 bits))
  when(lineStart) { lineCounter := lineCounter + 1 }
}
```

Logic outside a `ClockingArea` silently inherits the default domain — there is no
elaboration warning if you forget.

#### 28.4 `defaultClockDomainFrequency` in `SpinalConfig`

**MUST** set this to match the actual target clock. The template sets it to `100 MHz`.
This value is used for timing annotation in the generated Verilog and for any
`ClockDomain` that needs frequency information.

#### 28.5 Naming Clock and Reset Ports

**MUST** rename clock and reset ports to match the board pinout. By default SpinalHDL
generates `clk` and `reset`. Override in `GenVerilog`:

```scala
SpinalConfig(...).generateVerilog {
  val top = new MyTop(Params())
  top.clockDomain.clock.setName("sys_clk")
  top.clockDomain.reset.setName("sys_rst")
  top
}
```

#### 28.6 Multi-Domain Designs — Document the Clock Plan

**MUST** have a written clock plan (even a paragraph) for any design with more than one
clock domain. The plan states: clock names, frequencies, phase relationships, reset
sequencing, and which plugin owns each domain.

---

### 29. Reset Strategy

#### 29.1 Asynchronous Reset (`ASYNC`) — FPGA Default

FPGA flip-flops have dedicated asynchronous preset/clear inputs. `ASYNC` reset maps to
these pins directly and is the natural choice for FPGA designs. The template uses `ASYNC`.

**ASIC:** Asynchronous reset on ASIC requires a carefully timed reset tree with specific
setup/hold on the de-assertion edge. Many ASIC flows prefer synchronous reset. Confirm
with the physical design team before choosing `ASYNC` on an ASIC target.

#### 29.2 Synchronous Reset (`SYNC`)

`SYNC` reset inserts a mux before the flip-flop D input. It guarantees that the reset
effect is synchronous to the clock — no metastability on de-assertion. Valid on FPGA
when reset is held long enough (≥ 2 clock cycles after the last de-assertion glitch).

#### 29.3 `BOOT` Reset Kind

`BOOT` is a simulation-only concept: registers with no `init` value start at a random
state to expose uninitialised-state bugs. Do not use `BOOT` in production synthesis
configuration. The template does not use it.

#### 29.4 Active-High vs Active-Low

Match the board's reset pin polarity. Active-high is the template default (`resetActiveLevel = HIGH`).
If the board has an active-low reset button or reset controller, use `LOW` and name the
port with an `n` suffix (e.g., `sys_rstn`).

#### 29.5 Reset Synchronizer Pattern

When an external asynchronous reset signal (e.g., from a power-on reset chip) feeds a
synchronous domain, it **MUST** pass through a reset synchronizer — two flip-flops
clocked by the destination domain — before being used as the domain's reset. SpinalHDL
does not insert this automatically; instantiate it explicitly as a `BlackBox` or a small
Component.

```
  ext_rst_n ──► [FF1] ──► [FF2] ──► domain.reset
                 ↑          ↑
                clk        clk
```

#### 29.6 Registers That Must Not Reset

Block RAM content (`Mem`) is not reset by the flip-flop reset network. If a memory must
contain known data after reset, use a memory initialization file (`.hex`) or a software
initialization routine. Document this explicitly.

**ASIC:** Standard cell RAMs also do not reset; the same rule applies.

---

### 30. Clock Domain Crossing (CDC)

#### 30.1 The Cardinal Rule

**MUST NOT** make raw signal assignments across clock domain boundaries. Assigning a signal
from domain A to a register in domain B creates a metastable path that will eventually
cause a system failure.

```scala
// WRONG — raw cross-domain assignment
new ClockingArea(domainB) {
  val synced = RegNext(domainASignal)   // metastable
}
```

#### 30.2 `BufferCC(signal)` — The Two-Flop Synchronizer

Use `BufferCC` for single-bit control signals crossing from one domain to another:

```scala
val syncedEnable = BufferCC(domainAEnable)   // safe single-bit crossing
```

**MUST NOT** use `BufferCC` for multi-bit data values. If `n` bits are synchronized
separately, there is no guarantee they are captured from the same source cycle.
Exception: multi-bit constants or configuration values that are written once and then
held stable may use `BufferCC` on each bit, but this requires a handshaking protocol
ensuring stability before the destination domain reads them.

#### 30.3 `StreamFifoCC` — Asynchronous FIFO for Multi-Bit Data

**MUST** use `StreamFifoCC` for multi-bit data crossing between clock domains:

```scala
val asyncFifo = StreamFifoCC(
  dataType  = Bits(32 bits),
  depth     = 16,
  pushClock = domainA,
  popClock  = domainB
)
asyncFifo.io.push << producerStream
asyncFifo.io.pop  >> consumerStream
```

`StreamFifoCC` uses Gray-coded counters internally to safely pass the fill level across
the domain boundary.

#### 30.4 CDC Documentation Requirements

**MUST** annotate every CDC path with an inline comment naming the push and pop domains:

```scala
// CDC: push=pixDomain, pop=sysDomain
val asyncFifo = StreamFifoCC(...)
```

#### 30.5 Timing Constraints for CDC Paths

**MUST** write a false-path or max-delay timing exception for every CDC synchronizer path.
The synchronizer makes the path functionally safe, but the timing tool will still flag it
as a violation without the exception. The constraint suppresses the false alarm.

---

### 31. State Machines (FSM)

#### 31.1 `StateMachine` DSL vs `when`/`switch`

Use `StateMachine` for: multi-state sequencing, states that need to be queried externally
(`fsm.isActive`), or FSMs where `onEntry`/`onExit` side effects are useful.

Use `when`/`switch` for: two-state flags, one-liner conditions, derived control signals.

#### 31.2 FSM Anatomy

```scala
val fsm = new StateMachine {
  val idle    = new State with EntryPoint
  val running = new State
  val done    = new State

  idle.whenIsActive {
    when(start) { goto(running) }
  }

  running.whenIsActive {
    counter := counter + 1
    when(counter === maxVal) { goto(done) }
  }

  done.onEntry { resultReg := finalValue }
  done.whenIsActive { goto(idle) }
}
```

#### 31.3 Moore vs Mealy

**SHOULD** prefer Moore outputs (depend only on current state) for registered outputs —
they are easier to time and reason about. If Mealy paths (output depends on state AND
input) are needed, document each one explicitly.

#### 31.4 FSM Completeness

**MUST** ensure every state has a defined exit condition or is explicitly documented as
a terminal state. States with no exit and no terminal intent are deadlocks.

**MUST** ensure the `EntryPoint` state is safe immediately after reset — no side effects
that assume the design has been running.

#### 31.5 FSM Register Naming

**MUST** apply `periphName` prefix to the FSM state register:

```scala
val fsm = new StateMachine { ... }
fsm.stateReg.setName(s"${periphName}_fsmState")
```

---

### 32. Memory and Storage

#### 32.1 `Mem(T, size)` — SpinalHDL's Memory Primitive

```scala
val lineMem = Mem(UInt(8 bits), 1920)
lineMem.setName(s"${periphName}_lineMem")
```

**MUST** apply `periphName` prefix and `setName()` to every `Mem` instance.

#### 32.2 `readSync` — Registered Read Port (Block RAM)

```scala
val rdData = lineMem.readSync(address, enable = readEnable)
// 1-cycle latency: data is valid on the cycle AFTER readEnable is asserted
```

**SHOULD** prefer `readSync` — it maps to block RAM primitives on all major FPGAs. It
is more area-efficient and has lower routing pressure than distributed RAM.

**ASIC:** `readSync` maps to a synchronous SRAM macro — confirm the cell library has one
at the required size before choosing this approach.

#### 32.3 `readAsync` — Combinational Read (Distributed RAM)

```scala
val rdData = lineMem.readAsync(address)
// No latency: data is valid combinationally with address
```

Use only when pipeline latency cannot be tolerated. `readAsync` maps to LUT-based
distributed RAM — larger, higher routing pressure, harder to time.

#### 32.4 Synthesis Attributes for RAM Style Control

```scala
lineMem.addAttribute("syn_ramstyle", "block_ram")   // force BRAM inference
lineMem.addAttribute("syn_ramdecomp", "area")        // minimise block count
```

Verify inference from the synthesis utilization report after every generation. A zero BRAM
count when BRAM was expected means the tool fell back to LUT RAM.

#### 32.5 Read-During-Write Behavior

Document the read-during-write behavior of every `Mem` port. SpinalHDL's default is
tool-defined. If the design depends on a specific behavior (read-before-write or
write-then-read), specify it explicitly in the `readSync`/`readWriteSync` call.

---

### 33. Stream and Flow Protocols

#### 33.1 `Flow[T]` — Push Without Back-Pressure

Use `Flow[T]` when the producer always delivers and the consumer always accepts:
pixel data from a sensor, status pulses, debug outputs.

#### 33.2 `Stream[T]` — Valid/Ready Handshake

Use `Stream[T]` when back-pressure is needed. A transaction occurs when both
`valid` and `ready` are asserted simultaneously (`stream.fire`).

#### 33.3 Back-Pressure Discipline

**MUST NOT** silently drop data when a `Stream` is not consumed. Either hold the
`valid` until `ready` is asserted, or explicitly sink the stream with `stream.ready := True`
when no consumer is present:

```scala
Try(host[ConsumerPlugin]).toOption match {
  case Some(c) => // consumer drives ready
  case None    => producerStream.ready := True   // sink to prevent stall
}
```

#### 33.4 Monitoring Taps — Observe Without Touching `ready`

When a plugin observes a stream but does not consume it:

```scala
val fired   = pixelStream.fire       // observe transaction
val payload = pixelStream.payload    // observe data
// Do NOT touch pixelStream.ready — the real consumer drives it
```

#### 33.5 `m2sPipe` / `s2mPipe` — Pipeline Stages for Timing Relief

Insert pipeline stages on `Stream` paths to relieve timing pressure:

```scala
val pipelined = stream.m2sPipe()   // adds a master-to-slave register stage
```

---

### 34. Pipeline Architecture

#### 34.1 When to Pipeline

Combinational paths longer than one clock period must be broken with registers.
Pipelining trades latency for throughput and timing margin. Know the target clock
frequency before writing RTL; design register boundaries accordingly.

#### 34.2 Per-Stage Valid Bit Propagation

Every pipeline stage **MUST** carry a `valid` signal alongside its data:

```scala
val stage1Valid = RegNext(inputValid)  init False
val stage1Data  = RegNextWhen(inputData, inputValid)
```

`RegNextWhen` holds the data stable when not valid, preventing unnecessary switching.

#### 34.3 Pipeline Stalls

When a downstream stage is not ready, stall must propagate upstream:

```scala
val stall = !downstream.ready
val stage1Data = RegNextWhen(inputData, !stall)
```

**ASIC:** Stall-based pipelines with clock enable synthesis. Confirm the tool correctly
infers clock enables; if not, use explicit `when(!stall)` guards.

#### 34.4 Latency Accounting

**MUST** document the latency (in clock cycles) of every Core and pipeline stage.
Integration tests must account for pipeline depth in `waitSampling()` counts.
`ComparatorCore` has 1 cycle latency (one `RegInit`); pipeline tests must wait at least
1 cycle after input changes before asserting on outputs.

---

### 35. Arithmetic and Fixed-Point Conventions

#### 35.1 `UInt` vs `SInt`

Choose one and stay consistent within a datapath. SpinalHDL will not silently coerce
between signed and unsigned; explicit casts are required at the boundary.

#### 35.2 Width Growth Through Operations

SpinalHDL tracks result widths automatically:

- Addition of two N-bit values: (N+1)-bit result
- Multiplication of M-bit × N-bit: (M+N)-bit result

Truncating the result is explicit and intentional; document the bits dropped and why.

#### 35.3 Overflow Handling — Choose One per Datapath

- **Wrap:** natural truncation; correct for modular arithmetic (counters)
- **Saturate:** clamp at max/min; requires explicit logic; correct for signal processing
- **Flag:** carry or overflow bit promoted to sideband; correct for error detection

Document the choice per datapath. Silently mixing strategies across a pipeline is a bug.

#### 35.4 Fixed-Point Binary Point

SpinalHDL has no native fixed-point type. **MUST** document the binary point position
for every fixed-point signal with an inline comment:

```scala
val filtered = SInt(20 bits)   // Q7.13 — integer.fraction
```

Wherever the binary point shifts (truncation, scaling, DSP multiply), update the comment.

---

### 36. DSP and Hard-Block Inference

#### 36.1 What Constitutes a Hard Multiply

FPGA DSP blocks (DSP48 on Xilinx, DSP blocks on Intel, dedicated multipliers on Efinix)
are limited and high-performance. Without proper RTL style, multipliers map to LUT logic.

**ASIC:** Standard cell multipliers are inferred by synthesis; the RTL style still matters
for area and timing.

#### 36.2 Coding for DSP Inference

Keep the multiply and accumulate in the same `Area` or `when` block. Register inputs
before the multiply and register the output after it:

```scala
val aReg    = RegNext(a)
val bReg    = RegNext(b)
val prodReg = RegNext(aReg * bReg)      // registered multiply — maps inside DSP
val accReg  = RegInit(S(0, 32 bits))
accReg      := accReg + prodReg.asSInt  // accumulate
```

Inserting unrelated logic between the multiply and accumulate register breaks the
MAC pattern and prevents DSP inference.

#### 36.3 Verifying DSP Inference

Check the synthesis utilization report for DSP block count. A zero DSP count when
multipliers are present means the tool fell back to LUT logic. Use `addAttribute` to force:

```scala
productSignal.addAttribute("use_dsp", "yes")   // Xilinx
```

---

### 37. Power and Clock Enable Discipline

#### 37.1 Use Register CE, Not Gated Clocks

**MUST NOT** create gated clocks in RTL. Every FPGA flip-flop has a dedicated clock enable
pin (CE). `when(enable) { reg := next }` maps to CE, not to a gated clock.

Gated clocks created from combinational logic cause glitches and timing closure problems
and are not routable through global clock networks on any major FPGA family.

**ASIC:** Clock gating with integrated clock gate cells (ICG) is the correct ASIC
mechanism. The synthesizer inserts ICG cells from `enable` conditions in the RTL — but
only when the flow is configured to do so. Confirm with the synthesis team.

#### 37.2 Operand Isolation for Multipliers

When a multiply result is not consumed (pipeline stalled), hold the inputs constant to
prevent unnecessary switching — multiplier inputs toggling waste power even when the
output is not used:

```scala
val aReg = RegNextWhen(a, !stall)   // holds when stalled → no switching
val bReg = RegNextWhen(b, !stall)
```

**ASIC:** This is more critical than on FPGA. Work with the power analysis team to
identify hot paths in the power report.

#### 37.3 Global Clock Resource Consumption

FPGA global clock buffers (BUFG on Xilinx, BUFGCE on Xilinx, GBx on Efinix) are a
limited resource. Count them. Do not route a derived clock from combinational logic
through a global buffer — use a PLL output (see §48).


---

## Part VI — Simulation and Testing

### 38. Test Architecture and Philosophy

#### 38.1 The Three Test Types

| Type | File | Scope | Purpose |
|------|------|-------|---------|
| Elaboration | `ElaborationTest.scala` | Full design | Fiber graph resolves; Verilog generates |
| Unit | `XxxCoreTest.scala` | Single Core | Functional behavior via Verilator simulation |
| Integration | `XxxChainTest.scala` | Multi-core pipeline | End-to-end data flow across Cores |

#### 38.2 Why Cores Are Tested Directly

Cores contain all RTL logic. Plugins are thin wiring wrappers with no logic of their own.
Testing the Core directly via a Harness gives full stimulus/observe access to all signals
without the overhead of the full plugin graph. Plugin-level wiring is covered by the
elaboration test and integration tests.

#### 38.3 Sequential Test Execution — Non-Negotiable

`Test / parallelExecution := false` in `build.sbt` is required. Verilator compiles to
a native `.so` per test class. Parallel runs write to the same compilation directories
and corrupt each other's binaries, producing random test failures that are extremely
difficult to diagnose.

---

### 39. The Harness Wrapper Pattern

#### 39.1 Why Harnesses Are Necessary

`object XxxCore` is not a Component — it has no Verilog module boundary for Verilator to
target. A Harness wraps the Core in a minimal Component so that `SimConfig.compile()`
has something to compile.

#### 39.2 Harness Structure

```scala
class TimerHarness(width: Int = 8) extends Component {

  val io = new Bundle {
    val enable = in  Bool()
    val count  = out UInt(width bits)
  }

  val core = TimerCore.build(
    periphName = "timer",
    width      = width,
    enable     = io.enable
  )

  io.count := core.count
}
```

#### 39.3 Harness Design Principles

**MUST** expose every input and output of the Core — no hidden state.
**MUST NOT** add logic to the Harness beyond what is needed to wire the Core.
**SHOULD** use `Bool`, `UInt`, `SInt` at the Harness boundary, not `Stream`/`Flow`
unless the Core is explicitly a protocol Core.

#### 39.4 Chain Harness — Multi-Core Integration

For integration tests, wire multiple Cores directly in one Harness:

```scala
class TimerComparatorChainHarness(width: Int, threshold: Int) extends Component {
  val io = new Bundle {
    val enable    = in  Bool()
    val aboveFlag = out Bool()
  }

  val timer = TimerCore.build("timer",      width,     io.enable)
  val comp  = ComparatorCore.build("comparator", threshold, timer.count)

  io.aboveFlag := comp.above
}
```

#### 39.5 Harness Placement

**MUST** place all Harness classes in `src/test/scala/<pkg>/testhelpers/`. They are test
infrastructure, not production code.

---

### 40. Unit Testing — Core Tests

#### 40.1 One Test Class Per Core

```scala
class TimerCoreTest extends AnyFunSuite { ... }
class ComparatorCoreTest extends AnyFunSuite { ... }
```

#### 40.2 The `compile()` Pattern

Define a shared `def compile()` at the top of each test class:

```scala
class TimerCoreTest extends AnyFunSuite {
  val width = 8

  def compile() = SimConfig
    .withWave
    .workspacePath("simWorkspace/TimerCoreTest")   // unique per test class
    .compile(new TimerHarness(width = width))
```

- `withWave` — **MUST** always dump VCD for post-mortem debugging.
- `workspacePath` — **MUST** be unique per test class. Verilator stores compiled
  `.so` files here; two classes sharing a path will overwrite each other.
- `compile()` is a `def` (not `val`) because each call to `doSim` gets a fresh
  simulation handle.

#### 40.3 Test Case Structure

```scala
test("Counter increments when enabled") {
  compile().doSim("increment") { dut =>
    dut.clockDomain.forkStimulus(period = 10)
    dut.io.enable #= false
    dut.clockDomain.waitSampling(5)
    sleep(1)

    dut.io.enable #= true
    for (expected <- 1 to 10) {
      dut.clockDomain.waitSampling()
      sleep(1)
      assert(dut.io.count.toInt == expected,
        s"Cycle $expected: expected $expected, got ${dut.io.count.toInt}")
    }
  }
}
```

- `forkStimulus(period = 10)` — **MUST** be called explicitly in every simulation.
  Do not rely on a default clock.
- `doSim("name")` — the name becomes the directory under `workspacePath` that holds
  the VCD. Use descriptive names (`"increment"`, `"wrap"`, `"reset"`).

#### 40.4 Simulation Timing: `waitSampling()` + `sleep(1)` vs `waitSampling(n)`

Two patterns appear in the template:

**Pattern A — Precise (TimerCoreTest):**
```scala
dut.clockDomain.waitSampling()
sleep(1)
assert(dut.io.count.toInt == expected, ...)
```
`waitSampling()` lands at the rising edge. `sleep(1)` waits one delta cycle for
combinational evaluation to settle. Register outputs are then stable for reading.

**Pattern B — Simpler (ComparatorCoreTest):**
```scala
dut.io.countIn #= v
dut.clockDomain.waitSampling(2)
assert(dut.io.above.toBoolean, ...)
```
Wait two edges: the first captures the input into the register, the second ensures
the output is stable. Wastes one cycle but avoids the explicit `sleep(1)` and is
safe for any number of registered pipeline stages.

**SHOULD** use Pattern A for tight latency-sensitive tests. **MAY** use Pattern B for
simpler tests where cycle-exact timing is not tested. Never mix patterns within a single
test case.

#### 40.5 Mandatory Test Cases for Every Core

**MUST** cover:

- [ ] Happy path / normal operation
- [ ] Boundary values (zero, maximum, wrap-around or saturation)
- [ ] Reset behavior (register starts at `init` value)
- [ ] Enable/disable / hold behavior (when applicable)

**SHOULD** cover:

- [ ] Overflow and wrap-around (for counters and arithmetic cores)
- [ ] Back-pressure / stall behavior (for Stream-protocol cores)
- [ ] State machine transition coverage (for FSM cores)

#### 40.6 `assert()` Message Conventions

**MUST** include in every assert message: the test context, the expected value, the actual
value, and the cycle number where available:

```scala
assert(actual == expected,
  s"Cycle $cycle: expected $expected, got $actual")
```

An assert failure with only `"assertion failed"` requires waveform inspection to diagnose.
An assert failure with a clear message identifies the bug in the CI log without opening
a waveform viewer.

---

### 41. Integration Testing — Chain and Plugin Tests

#### 41.1 When Integration Tests Are Needed

- Multi-core pipelines with inter-core dependencies (e.g., counter feeds threshold).
- Timing relationships visible only when cores are connected (pipeline latency).
- Protocol handshaking that spans core boundaries.

#### 41.2 Chain Test Pattern

```scala
class TimerComparatorChainTest extends AnyFunSuite {

  def compile() = SimConfig
    .withWave
    .workspacePath("simWorkspace/TimerComparatorChainTest")
    .compile(new TimerComparatorChainHarness(width = 8, threshold = 128))

  test("above flag asserts after counter reaches threshold") {
    compile().doSim("chain_above") { dut =>
      dut.clockDomain.forkStimulus(period = 10)
      dut.io.enable #= true
      // 128 counter cycles + 1 register cycle in ComparatorCore
      dut.clockDomain.waitSampling(129)
      sleep(1)
      assert(dut.io.aboveFlag.toBoolean,
        s"Expected aboveFlag after 128 cycles")
    }
  }
}
```

#### 41.3 Accounting for Pipeline Depth

**MUST** add the pipeline depth of each Core to the `waitSampling()` count in integration
tests. `ComparatorCore` has 1 cycle latency; `TimerCore` has 0 (the counter value is
driven directly from `countReg`). For a chain of N stages with latencies L1..LN, wait
`input_cycles + L1 + ... + LN` before asserting the chain output.

---

### 42. Elaboration Tests

#### 42.1 Purpose

Elaboration tests validate the Fiber dependency graph and Verilog generation. They do not
test functional behavior — that is the job of unit and integration tests.

#### 42.2 Minimum Coverage

**MUST** cover:

- [ ] Default `Params` — the most common configuration.
- [ ] At least one non-default configuration — exercises parameter threading.

**SHOULD** cover:

- [ ] Boundary `Params` (minimum width, maximum threshold, edge-case combinations).

#### 42.3 Structure

```scala
class ElaborationTest extends AnyFunSuite {

  test("MyTop elaborates with default Params") {
    SpinalConfig(
      targetDirectory = "rtl",
      defaultClockDomainFrequency = FixedFrequency(100 MHz),
      defaultConfigForClockDomains = ClockDomainConfig(
        resetKind        = ASYNC,
        resetActiveLevel = HIGH
      )
    ).generateVerilog(new MyTop(Params()))
  }

  test("MyTop elaborates with custom Params") {
    SpinalConfig(targetDirectory = "rtl")
      .generateVerilog(new MyTop(Params(timerWidth = 16, threshold = 1000)))
  }
}
```

#### 42.4 What Elaboration Tests Catch

- Circular Handle dependencies (Fiber deadlock)
- Missing `load()` calls
- `require()` failures from invalid `Params`
- SpinalHDL type-checking errors (width mismatch, direction errors)
- Undriven signals and ports

#### 42.5 What Elaboration Tests Do Not Catch

Functional correctness, timing behavior, and protocol compliance. These require simulation.

---

### 43. Simulation Conventions

#### 43.1 `SimConfig` Standard Setup

Every simulation **MUST** use:

```scala
SimConfig
  .withWave                               // always dump VCD
  .workspacePath("simWorkspace/XxxTest")  // unique per test class
  .compile(new XxxHarness(...))
```

#### 43.2 `forkStimulus(period = 10)` — Always Explicit

**MUST** call `dut.clockDomain.forkStimulus(period = 10)` at the start of every
simulation body. The `period` sets the simulation clock period in time units. The value
`10` is the template convention (5 time units per half-period).

#### 43.3 `#=` — The Simulation Write Operator

`#=` assigns a value to a simulation signal (driving from the testbench):

```scala
dut.io.enable #= true
dut.io.countIn #= 127
```

Do not use Scala `=` on hardware signals in simulation — it rebinds the Scala variable,
not the hardware signal.

#### 43.4 `fork { } / join` — Parallel Stimulus and Monitor

For tests that require separate stimulus and monitoring threads:

```scala
val monitor = fork {
  while(true) {
    dut.clockDomain.waitSampling()
    if (dut.io.valid.toBoolean) {
      results += dut.io.data.toInt
    }
  }
}

// stimulus in main thread
dut.io.enable #= true
dut.clockDomain.waitSampling(100)

monitor.terminate()
```

**MUST** ensure all forked threads terminate before the simulation ends. An unterminated
fork thread causes a simulation hang.

#### 43.5 VCD Naming

Name simulation runs to match the scenario being tested. The run name becomes a directory
under `workspacePath`:

```
simWorkspace/TimerCoreTest/TimerHarness/increment/wave.vcd
simWorkspace/TimerCoreTest/TimerHarness/wrap/wave.vcd
```

Use snake_case, descriptive names. A VCD named `test1` gives no information when opening
it six months later.

---

### 44. Assertions and Verification

#### 44.1 `require()` — Elaboration-Time Preconditions

`require()` tests a Scala `Boolean`. It fires during elaboration. Use it to validate
`Params` fields and Core `build()` arguments.

#### 44.2 `assert()` in Simulation

Scala `assert()` inside a `doSim` block runs in the simulation thread. It fires when the
condition is false and terminates the simulation with a stack trace. Always include a
meaningful message (§40.6).

#### 44.3 `AssertStatement` — Synthesizable Hardware Assertions

SpinalHDL can embed assertions in the generated Verilog/SystemVerilog for use with
formal tools:

```scala
assert(
  assertion = countReg < threshold,
  message   = "count must not exceed threshold",
  severity  = ERROR
)
```

Severity levels: `NOTE`, `WARNING`, `ERROR`, `FAILURE`. `ERROR` and `FAILURE` generate
synthesizable `$error`/`$fatal` statements in SystemVerilog output.

#### 44.4 `report.printPruned()` — Dead Logic Detection

**MUST** call `report.printPruned()` after every `generateVerilog` call. Pruned signals
are signals SpinalHDL determined have no effect on any output. A pruned register almost
always indicates a missing connection upstream — not an optimisation.

```scala
val report = SpinalConfig(...).generateVerilog(new MyTop(Params()))
report.printPruned()
```

#### 44.5 SpinalHDL Lint Warnings

Treat all SpinalHDL warnings as errors. Common warnings and their meanings:

| Warning | Cause | Fix |
|---------|-------|-----|
| Undriven signal | Signal declared but never assigned | Find missing assignment |
| Unconnected port | Port declared but not wired | Wire it or remove it |
| Width truncation | Implicit width narrowing | Add explicit `.resize()` |
| Latch inferred | `when` without `otherwise` or default | Add default assignment |

#### 44.6 Formal Verification — SymbiYosys

SpinalHDL can emit SystemVerilog Assertions (SVA) compatible with SymbiYosys. Formal
verification complements simulation — it proves properties for all possible inputs, not
just the inputs the testbench exercises. Start with bounded model checking (`bmc`) on
safety properties (no overflow, no invalid state, no deadlock).

This is particularly valuable for VexRiscv peripheral integration: formal can prove that
a bus transaction always completes, that a FIFO never overflows, or that an interrupt
is always acknowledged within N cycles.


---

## Part VII — Synthesis and Generation

### 45. Verilog Generation (`SpinalConfig`)

#### 45.1 The `GenVerilog` Object

The template's entry point is:

```scala
object GenVerilog extends App {
  val report = SpinalConfig(
    targetDirectory              = "rtl",
    defaultClockDomainFrequency  = FixedFrequency(100 MHz),
    defaultConfigForClockDomains = ClockDomainConfig(
      resetKind        = ASYNC,
      resetActiveLevel = HIGH
    )
  ).generateVerilog {
    val top = new MyTop(Params())
    top
  }
  report.printPruned()
}
```

Run: `sbt "runMain mydesign.GenVerilog"` → output lands in `rtl/MyTop.v`.

#### 45.2 `targetDirectory`

**MUST** set `targetDirectory = "rtl"`. This ensures generated Verilog lands in a known
location. Do not use the default (current directory) — it pollutes the project root.

#### 45.3 `defaultClockDomainFrequency`

**MUST** set this to match the actual target clock. The value is embedded in the generated
Verilog as a comment and is used for timing annotation. A mismatch between this value and
the constraints file `create_clock` does not cause a synthesis error but will confuse
anyone reading the Verilog.

#### 45.4 `defaultConfigForClockDomains`

**MUST** set `resetKind` and `resetActiveLevel` to match the board's reset topology.
The template defaults to `ASYNC` / `HIGH`. If the board has an active-low reset or the
ASIC flow requires synchronous reset, change this here — not in individual plugins.

#### 45.5 `generateVerilog` vs `generateSystemVerilog`

**SHOULD** use `generateVerilog` for maximum tool compatibility. `generateSystemVerilog`
produces cleaner output (native `always_ff`, `always_comb`, enum types) and is correct
for Vivado 2019+, Quartus Prime Pro, and most modern synthesis tools. However, some older
tools and Efinix's Efinity tool have limited SystemVerilog support. Verify with your tool
before switching.

#### 45.6 Clock and Reset Port Renaming in `GenVerilog`

Rename clock and reset ports to match the board pinout inside the `generateVerilog` block:

```scala
SpinalConfig(...).generateVerilog {
  val top = new MyTop(Params())
  top.clockDomain.clock.setName("sys_clk")
  top.clockDomain.reset.setName("sys_rst")
  top
}
```

**MUST** do this for every clock domain in the design. Without it, the generated port
names are `clk` and `reset` — generic names that will not match any real pinout.

#### 45.7 `noRandBoot` — Simulation Initialization Behavior

`SpinalConfig().noRandBoot` suppresses SpinalHDL's random boot-state initialization in
simulation. With `noRandBoot`, registers without `init` values start at zero in simulation
rather than at a random value.

**This affects simulation only — not synthesis.** The synthesis tool uses register reset
pins; it does not see `noRandBoot`. Do not use `noRandBoot` as a substitute for
providing `init` values — it masks uninitialized-state bugs.

#### 45.8 Generating Multiple Configurations

When the design has multiple `Params` variants for different targets, generate each to a
named directory:

```scala
object GenVerilog extends App {
  for ((name, params) <- Seq(
    "fpga_100mhz"  -> Params(sysClkHz = 100 MHz, timerWidth = 8),
    "fpga_200mhz"  -> Params(sysClkHz = 200 MHz, timerWidth = 16)
  )) {
    SpinalConfig(targetDirectory = s"rtl/$name").generateVerilog(new MyTop(params))
      .printPruned()
  }
}
```

---

### 46. BlackBox and Vendor IP

#### 46.1 When to Use `BlackBox`

Use `BlackBox` for: vendor primitives (PLLs, SERDES, IO buffers, DSP blocks), third-party
IP delivered as a netlist or encrypted Verilog, and any block whose internals SpinalHDL
must not see.

**MUST NOT** use `BlackBox` to encapsulate SpinalHDL logic — use a Plugin IP Component (§24).

#### 46.2 `BlackBox` Port Declaration

```scala
class SysPll extends BlackBox {
  val io = new Bundle {
    val refClk  = in  Bool()
    val rstn    = in  Bool()
    val outClk  = out Bool()
    val locked  = out Bool()
  }

  mapCurrentClockDomain(clock = io.outClk, reset = io.rstn)
  addGeneric("REFCLK_FREQ", "100.0")   // string generic for vendor tools
}
```

#### 46.3 `mapClockDomain` / `mapCurrentClockDomain`

**MUST** use these to connect a BlackBox's clock and reset outputs to `ClockDomain`
instances. Without this mapping, SpinalHDL cannot determine which domain the BlackBox's
outputs drive.

#### 46.4 Simulation Models for BlackBoxes

`BlackBox` instances do not simulate in Verilator by default — Verilator needs a Verilog
model. For PLLs and similar infrastructure blocks, provide a stub:

```scala
class SysPll extends BlackBox {
  // ...
  // Stub for simulation: immediately assert locked
  addPrePopulation {
    // or use BlackBoxWithSimInlines to embed Verilog simulation behavior
  }
}
```

For simulation, PLL stubs should immediately assert `locked` and pass through the reference
clock. This is safe because `forkStimulus` drives the simulation clock, not the PLL model.

---

### 47. Synthesis Attributes and Pragmas

#### 47.1 The `addAttribute` API

```scala
signal.addAttribute("key", "value")   // key-value attribute
signal.addAttribute("key")            // presence-only attribute
```

Attributes are emitted as `(* key = "value" *)` in Verilog or as `attribute` statements
in VHDL.

#### 47.2 Common Attributes

| Attribute | Value | Effect |
|-----------|-------|--------|
| `syn_ramstyle` | `"block_ram"`, `"distributed"` | Force BRAM or LUT RAM inference |
| `syn_ramdecomp` | `"area"`, `"speed"` | Optimise block count vs. throughput |
| `keep` | — | Prevent optimizer from removing this signal |
| `dont_touch` | — | Prevent optimizer from touching this hierarchy |
| `use_dsp` | `"yes"`, `"no"` | Force or prevent DSP block inference (Xilinx) |
| `mark_debug` | — | Mark signal for ILA capture in Vivado |

#### 47.3 Timing Exception Documentation

False paths and multi-cycle paths belong in the constraints file, not in RTL. However,
the RTL **MUST** carry a comment at the source signal referencing the constraint:

```scala
val asyncStatus = BufferCC(domainAStatus)
// CDC: false path declared in constraints.xdc — see set_false_path -from [get_cells domainA/*]
```

**SHOULD** reference the constraint by name so the comment stays aligned with the
constraints file as it evolves.

---

### 48. PLL and Clock Generation

#### 48.1 When a Raw Pad Clock Is Sufficient

A pad clock routed through a global clock buffer is correct for: single clock domain,
frequency equals the pad clock frequency, no phase requirements, no tight jitter budget.
**Do not add a PLL by default.** Add one when the design requires it.

#### 48.2 When a PLL Is Needed

- Frequency multiplication or division from the pad clock.
- Multiple output clocks with defined phase relationships.
- Tight jitter budget the raw clock path cannot meet.

**ASIC:** A PLL is typically required above a few hundred MHz. Coordinate with the
analog/mixed-signal team before choosing oscillator frequency and PLL topology.

#### 48.3 Instantiating a PLL as `BlackBox`

PLL primitives are vendor-specific. Wrap them as `BlackBox` and connect outputs to
`ClockDomain` instances:

```scala
val pll = new SysPll()
pll.io.refClk := io.refClk
pll.io.rstn   := io.rstn

val sysDomain = ClockDomain(
  clock  = pll.io.outClk,
  reset  = !pll.io.locked,   // domain stays in reset until PLL locks
  config = ClockDomainConfig(resetKind = ASYNC, resetActiveLevel = HIGH)
)
```

#### 48.4 PLL Lock Signal — Tying Reset to Lock

**MUST** keep all downstream domains in reset until the PLL lock signal asserts. Drive
the domain reset from `!pll.io.locked` (after passing through a reset synchronizer —
see §29.5) for each downstream clock domain.

#### 48.5 Simulation — PLL Stubs

PLL primitives do not simulate in Verilator. Provide a stub that immediately asserts
`locked` and passes the reference clock through. In simulation, `forkStimulus` drives
the clock — the PLL model is irrelevant.

---

### 49. IO Standards and Pad Constraints

#### 49.1 IO Standards Live in the Constraints File

SpinalHDL controls port names and signal types — not electrical standards.
LVCMOS, LVDS, SSTL, and similar standards are declared in XDC/SDC/QSF. The RTL's only
responsibility is providing the correct port name (via `setName()`) for the constraint
to target.

#### 49.2 Differential Pairs — Use BlackBox Primitives

LVDS and TMDS input/output buffers are vendor primitives. Wrap them as `BlackBox` and
connect SpinalHDL logic to the single-ended side:

```scala
class IBUFDS extends BlackBox {
  val io = new Bundle {
    val I  = in  Bool()   // positive
    val IB = in  Bool()   // negative
    val O  = out Bool()   // single-ended output
  }
}
```

#### 49.3 `TriState[T]` for Bidirectional Pads

```scala
val sdaPin = master(TriState(Bool()))
// sdaPin.write — drives the pad when writeEnable is true
// sdaPin.read  — always reflects the pad value
// sdaPin.writeEnable — controls the output driver
```

Map each TriState component to a vendor pad primitive via `BlackBox`.

#### 49.4 Input/Output Delay Budgets

`set_input_delay` and `set_output_delay` belong in the constraints file. Add an inline
comment at the relevant port in `Top.scala` stating the expected delay budget so the
constraints author has context:

```scala
// Expected board input delay: 2–4 ns; set_input_delay in constraints.xdc
val sensorData = in Bits(8 bits)
```

---

### 50. Linting and Static Analysis on Generated Verilog

#### 50.1 SpinalHDL Checks Come First

Width mismatches, undriven signals, combinational loops, and direction errors are caught
at elaboration time. Fix all SpinalHDL errors and warnings before running any external
tool. External linters on broken Verilog produce confusing secondary errors.

#### 50.2 `report.printPruned()`

**MUST** review pruned output after every `generateVerilog` call. Unexpected pruning
almost always indicates a missing connection — not an optimization.

#### 50.3 Verilator `--lint-only`

Add to the CI pipeline after Verilog generation:

```bash
verilator --lint-only --Wall rtl/MyTop.v
```

This catches Verilog-level issues that SpinalHDL's elaboration does not see: implicit
net declarations, sensitivity list issues, width extension warnings.

#### 50.4 Vendor Synthesis Critical Warnings

Vendor tools (Vivado, Quartus, Efinity) emit critical warnings for inferred latches,
unresolved references, and undriven ports. **MUST** treat all critical warnings as errors
until reviewed and explicitly accepted with a documented justification. A critical warning
that is "understood and harmless" still needs a comment in the synthesis script or log
explaining why it is accepted.


---

## Part VIII — Design for Timing

> Timing decisions happen in RTL, not in the constraints file. A constraint can relax a
> timing check; it cannot make bad RTL meet timing. The decisions in this part happen in
> SpinalHDL; the synthesis and implementation reports are downstream verification.

### 51. Writing RTL with Timing in Mind

#### 51.1 The Setup Timing Model

Data must be stable at a register's D input before the clock edge by at least the setup
time. The number of logic gates between two registers determines the maximum operating
frequency for that path. SpinalHDL's `Reg`/`RegInit`/`RegNext` each create a register
boundary that resets the logic depth counter.

#### 51.2 Controlling Logic Depth

**SHOULD** insert pipeline registers to break combinational paths longer than one clock
period at the target frequency. Deep `when`/`switch` trees and wide mux trees are the
most common sources of long paths.

```scala
// Deep mux — may be a timing problem at high frequency
val result = UInt(8 bits)
switch(sel) {
  is(0) { result := a }
  is(1) { result := b }
  is(2) { result := c }
  // ...16 cases...
}

// Break with a registered intermediate
val muxResult = RegNext(result)   // pipeline stage
val finalOut  = someLogic(muxResult)
```

#### 51.3 `Area` Boundaries Do Not Add Latency

`new Area { }` is a naming construct. It does not insert a register stage or create a
logic boundary that the synthesizer respects. Only explicit `Reg`/`RegNext`/`RegNextWhen`
create register stages.

#### 51.4 Fanout and Routing Delay

A signal driving many downstream registers increases routing delay. High-fanout signals
(resets, global enables, clock enables used across many plugins) may need register
replication. SpinalHDL's `addAttribute("dont_touch")` can prevent the optimizer from
merging replicated registers.

#### 51.5 Target Frequency Awareness

**MUST** know the target clock frequency before writing RTL. Design register boundaries
to meet that frequency; do not retrofit pipelining after timing closure fails.

**FPGA:** Device speed grade and the implementation tool's timing summary report (Vivado
`Timing Summary`, Quartus Fitter) are authoritative. Verify route-level FMax — synthesis
FMax is optimistic. Check critical paths in the timing report before declaring closure.

**ASIC:** Process technology (corners, libraries) determines achievable frequency. Align
with the technology characterization document before setting target frequency.

---

### 52. Multi-Cycle Paths

#### 52.1 What a Multi-Cycle Path Is

A combinational path between two registers in the same clock domain that intentionally
takes more than one clock cycle to stabilize. Common sources: pipelined multipliers with
registered outputs consumed later, slow control paths, divided-clock-rate data.

#### 52.2 RTL Side — Validity Signaling

A multi-cycle path **MUST** have a validity or enable signal that indicates when the
destination register may safely sample. The RTL comment at the source must name the path
and reference the constraint:

```scala
// Multi-cycle path: result valid 3 cycles after load.
// set_multicycle_path 3 -from [get_cells ${periphName}_loadReg*]
//                       -to   [get_cells ${periphName}_resultReg*]
val resultReg = RegNextWhen(compute(a, b), validAfter3Cycles)
```

#### 52.3 Constraint Side

The `set_multicycle_path` constraint relaxes the timing check. **MUST** have both the RTL
validity signaling and the matching constraint. One without the other is incomplete:
validity signaling without the constraint means the tool may still flag it as a violation;
the constraint without the validity signaling means the design may sample incorrect data.

---

### 53. False Paths

#### 53.1 What a False Path Is

A physical path in the netlist that the timing tool checks but can never cause a timing
failure in practice. Common: CDC synchronizer paths (the synchronizer makes the path safe),
static configuration signals written once at startup, power-down domains.

#### 53.2 Documenting False Paths in RTL

**MUST** add an inline comment at the source or destination signal:

```scala
// CDC false path: domainA → domainB via BufferCC synchronizer.
// set_false_path -from [get_clocks clk_a] -to [get_clocks clk_b]
// -through [get_cells ${periphName}_buffercc*]
val synced = BufferCC(domainASignal)
```

The comment states: why the path is false, what mechanism makes it safe, and which
constraint suppresses the check.

#### 53.3 Conservative False Path Declarations

A path declared false that is not actually false will mask a real timing violation.
When in doubt, constrain the path and accept the timing penalty — do not declare a path
false speculatively.

---

### 54. Post-Generation Review

#### 54.1 `report.printPruned()` — First Check

Run after every `generateVerilog`. Any pruned signal is a potential wiring error. Pruned
registers usually mean a missing `load()` call or a missing connection in `TopIoExportPlugin`.

#### 54.2 Synthesis Utilization Report

Review LUT, register, BRAM, and DSP counts against design intent after every synthesis run:

- Unexpected high LUT count: check for accidental LUT-based multipliers (§36).
- Unexpected zero BRAM count: check `readSync` vs `readAsync` (§32.2–32.3).
- Unexpected low register count: check for pruned registers (§54.1).

#### 54.3 Synthesis Timing Report

Identify the worst-case path (most negative slack). Trace it back to RTL:

1. Which Core and which `when` branch contributes the deepest logic?
2. Is the path through a mux tree, a carry chain, or a multiply?
3. Can a pipeline register be inserted at a natural boundary?

Prefer RTL changes (pipeline insertion, logic restructuring) over constraints when the path
is genuinely too long. Timing constraints should document reality, not paper over bad RTL.

**ASIC:** Floorplan and placement are additional levers; coordinate with physical design.
IR drop and electromigration checks are part of ASIC sign-off and are not covered by RTL
changes alone.

---

## Part IX — Design Review Checklists

> Use these checklists at design review. Each item links implicitly to the relevant section
> of this guide. Items marked **ASIC:** apply to ASIC targets and are supplementary for FPGA.

### 55. Pre-Synthesis Checklist

#### Elaboration and Phase Correctness
- [ ] Elaboration test passes for default `Params` and at least one non-default variant
- [ ] All `require()` guards present for mandatory parameters and signal arguments
- [ ] No hardware signals accessed outside a `during build` phase
- [ ] No Scala `var` used for hardware signal references (use `val`)
- [ ] `def plugins` used in `Params` — not `val plugins`

#### Signal Hygiene
- [ ] All registers have an `init` value (`Reg(T) init x` or `RegInit(x)`)
- [ ] All `when` branches either have `otherwise` or all assigned signals have default values above the `when`
- [ ] No combinational loops (SpinalHDL errors on detection — verify clean compile)
- [ ] No undriven output ports or signals
- [ ] `report.printPruned()` reviewed after generation — no unexpected pruning

#### Flat-Plugin Architecture
- [ ] All Handles loaded before they are awaited (no circular load/await)
- [ ] `TopIoExportPlugin` two-phase discipline maintained — all loads precede all awaits
- [ ] `Try(host[X]).toOption` used for all optional plugin references
- [ ] `case None` branches provide safe defaults for every optional port
- [ ] Every IO port has a `setName()` call
- [ ] No magic numbers — all constants flow through `Params`
- [ ] `periphName` prefix and `setName()` on every `Reg()` and `Mem()` in every Core

---

### 56. Clock Domain Checklist

- [ ] Default clock domain configuration reviewed; explicitly overridden if it does not match the target
- [ ] `resetKind` matches target FPGA fabric and board requirements
- [ ] `resetActiveLevel` matches board reset pin polarity
- [ ] `defaultClockDomainFrequency` in `SpinalConfig` matches the actual target clock
- [ ] Clock and reset port names set via `setName()` to match the pinout/constraints file
- [ ] All logic in non-default clock domains wrapped in `ClockingArea`
- [ ] Multi-domain designs have a written clock plan
- [ ] PLL lock signal tied into the reset path for all downstream domains

---

### 57. Reset Checklist

- [ ] All state-holding elements have a reset value (`init` / `RegInit`)
- [ ] External async reset passes through a reset synchronizer before entering any synchronous domain
- [ ] Reset release sequencing defined for multi-domain designs
- [ ] BRAM content treated as undefined after reset (unless an initialization file is provided)
- [ ] No combinational logic driven by a reset signal (reset is a clock-domain concern)
- [ ] **ASIC:** Reset strategy (async vs sync) confirmed with physical design team
- [ ] **ASIC:** Reset tree buffering strategy confirmed with synthesis team

---

### 58. CDC Checklist

- [ ] No raw signal assignments across clock domain boundaries
- [ ] Every CDC path uses an approved primitive: `BufferCC`, `StreamFifoCC`, or `StreamCCByToggle`
- [ ] Multi-bit data crossings use `StreamFifoCC` — not `BufferCC`
- [ ] All CDC paths annotated with inline comments naming push domain and pop domain
- [ ] Timing exceptions (false path or max-delay) written and reviewed for all CDC paths
- [ ] MTBF considered at target operating frequency for synchronizer chain depth

---

### 59. State Machine Checklist

- [ ] All states reachable from the `EntryPoint`
- [ ] No state-machine deadlocks (states with no exit and no terminal intent)
- [ ] Every state has a defined exit condition, or is explicitly documented as terminal
- [ ] FSM resets to a known safe state via the `EntryPoint`
- [ ] FSM state register has `periphName` prefix and `setName()` call
- [ ] `onEntry`/`onExit` side effects are bounded (no accidental feedback)
- [ ] Moore vs Mealy choice documented for each FSM output

---

### 60. Simulation and Test Checklist

- [ ] Unit tests cover: happy path, boundary values, reset behavior, overflow/wrap-around
- [ ] Elaboration test covers default `Params` and at least one non-default variant
- [ ] `withWave` enabled — VCD dumped for every simulation run
- [ ] `parallelExecution := false` in `build.sbt`
- [ ] `workspacePath` unique per test class
- [ ] All `fork`/`join` simulation threads terminate cleanly
- [ ] `assert()` messages include expected value, actual value, and cycle number where applicable
- [ ] Integration tests account for pipeline depth in `waitSampling()` counts
- [ ] Plugin IP Components (§24) have standalone Component-level tests

---

### 61. Code Review Checklist

- [ ] No Scala implicits in signal assignment paths — explicit is always preferable
- [ ] CDC crossings documented inline with domain names
- [ ] New plugins/cores documented in `docs/ARCHITECTURE.md`
- [ ] No unused `Handle` fields left in plugins
- [ ] No dead plugins remaining in `Params.plugins`
- [ ] `report.printPruned()` output reviewed — no unintended pruning of live signals
- [ ] Synthesis attributes (`addAttribute`) have a justifying comment
- [ ] Timing exception comments reference the constraint file and the constraint name
- [ ] Plugin IP Component (§24) port list treated as an API — breaking port changes versioned
- [ ] `setName()` calls present on every IO port; names match the pinout/constraints file

---

### 62. Timing and Implementation Checklist

- [ ] Target clock frequency known before RTL was written; pipeline stages sized accordingly
- [ ] `defaultClockDomainFrequency` in `SpinalConfig` matches the constraints file `create_clock`
- [ ] No combinational paths identified as likely to fail at target frequency (review synthesis timing report)
- [ ] All multi-cycle paths have both RTL validity signaling and a matching constraint
- [ ] All false paths have both an RTL comment explaining the safety mechanism and a matching constraint
- [ ] Synthesis utilization reviewed — BRAM, DSP, LUT, register counts match design intent
- [ ] `report.printPruned()` clean — no live signals pruned
- [ ] Vendor synthesis critical warnings reviewed and either fixed or explicitly accepted with justification
- [ ] **ASIC:** All PVT corners pass STA; IR drop and EM within specification

---

### 63. Power Checklist

- [ ] No gated clocks in RTL — clock enables used via `when(enable)` mapping to FF CE pin
- [ ] No clock derived from combinational logic — use a PLL output or a buffered pad clock
- [ ] High-activity datapaths reviewed for unnecessary toggling (multiply inputs held when stalled)
- [ ] DSP inference confirmed in synthesis report — no accidental LUT multipliers in performance-critical paths
- [ ] `readSync` used in preference to `readAsync` where latency permits (lower switching activity)
- [ ] **ASIC:** Operand isolation applied to multipliers and wide datapaths on slow-enable control paths
- [ ] **ASIC:** Power analysis run for the operating scenario; hot paths identified and reviewed

---

*This guide is a living document. Update it when patterns change, when new SpinalHDL
versions alter API behavior, and when the team learns from post-silicon or post-implementation
failures. Every rule in this guide was derived from either the template code or a real
failure mode.*
