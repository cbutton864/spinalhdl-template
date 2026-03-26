# SpinalHDL Flat-Plugin Architecture

A design methodology for SpinalHDL FPGA projects using **FiberPlugin** composable architecture.
This pattern produces flat, readable Verilog with no component hierarchy, while keeping
the Scala source modular, testable, and reusable.

---

## Table of Contents

1. [Core Principles](#1-core-principles)
2. [Project Structure](#2-project-structure)
3. [The Plugin Pattern](#3-the-plugin-pattern)
4. [The Core Pattern](#4-the-core-pattern)
5. [TopIoExportPlugin — The Wiring Hub](#5-topioexportplugin--the-wiring-hub)
6. [Params — Centralised Configuration](#6-params--centralised-configuration)
7. [Top Component — Static IO Shell](#7-top-component--static-io-shell)
8. [Verilog Generation](#8-verilog-generation)
9. [Test Infrastructure](#9-test-infrastructure)
10. [Handle Lifecycle & Fiber Mechanics](#10-handle-lifecycle--fiber-mechanics)
11. [Naming Conventions](#11-naming-conventions)
12. [Memory & Synthesis Attributes](#12-memory--synthesis-attributes)
13. [Stream Protocol Conventions](#13-stream-protocol-conventions)
14. [Design Rules Summary](#14-design-rules-summary)
15. [Checklist for Adding a New Feature](#15-checklist-for-adding-a-new-feature)

---

## 1. Core Principles

| Principle | Rationale |
|-----------|-----------|
| **Flat Verilog** | No module hierarchy — all signals are top-level peers. This makes vendor tool synthesis transparent, simplifies timing analysis, and eliminates cross-module optimisation barriers. |
| **Plugin = Feature** | Each FiberPlugin encapsulates one logical feature. Plugins can be added/removed from the design by editing the plugin list in Params. |
| **Core = Logic** | All RTL logic lives in stateless `object XxxCore.build()` methods. Cores are bus-agnostic and testable in isolation. |
| **Fiber = Glue** | SpinalHDL's Fiber system resolves inter-plugin dependencies automatically, regardless of plugin list order. No manual ordering. |
| **Handle = Contract** | `Handle[T]` is the inter-plugin communication primitive. Producer loads, consumer awaits. Type-safe, deadlock-detectable. |

---

## 2. Project Structure

```
project-root/
├── build.sbt                           # SBT build with SpinalHDL deps
├── project/
│   ├── build.properties                # SBT version
│   └── plugins.sbt                     # SBT plugins (scalafmt, etc.)
├── src/
│   ├── main/scala/<pkg>/
│   │   ├── Params.scala                # Centralised parameters + plugin list
│   │   ├── Top.scala                   # Top Component (static IO only)
│   │   ├── GenVerilog.scala            # Verilog generation entry point
│   │   ├── XxxPlugin.scala            # FiberPlugin wrapper per feature
│   │   ├── XxxCore.scala              # Core logic (object + build method)
│   │   └── TopIoExportPlugin.scala    # Single IO wiring point
│   └── test/scala/<pkg>/
│       ├── XxxCoreTest.scala           # Unit tests per core
│       ├── ElaborationTest.scala       # Smoke test: full design elaborates
│       └── testhelpers/
│           └── XxxHarness.scala        # Simulation wrappers for cores
├── rtl/                                # Generated Verilog output
├── docs/                               # Design documentation
└── simWorkspace/                       # Verilator simulation artifacts
```

---

## 3. The Plugin Pattern

A **Plugin** is a `case class` extending `FiberPlugin`. It does three things:

1. **Declares Handles** — the plugin's published outputs and consumed inputs
2. **Awaits dependencies** — blocks on Handles from other plugins via `host[OtherPlugin]`
3. **Delegates to a Core** — calls `XxxCore.build()` with the resolved signals, then loads results into Handles

```scala
case class CounterPlugin(width: Int = 8) extends FiberPlugin {

  // Published output Handle
  val countOut: Handle[UInt] = Handle[UInt]()

  // Input Handle (loaded by TopIoExportPlugin)
  val enableIn: Handle[Bool] = Handle[Bool]()

  val logic = during build new Area {
    // Phase 1: await inputs from other plugins
    val enable = enableIn.await

    // Phase 2: delegate to core
    val core = CounterCore.build(
      periphName = "counter",
      width      = width,
      enable     = enable
    )

    // Phase 3: load outputs for downstream consumers
    countOut.load(core.count)
  }
}
```

### Key rules for Plugins:
- **No RTL logic** in the plugin itself — only Handle plumbing
- **One `during build` block** per plugin
- **`host[OtherPlugin]`** to access other plugins' Handles (Fiber-resolved)
- **`await`** blocks until the Handle is loaded — ordering is automatic
- **`load`** publishes a signal — unblocks any awaiting consumers
- **`case class`** (not `class`) for clean construction with parameters

---

## 4. The Core Pattern

A **Core** is a stateless Scala `object` with a `def build()` method. The build method:

1. Takes parameters and input signals as arguments
2. Creates registers, combinational logic, memories
3. Returns a plain Scala `case class Io` containing output signal references

```scala
object CounterCore {

  // Return type: plain Scala case class — NOT a Bundle
  case class Io(count: UInt)

  def build(
      periphName: String = "counter",
      width:      Int    = 8,
      enable:     Bool   = null    // signal reference, not a Bundle port
  ): Io = {
    require(enable != null, "enable signal is required")

    val countReg = Reg(UInt(width bits)) init 0
    countReg.setName(s"${periphName}_countReg")

    when(enable) {
      countReg := countReg + 1
    }

    Io(count = countReg)
  }
}
```

### Key rules for Cores:
- **`object`**, not `class` — stateless, all state is in the returned signals
- **`case class Io`** return type — plain Scala, NOT `extends Bundle`
- **`periphName` prefix** on all registers and memories — prevents name collisions in flat Verilog
- **`require()` guards** validate inputs at elaboration time
- **Bus-agnostic** — cores take raw signals (`Bool`, `UInt`, `Stream[T]`), not IO bundles
- **No Component hierarchy** — `build()` runs inside the calling Component's scope
- **Testable in isolation** via a Harness wrapper (see Section 9)

### Why `case class Io` instead of `Bundle`?

Bundles in SpinalHDL create hardware direction semantics (`in`/`out`). The Core pattern
doesn't need this — it returns signal *references* that the Plugin wires up. Using a plain
Scala case class:
- Avoids accidental direction inference issues
- Makes it clear this is a data container, not hardware
- Can hold any type (Streams, Bools, Ints, even non-hardware data)

---

## 5. TopIoExportPlugin — The Wiring Hub

The **TopIoExportPlugin** is the single point where plugin Handles connect to top-level IO pads.
It follows a strict **two-phase pattern** to prevent Fiber deadlocks:

```scala
case class TopIoExportPlugin() extends FiberPlugin {
  val logic = during build new Area {
    val top = Component.current.asInstanceOf[MyTop]

    // ══════════════════════════════════════════════
    // Phase 1: LOAD input Handles (non-blocking)
    //   Feeds pad signals into plugins.
    //   These complete immediately — no blocking.
    // ══════════════════════════════════════════════
    Try(host[CounterPlugin]).toOption.foreach { counter =>
      counter.enableIn.load(top.io.enable)
    }

    // ══════════════════════════════════════════════
    // Phase 2: AWAIT output Handles (blocking)
    //   Wires plugin outputs to pads.
    //   These block until the producer plugin loads.
    // ══════════════════════════════════════════════
    Try(host[CounterPlugin]).toOption match {
      case Some(counter) =>
        top.io.count := counter.countOut.await
      case None =>
        top.io.count := 0  // safe default when plugin absent
    }
  }
}
```

### Two-Phase Rule (Critical)

| Phase | Operation | Blocking? | Purpose |
|-------|-----------|-----------|---------|
| 1 | `handle.load(padSignal)` | No | Feed pad inputs into plugins |
| 2 | `handle.await` | Yes | Read plugin outputs to pads |

**Why?** If Phase 2 ran first, TopIoExportPlugin would block awaiting a plugin's output.
But that plugin is blocked awaiting its input Handle — which TopIoExportPlugin hasn't loaded
yet. Deadlock. Loads always go first.

### Try/toOption Pattern

```scala
Try(host[SomePlugin]).toOption.foreach { plugin => ... }
Try(host[SomePlugin]).toOption match {
  case Some(plugin) => // wire outputs
  case None         => // safe defaults
}
```

This makes each wiring section safe when a plugin is absent. You can remove plugins from
the Params list without touching TopIoExportPlugin.

---

## 6. Params — Centralised Configuration

All hardware parameters live in a single `case class Params`. The plugin list is a method
that constructs plugins with the appropriate parameters:

```scala
case class Params(
    sysClkHz:     HertzNumber = 100 MHz,
    counterWidth: Int         = 8,
    threshold:    Int         = 128
) {
  def plugins: Seq[FiberPlugin] = Seq(
    CounterPlugin(width = counterWidth),
    ThresholdPlugin(threshold = threshold),
    TopIoExportPlugin()
  )
}
```

### Rules:
- **Plugin list order doesn't matter** — Fiber resolves all dependencies
- **`def plugins`** (not `val`) — fresh instances each time (important for re-elaboration in tests)
- **TopIoExportPlugin always last by convention** (cosmetic, not required)
- **All numeric constants** pass through Params — no magic numbers in cores

---

## 7. Top Component — Static IO Shell

The Top Component is the **one real Component** in the design. It has:

1. **Static `val io = new Bundle { ... }`** — all pad-level ports declared here
2. **`setName()` calls** — flatten Verilog port names (removes `io_` prefix)
3. **`PluginHost` construction** — assembles all plugins from Params

```scala
class MyTop(params: Params = Params()) extends Component {
  val io = new Bundle {
    val enable    = in  Bool()
    val count     = out UInt(params.counterWidth bits)
    val aboveFlag = out Bool()
  }

  // Flatten port names for clean Verilog
  io.enable.setName("enable")
  io.count.setName("count")
  io.aboveFlag.setName("above_flag")

  // All logic is in plugins
  val host = new PluginHost
  host.asHostOf(params.plugins)
}
```

### Rules:
- **No logic** in Top — only IO declarations and PluginHost
- **`setName()`** on every IO for vendor-tool-friendly Verilog port names
- **BlackBoxes and CDCs** are the only other allowed Components (e.g., `StreamFifoCC`)

---

## 8. Verilog Generation

```scala
object GenVerilog extends App {
  val report = SpinalConfig(
    targetDirectory = "rtl",
    defaultClockDomainFrequency = FixedFrequency(100 MHz),
    defaultConfigForClockDomains = ClockDomainConfig(
      resetKind        = ASYNC,
      resetActiveLevel = HIGH
    )
  ).generateVerilog {
    val top = new MyTop(Params())
    // Optional: rename clock/reset to match board
    // top.clockDomain.clock.setName("sys_clk")
    // top.clockDomain.reset.setName("sys_rst")
    top
  }
  report.printPruned()
}
```

Run: `sbt "runMain mydesign.GenVerilog"`

Output lands in `rtl/MyTop.v`.

---

## 9. Test Infrastructure

### Test Harness Pattern

Cores can't be simulated directly — they run inside a Component's scope.
A **Harness** wraps the core in a minimal Component with sim-friendly IO:

```scala
class CounterHarness(width: Int = 8) extends Component {
  val io = new Bundle {
    val enable = in  Bool()
    val count  = out UInt(width bits)
  }

  val core = CounterCore.build(
    periphName = "counter",
    width      = width,
    enable     = io.enable
  )

  io.count := core.count
}
```

### Test Pattern

Tests use ScalaTest `AnyFunSuite` + SpinalHDL simulation:

```scala
class CounterCoreTest extends AnyFunSuite {
  val width = 8

  def compile() = SimConfig
    .withWave                                        // VCD waveform dump
    .workspacePath("simWorkspace/CounterCoreTest")   // per-test output dir
    .compile(new CounterHarness(width = width))      // compile once

  test("Counter increments when enabled") {
    compile().doSim("increment") { dut =>
      dut.clockDomain.forkStimulus(period = 10)
      dut.io.enable #= false
      dut.clockDomain.waitSampling(3)

      dut.io.enable #= true
      for (i <- 1 to 10) {
        dut.clockDomain.waitSampling()
        assert(dut.io.count.toInt == i,
          s"Cycle $i: expected $i, got ${dut.io.count.toInt}")
      }
    }
  }
}
```

### Test Types

| Test Type | File | Purpose |
|-----------|------|---------|
| **Elaboration** | `ElaborationTest.scala` | Smoke test — full design generates Verilog |
| **Core unit** | `XxxCoreTest.scala` | Functional tests via Verilator simulation |
| **Chain/integration** | `XxxChainTest.scala` | Multi-core harness testing pipeline stages |

### Test Conventions:
- **`compile()` method** — reusable SimConfig for all tests in the suite
- **`withWave`** — always dump VCD for post-mortem debugging
- **`workspacePath`** — unique per test class (Verilator builds)
- **`doSim("name")`** — named simulation runs within a test
- **`clockDomain.forkStimulus(period = 10)`** — standard clock setup
- **`#=`** — SpinalHDL sim signal assignment operator
- **`waitSampling(n)`** — advance n clock edges
- **Fork blocks** for parallel stimulus/collection
- **`Test / parallelExecution := false`** in build.sbt to avoid Verilator conflicts

---

## 10. Handle Lifecycle & Fiber Mechanics

### Handle States

```
  Created         Loaded          Awaited
  ─────────►  ─────────────►  ──────────►
  Handle[T]()    handle.load(x)   handle.await
  (empty)        (signal bound)   (returns signal)
```

### Dependency Resolution

```
  CounterPlugin                    ThresholdPlugin
  ┌─────────────────────┐         ┌─────────────────────┐
  │ enableIn.await ◄────┼────┐    │ host[CounterPlugin]  │
  │   ↓                 │    │    │   .countOut.await ◄──┼─── blocks until
  │ CounterCore.build() │    │    │     ↓                │    countOut loaded
  │   ↓                 │    │    │ ThresholdCore.build() │
  │ countOut.load(───)──┼────┼───►│     ↓                │
  └─────────────────────┘    │    │ aboveFlag.load(───)  │
                             │    └─────────────────────┘
  TopIoExportPlugin          │
  ┌─────────────────────┐    │
  │ Phase 1: loads ─────┼────┘   (non-blocking: loads enableIn)
  │ Phase 2: awaits     │         (blocking: awaits countOut, aboveFlag)
  └─────────────────────┘
```

Fiber automatically determines execution order from the `load`/`await` graph.
No manual ordering required.

### Common Deadlock Causes

| Cause | Symptom | Fix |
|-------|---------|-----|
| Circular await | Fiber timeout | Break cycle — one plugin must load without awaiting the other |
| Phase 2 before Phase 1 in TopIoExport | Fiber timeout | Always load inputs first, then await outputs |
| Missing `load()` call | Fiber timeout (consumer blocks forever) | Check that every published Handle gets loaded |

---

## 11. Naming Conventions

| Item | Convention | Example |
|------|-----------|---------|
| Registers | `${periphName}_xxxReg` | `counter_countReg` |
| Memories | `${periphName}_xxxBuffer` | `green_ext_lineBuffer` |
| Plugin class | `XxxPlugin` (PascalCase) | `CounterPlugin` |
| Core object | `XxxCore` (PascalCase) | `CounterCore` |
| Plugin package | feature-based grouping | `mydesign.processing` |
| IO ports | `snake_case` via `setName()` | `above_flag` |
| Test class | `XxxCoreTest` | `CounterCoreTest` |
| Harness | `XxxHarness` | `CounterHarness` |

### Why `periphName` prefix?

In flat Verilog, all registers are top-level. Without prefixes, `countReg` from two
different cores would collide. The `periphName` parameter ensures unique names:
`counter_countReg`, `timer_countReg`.

---

## 12. Memory & Synthesis Attributes

### Block RAM with Attributes

When using `Mem()` for block RAM, add synthesis attributes to control inference:

```scala
val lineBuffer = Mem(UInt(8 bits), 1920)
lineBuffer.setName(s"${periphName}_lineBuffer")
lineBuffer.addAttribute("syn_ramstyle",  "block_ram")   // force BRAM inference
lineBuffer.addAttribute("syn_ramdecomp", "area")        // minimise block count
```

### Attribute Reference

| Attribute | Values | Effect |
|-----------|--------|--------|
| `syn_ramstyle` | `"block_ram"`, `"distributed"` | Force BRAM or LUT RAM inference |
| `syn_ramdecomp` | `"area"`, `"speed"` | Optimise for block count vs. throughput |
| `ram_style` | `"distributed"` | SpinalHDL's built-in distributed RAM hint |

### readSync vs readAsync

- **`readSync`** — registered read, maps to BRAM. 1-cycle latency. **Preferred.**
- **`readAsync`** — combinational read, maps to LUT/distributed RAM. Higher timing pressure.

```scala
// Block RAM (readSync — 1 cycle latency, needs pipeline alignment)
val rdData = mem.readSync(address, enable = fireSignal)

// Distributed/LUT RAM (readAsync — combinational, no latency)
val rdData = mem.readAsync(address)
```

---

## 13. Stream Protocol Conventions

### Monitoring Taps (No Back-Pressure)

When a core observes a stream without consuming it:

```scala
// The core snoops fire + payload without driving ready
val pixFire    = pixelIn.fire      // observe transaction
val pixPayload = pixelIn.payload   // observe data
// ready is driven by the actual consumer, not this core
```

### Sink Pattern (When Plugin is Absent)

When a stream has no consumer, sink it to prevent deadlock:

```scala
Try(host[ConsumerPlugin]).toOption match {
  case Some(consumer) => // consumer handles the stream
  case None =>
    someStream.ready := True  // sink: accept and discard
}
```

### Frame/Line Protocol

Pixel streams carry framing sideband signals:

```scala
case class MyPixel() extends Bundle {
  val data       = UInt(8 bits)
  val frameStart = Bool()     // pulse on first pixel of frame
  val frameEnd   = Bool()     // pulse on last pixel of frame
  val lineStart  = Bool()     // pulse on first pixel of line
  val lineEnd    = Bool()     // pulse on last pixel of line
}
```

---

## 14. Design Rules Summary

```
┌──────────────────────────────────────────────────────────────┐
│  Design Rules                                                │
├──────────────────────────────────────────────────────────────┤
│                                                              │
│  Components only at edges:                                   │
│    • MyTop              (the one real Component)             │
│    • BlackBoxes         (vendor hard-IP)                     │
│    • StreamFifoCC       (CDC primitive)                      │
│                                                              │
│  Everything else is a FiberPlugin + def build():             │
│    • Core logic lives in object XxxCore.build()              │
│    • Plugin wraps core, publishes Handle[T]                  │
│    • Plain case class Io (NOT extends Bundle) for returns    │
│    • No Component hierarchy — all signals are toplevel peers │
│                                                              │
│  Cross-plugin wiring:                                        │
│    • Handles resolve via Fiber (order-independent)           │
│    • TopIoExportPlugin is the single IO wiring point         │
│    • Internal: producer loads Handle, consumer awaits        │
│    • Pad inputs: TopIoExport loads, plugin awaits            │
│    • Pad outputs: plugin loads, TopIoExport awaits           │
│    • Two-phase in TopIoExport: load inputs first, then await │
│                                                              │
│  Naming:                                                     │
│    • periphName prefix on all internal registers/memories    │
│    • setName() on top IO for clean Verilog port names        │
│    • Internal reg names must not collide with IO port names  │
│                                                              │
│  Swappability:                                               │
│    • Params.plugins defines the active plugin list           │
│    • Plugins can be added/removed without touching others    │
│    • Try(host[X]).toOption makes wiring safe for absent      │
│      plugins                                                 │
│                                                              │
│  Testing:                                                    │
│    • Cores tested in isolation via Harness wrappers          │
│    • Elaboration test validates full Fiber graph             │
│    • No parallel test execution (Verilator builds conflict)  │
│                                                              │
└──────────────────────────────────────────────────────────────┘
```

---

## 15. Checklist for Adding a New Feature

When adding a new feature (e.g., a UART, a filter, a state machine):

- [ ] **Create `XxxCore.scala`** — `object XxxCore` with `case class Io` and `def build()`
- [ ] **Create `XxxPlugin.scala`** — `case class XxxPlugin` extending `FiberPlugin`
  - Declare all published Handles
  - In `during build`: await inputs, call `XxxCore.build()`, load outputs
- [ ] **Add to `Params.plugins`** — include the new plugin in the Seq
- [ ] **Wire in `TopIoExportPlugin`** — Phase 1: load inputs, Phase 2: await outputs
  - Use `Try(host[XxxPlugin]).toOption` for safe wiring
  - Provide defaults in `case None =>` branch
- [ ] **Add IO to `MyTop.io`** — if the feature has external pad connections
  - Add `setName()` call for clean Verilog names
- [ ] **Create `XxxHarness.scala`** — test wrapper Component in `testhelpers/`
- [ ] **Create `XxxCoreTest.scala`** — unit tests with `compile().doSim()`
- [ ] **Update `ElaborationTest`** — ensure it still passes (usually automatic)
- [ ] **Run `sbt test`** — all tests must pass
- [ ] **Run `sbt "runMain mydesign.GenVerilog"`** — verify clean Verilog output
