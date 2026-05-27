# SpinalHDL Flat-Plugin Architecture

A design methodology for SpinalHDL FPGA and ASIC projects using **FiberPlugin** composable
architecture. This pattern produces flat, readable Verilog with no component hierarchy, while
keeping the Scala source modular, testable, and reusable.

---

## Table of Contents

1. [Core Principles](#1-core-principles)
2. [Project Structure](#2-project-structure)
3. [The Plugin Pattern](#3-the-plugin-pattern)
4. [The Core Pattern](#4-the-core-pattern)
5. [Service Traits: Pipeline Stage Contracts](#5-service-traits-pipeline-stage-contracts)
6. [Pipeline Stages as Named Areas](#6-pipeline-stages-as-named-areas)
7. [TopIoExportPlugin: The Wiring Hub](#7-topioexportplugin-the-wiring-hub)
8. [Params: Centralised Configuration](#8-params-centralised-configuration)
9. [Top Component: Static IO Shell](#9-top-component-static-io-shell)
10. [Verilog Generation](#10-verilog-generation)
11. [Test Infrastructure](#11-test-infrastructure)
12. [Handle Lifecycle and Fiber Mechanics](#12-handle-lifecycle-and-fiber-mechanics)
13. [Naming Conventions](#13-naming-conventions)
14. [Memory and Synthesis Attributes](#14-memory-and-synthesis-attributes)
15. [Stream Protocol Conventions](#15-stream-protocol-conventions)
16. [Optional Plugins: EdgeDetector and APB](#16-optional-plugins-edgedetector-and-apb)
17. [Design Rules Summary](#17-design-rules-summary)
18. [Checklist for Adding a New Feature](#18-checklist-for-adding-a-new-feature)

---

## 1. Core Principles

| Principle | Rationale |
|-----------|-----------|
| **Flat Verilog** | No module hierarchy; all signals are top-level peers. Transparent synthesis, easy timing analysis, no cross-module optimisation barriers. |
| **Plugin = Feature** | Each FiberPlugin encapsulates one logical feature. Features are added or removed by editing the plugin list in Params. |
| **Core = Logic** | All RTL logic lives in stateless `object XxxCore.build()` methods. Cores are bus-agnostic and testable in isolation. |
| **Fiber = Glue** | SpinalHDL's Fiber system resolves inter-plugin dependencies automatically, regardless of plugin list order. No manual ordering. |
| **Handle = Contract** | `Handle[T]` is the inter-plugin communication primitive. Producer loads, consumer awaits. Type-safe and deadlock-detectable. |
| **Trait = Stage Boundary** | Scala traits define the contract between pipeline stages. Plugins implement a trait to publish outputs; consume a trait to snap into the upstream stage. |

---

## 2. Project Structure

```
project-root/
├── build.sbt
├── project/
│   ├── build.properties              # sbt version pin; always commit
│   └── plugins.sbt
├── src/
│   ├── main/scala/<pkg>/
│   │   ├── Params.scala              # Parameters + plugin list
│   │   ├── Top.scala                 # Top Component (static IO only)
│   │   ├── GenVerilog.scala          # Verilog generation entry point
│   │   ├── PipelineTraits.scala      # Stage-boundary service traits
│   │   ├── PipelineSubsystemPlugin.scala # Production composite grouping example
│   │   ├── TimerPlugin.scala         # Stage 1: signal source
│   │   ├── TimerCore.scala
│   │   ├── PassThroughPlugin.scala   # Stage 2 identity (swap with ScalePlugin)
│   │   ├── ScalePlugin.scala         # Stage 2 alternate: right-shift scaling
│   │   ├── ComparatorPlugin.scala    # Stage 3: threshold comparator
│   │   ├── ComparatorCore.scala
│   │   ├── HysteresisPlugin.scala    # Stage 3 alternate: dual-threshold latch
│   │   ├── EdgeDetectorPlugin.scala  # Optional stage 4: edge detection
│   │   ├── EdgeDetectorCore.scala    # Demonstrates named-Area pipeline stages
│   │   ├── ApbMonitorPlugin.scala    # Optional side channel: APB3 register read-back
│   │   └── util/
│   │       ├── BuildHelper.scala              # Core autoPull and PrefixArea utilities
│   │       └── SubsystemCompositePlugin.scala # Generic composite subsystem wrapper plugin
│   │   └── TopIoExportPlugin.scala   # Single IO wiring point
│   └── test/scala/<pkg>/
│       ├── TimerCoreTest.scala
│       ├── ComparatorCoreTest.scala
│       ├── EdgeDetectorCoreTest.scala
│       ├── ElaborationTest.scala
│       ├── HierarchyCornerCasesTest.scala # Dynamic boundary testing
│       ├── SubsystemCompositeTest.scala  # Testing production PipelineSubsystemPlugin
│       └── testhelpers/
│           ├── TimerHarness.scala
│           ├── ComparatorHarness.scala
│           └── EdgeDetectorHarness.scala
├── rtl/                              # Generated Verilog output
├── docs/
│   ├── ARCHITECTURE.md              # This file
│   └── STYLE_GUIDE.md               # Full design rules and conventions
└── simWorkspace/                     # Verilator artifacts; gitignored
```

---

## 3. The Plugin Pattern

A **Plugin** is a `case class` extending `FiberPlugin`. It does three things:

1. **Declares Handles**: the plugin's published outputs and consumed inputs
2. **Awaits dependencies**: blocks on Handles from other plugins via `host[Trait]`
3. **Delegates to a Core**: calls `XxxCore.build()` with the resolved signals, then loads results into Handles

```scala
case class TimerPlugin(width: Int = 8) extends FiberPlugin with SignalSource {

  val countOut:  Handle[UInt] = Handle[UInt]()  // published output (concrete name)
  val signalOut: Handle[UInt] = countOut        // SignalSource boundary; same Handle, two names
  val enableIn:  Handle[Bool] = Handle[Bool]()  // loaded by TopIoExportPlugin

  val logic = during build new Area {
    val enable = enableIn.await                  // await input
    val core   = TimerCore.build(
      periphName = "timer",
      width      = width,
      enable     = enable
    )
    countOut.load(core.count)                    // publish output; signalOut resolves automatically
  }
}
```

### Key rules for Plugins

- **No RTL logic** in the plugin class body; only Handle declarations
- **One `during build` block** per plugin (named `val logic`)
- **`host[Trait]`** to access the upstream stage boundary (Fiber-resolved)
- **`await`** blocks until the Handle is loaded; ordering is automatic
- **`load`** publishes a signal; unblocks any awaiting consumers
- **`case class`** for clean construction with parameters

---

## 4. The Core Pattern

A **Core** is a stateless Scala `object` with a `def build()` method:

```scala
object TimerCore {
  case class Io(count: UInt)   // plain Scala case class; NOT a Bundle

  def build(
      periphName: String = "timer",
      width:      Int    = 8,
      enable:     Bool   = null
  ): Io = {
    require(enable != null, "enable signal is required")

    val logic = new PrefixArea(periphName) {
      val countReg = Reg(UInt(width bits)) init 0

      when(enable) { countReg := countReg + 1 }
    }

    Io(count = logic.countReg)
  }
}
```

### Key rules for Cores

- **`object`**: stateless; all state lives in the returned hardware nodes
- **`case class Io`**: plain Scala, NOT `extends Bundle`
- **`PrefixArea(periphName)`**: Automatically wraps inner registers and memories to give them unique names in flat Verilog, completely eliminating manual `.setName()` string boilerplates for child signals.
- **`require()` guards**: validate inputs at elaboration time, before hardware is created
- **Bus-agnostic**: takes raw signals, not bus-specific bundles
- **No Component instantiation** inside `build()`

### Why `case class Io` instead of `Bundle`?

Bundles carry direction semantics (`in`/`out`). A Core returns signal *references*, not
a port declaration. A plain case class cannot accidentally cause direction-mismatch errors
and can hold any Scala or hardware type.

---

## 5. Service Traits: Pipeline Stage Contracts

A **service trait** is a zero-hardware Scala trait that defines the output contract for one
pipeline stage. Plugins implement a trait to declare what they produce; plugins consume a
trait to declare what they need upstream. Neither side depends on a specific implementation.

```scala
// PipelineTraits.scala: all stage boundaries in one file
trait SignalSource    { val signalOut:    Handle[UInt] }
trait ProcessedSignal { val processedOut: Handle[UInt] }
trait ThresholdResult { val aboveFlag:    Handle[Bool] }
trait EdgeResult      { val risingEdge:   Handle[Bool]
                        val fallingEdge:  Handle[Bool] }
```

### Producer side: implementing a trait

```scala
case class ComparatorPlugin(threshold: Int = 128)
    extends FiberPlugin with ThresholdResult {

  val aboveFlag: Handle[Bool] = Handle[Bool]()   // satisfies ThresholdResult

  val logic = during build new Area {
    val countVal = host[ProcessedSignal].processedOut.await  // consumes upstream trait
    val core = ComparatorCore.build(...)
    aboveFlag.load(core.above)
  }
}
```

### Consumer side: consuming a trait

```scala
case class EdgeDetectorPlugin() extends FiberPlugin with EdgeResult {

  val risingEdge:  Handle[Bool] = Handle[Bool]()
  val fallingEdge: Handle[Bool] = Handle[Bool]()

  val logic = during build new Area {
    val above = host[ThresholdResult].aboveFlag.await  // works with any ThresholdResult impl
    val core  = EdgeDetectorCore.build(periphName = "edgeDetector", input = above)
    risingEdge.load(core.rising)
    fallingEdge.load(core.falling)
  }
}
```

### Swapping a stage

Because both sides depend only on the trait, swapping the Stage 3 implementation is a
one-line change in `Params.plugins`:

```scala
// Before
ComparatorPlugin(threshold = threshold)

// After: no other file changes needed
HysteresisPlugin(loThreshold = 64, hiThreshold = 192)
```

Both plugins implement `ThresholdResult`. Anything downstream (`EdgeDetectorPlugin`,
`ApbMonitorPlugin`, `TopIoExportPlugin`) continues to compile and work unchanged.

### When to define a new trait

Define a trait when a stage boundary is a stable, named interface that more than one plugin
might implement or consume. Do not trait-wrap every internal signal; one trait per meaningful
pipeline stage boundary is enough.

---

## 6. Pipeline Stages as Named Areas

`Area` is SpinalHDL's mechanism for grouping related signals without creating a Verilog
module boundary. Each CPU pipeline stage in large SpinalHDL designs is a named Area.
The same pattern applies to any multi-stage Core.

**Named Areas do two things:**
1. They prefix contained signal names with the Area's name in waveforms, aiding debug.
2. They make the pipeline stages explicit and self-documenting in the Scala source.

`EdgeDetectorCore` demonstrates a 2-register pipeline using named Areas:

```scala
object EdgeDetectorCore {
  case class Io(rising: Bool, falling: Bool)

  def build(periphName: String = "edgeDetector", input: Bool = null): Io = {
    require(input != null, "input signal is required")

    // Stage 1: capture previous value
    val prevStage = new Area {
      val prev = RegNext(input) init False
      prev.setName(s"${periphName}_prev")
    }
    // Waveform: prevStage_prev

    // Stage 2: detect and register edge events
    val edgeStage = new Area {
      val rising  = RegNext(input && !prevStage.prev) init False
      val falling = RegNext(!input && prevStage.prev) init False
      rising.setName(s"${periphName}_rising")
      falling.setName(s"${periphName}_falling")
    }
    // Waveforms: edgeStage_rising, edgeStage_falling

    Io(rising = edgeStage.rising, falling = edgeStage.falling)
  }
}
```

### Pipeline timing diagram

```
Clock    --+ +--+ +--+ +--+ +--
           +-+  +-+  +-+  +-+

input    ----------+
                   +-----------

prevStage.prev       ----------+
                               +-

edgeStage.rising          -----+
(output valid)                 +-
```

- **Latency:** 1 clock cycle from input change to output pulse
- **Pulse width:** exactly 1 cycle
- Both registers update on the same rising edge. The detection `input && !prev` uses the
  pre-edge value of `prev`, so the pulse fires at the same edge that `prev` latches the
  new input value.

### Extending to deeper pipelines

The pattern scales to N stages. Each stage is a named Area that reads from the previous:

```scala
val fetchStage = new Area {
  val data  = RegNext(input)        init 0
  val valid = RegNext(inputValid)   init False
}

val executeStage = new Area {
  val result = RegNext(process(fetchStage.data))  init 0
  val valid  = RegNext(fetchStage.valid)           init False
}

val writebackStage = new Area {
  val output = RegNext(executeStage.result)  init 0
  val valid  = RegNext(executeStage.valid)   init False
}
```

Stage names appear in waveforms as `fetchStage_data`, `executeStage_result`,
`writebackStage_output`. Document pipeline latency in the Core scaladoc so
simulation tests wait the correct number of cycles.

---

## 7. TopIoExportPlugin: The Wiring Hub

The **TopIoExportPlugin** is the single point where plugin Handles connect to top-level
IO pads. It follows a strict **two-phase pattern**:

```scala
case class TopIoExportPlugin() extends FiberPlugin {
  val logic = during build new Area {
    val top = Component.current.asInstanceOf[MyTop]

    // Phase 1: LOAD input Handles (non-blocking)
    Try(host[TimerPlugin]).toOption.foreach { timer =>
      timer.enableIn.load(top.io.enable)
    }

    // Phase 2: AWAIT output Handles (blocking)
    Try(host[TimerPlugin]).toOption match {
      case Some(timer) => top.io.count    := timer.countOut.await
      case None        => top.io.count    := 0
    }

    Try(host[ThresholdResult]).toOption match {
      case Some(thresh) => top.io.aboveFlag := thresh.aboveFlag.await
      case None         => top.io.aboveFlag := False
    }

    Try(host[EdgeResult]).toOption match {
      case Some(edge) =>
        top.io.risingEdge  := edge.risingEdge.await
        top.io.fallingEdge := edge.fallingEdge.await
      case None =>
        top.io.risingEdge  := False
        top.io.fallingEdge := False
    }

    Try(host[ApbMonitorPlugin]).toOption match {
      case None =>
        top.io.apb_PREADY  := True
        top.io.apb_PRDATA  := B(0, 32 bits)
        top.io.apb_PSLVERR := False
      case Some(_) => // ApbMonitorPlugin drives these directly
    }
  }
}
```

Note that `host[ThresholdResult]` and `host[EdgeResult]` consume service traits, not
concrete plugin types. Swapping `ComparatorPlugin` for `HysteresisPlugin` requires no
change here.

`TimerPlugin` is still accessed concretely because `TopIoExportPlugin` needs to load its
`enableIn` input Handle, which is specific to the timer implementation and not part of any
upstream-facing trait.

### Two-Phase Rule

| Phase | Operation | Blocking? | Purpose |
|-------|-----------|-----------|---------|
| 1 | `handle.load(padSignal)` | No | Feed pad inputs to plugins |
| 2 | `handle.await` | Yes | Read plugin outputs and drive pads |

**Why?** Phase 2 blocks. If it ran before Phase 1, `TopIoExportPlugin` would block
waiting for a plugin's output, but that plugin is blocked waiting for its input Handle,
which Phase 1 has not loaded yet. Deadlock. Loads always go first.

---

## 8. Params & Elaboration Modes

```scala
case class Params(
    sysClkHz:   HertzNumber = 100 MHz,
    timerWidth: Int         = 8,
    threshold:  Int         = 128,
    globalHierarchy: Option[Boolean] = None
) {
  def plugins: Seq[FiberPlugin] = Seq(
    TimerPlugin(width = timerWidth, hierarchical = globalHierarchy.getOrElse(false)), // Stage 1
    PassThroughPlugin(),                        // Stage 2: swap with ScalePlugin(shift = 2)
    ComparatorPlugin(threshold = threshold),    // Stage 3: swap with HysteresisPlugin(lo, hi)
    TopIoExportPlugin()
  )
}
```

- **`def plugins`** (not `val`): fresh instances on every call; required for test re-elaboration
- **Plugin list order does not matter**: Fiber resolves all dependencies
- **All constants pass through Params**: no magic numbers in Cores or Plugins
- **Elaboration Modes Configuration:** 
  - `DebugBuild` Mode: Compiles modules inside explicit sub-component boundaries. Recommended for floorplanning, physical area partitions, and simulator wave tracing.
  - `ProductionBuild` Mode: Flatly merges and compiles registers for global area optimization steps.

---

## 9. Top Component: Static IO Shell

```scala
class MyTop(params: Params = Params()) extends Component {
  val io = new Bundle {
    val enable      = in  Bool()
    val count       = out UInt(params.timerWidth bits)
    val aboveFlag   = out Bool()
    val risingEdge  = out Bool()
    val fallingEdge = out Bool()
    val apb         = slave(Apb3(Apb3Config(addressWidth = 8, dataWidth = 32)))
  }

  io.enable.setName("enable")
  io.count.setName("count")
  io.aboveFlag.setName("aboveFlag")
  io.risingEdge.setName("risingEdge")
  io.fallingEdge.setName("fallingEdge")

  val host = new PluginHost
  host.asHostOf(params.plugins)
}
```

**Rules:**
- No logic in Top; only IO declarations and PluginHost
- `setName()` on every IO port; required for clean Verilog port names
- Optional plugin ports are always declared; safe defaults are driven when the plugin is absent

---

## 10. Verilog Generation

```bash
sbt "runMain mydesign.GenVerilog"   # generates rtl/MyTop.v
```

`GenVerilog.scala` calls `report.printPruned()` after generation. **Pruned signals are a
warning sign**: they usually mean a missing connection, not an optimisation.

### Parameterized Meta-Hierarchy (The `buildBlock` Pattern)

For debugging and schematic mapping, you can optionally wrap flat blocks with hierarchical boundaries at elaboration-time under parameterized configurations using `BuildHelper.buildBlock`. 

This allows you to select flat compilation for maximum production synthesis optimization, or nested modules for timing analysis and wave tracing.

#### Implementation Pattern
```scala
val timerCount = BuildHelper.buildBlock(HardType(UInt(width bits)), hierarchical, "TimerSub", enable) { pulledEnable => outSig =>
  val core = TimerCore.build("timer", width, enable = pulledEnable)
  outSig := core.count
}
```

#### Verification Rules
- **Automated Input Pulling**: Use `BuildHelper.buildBlock` with the `inputs` parameter. This automatically calls `.pull()` on any inputs passed through (including single signals, tuples, or collections) when compiling in hierarchical mode, keeping the user logic clean and avoiding `PhaseCheckHierarchy` warnings.
- **Definite Port Names**: Dynamic components must execute `.setDefinitionName()` and `.setName()` to prevent blank/unnamed sub-module names in the generated Verilog source.
- **Behavioral Equivalence**: The inner core RTL must maintain cycle-exact timing and functional equivalence in both flat and hierarchical modes.

---

## 11. Test Infrastructure

### Test Harness Pattern

Cores are `object`s; they have no Component boundary for Verilator to target. A Harness
wraps the Core in a minimal Component:

```scala
class TimerHarness(width: Int = 8) extends Component {
  val io = new Bundle {
    val enable = in  Bool()
    val count  = out UInt(width bits)
  }
  val core = TimerCore.build(periphName = "timer", width = width, enable = io.enable)
  io.count := core.count
}
```

### Test Pattern

```scala
class TimerCoreTest extends AnyFunSuite {
  def compile() = SimConfig
    .withWave
    .workspacePath("simWorkspace/TimerCoreTest")
    .compile(new TimerHarness(width = 8))

  test("Timer increments when enabled") {
    compile().doSim("increment") { dut =>
      dut.clockDomain.forkStimulus(period = 10)
      dut.io.enable #= true
      for (i <- 1 to 10) {
        dut.clockDomain.waitSampling()
        sleep(1)
        assert(dut.io.count.toInt == i,
          s"Cycle $i: expected $i, got ${dut.io.count.toInt}")
      }
    }
  }
}
```

### Test Types

| Type | File | Purpose |
|------|------|---------|
| **Elaboration** | `ElaborationTest.scala` | Smoke test: full design generates Verilog for all plugin combinations |
| **Core unit** | `XxxCoreTest.scala` | Functional tests via Verilator simulation |
| **Integration** | `XxxChainTest.scala` | Multi-core pipeline testing |

### Test Boundary Isolation and sbt `IntegrationTest`

The design enforces strict boundaries between development unit/elaboration testing and golden hardware generation.

#### 1. Sandbox Compilation Path
To prevent unit/smoke testing from overwriting or contaminating standard release-ready verilog outputs, tests and transient compilations **MUST** write to the ignored temporary directory `target/tmp_rtl` rather than `rtl/`:
```scala
SpinalConfig(targetDirectory = "target/tmp_rtl")
```

#### 2. Separated Integration Testing (`src/it`)
Production release validation is situated under sbt's native `IntegrationTest` layout in `src/it/scala`.

- **Release Target Binding**: The integration test suite asserts correctness directly against the finalized compiled files (e.g., `rtl/MyTop.v`), validating actual downstream assets rather than re-compiling live Scala code.
- **Source Synchronicity**: Tests extending `GoldenIntegrationTest` verify that the compiled production Verilog asset actually exists on disk and is strictly newer than any source files in `src/main/scala`:
  ```scala
  class MyTopIntegrationTest extends GoldenIntegrationTest {
    // Verified against rtl/MyTop.v automatically
  }
  ```
- **Execution Command**:
  ```bash
  sbt it:test
  ```

---

## 12. Handle Lifecycle and Fiber Mechanics

```
Handle[T]()    ->   handle.load(signal)   ->   handle.await
(empty)             (signal bound)              (returns signal)
```

### Dependency resolution in this design

```
  TimerPlugin                         PassThroughPlugin / ScalePlugin
  +----------------------+           +----------------------+
  | enableIn.await <-----+----+      | host[SignalSource]    |
  |  v                   |    |      |   .signalOut.await <--+---- blocks
  | TimerCore.build()    |    |      |  v                    |
  |  v                   |    |      | processedOut.load() --+---->
  | countOut.load() -----+----+---->  +----------------------+    |
  | signalOut alias      |           (SignalSource boundary)       |
  +----------------------+                                         |
                                    ComparatorPlugin / HysteresisPlugin
  TopIoExportPlugin                 +----------------------+       |
  +----------------------+    +---> | host[ProcessedSignal] |       |
  | Phase 1: load -------+----+     |   .processedOut.await |       |
  | Phase 2: await ------+----+     |  v                    |       |
  |   host[ThresholdResult]   |     | aboveFlag.load() ----+---->  |
  |   host[EdgeResult]   |    |     +----------------------+    |   |
  +----------------------+    |     (ThresholdResult boundary)   |   |
                              |                                   |   |
                              |     EdgeDetectorPlugin            |   |
                              +---> +----------------------+      |   |
                                    | host[ThresholdResult] |      |   |
                                    |   .aboveFlag.await <--+------+   |
                                    |  v                    |          |
                                    | EdgeDetectorCore.build|          |
                                    |  v                    |          |
                                    | risingEdge.load()     |          |
                                    | fallingEdge.load() ---+--------->|
                                    +----------------------+
```

### Common Deadlock Causes

| Cause | Symptom | Fix |
|-------|---------|-----|
| Missing `load()` | Fiber timeout | Ensure the producing plugin is in `Params.plugins` |
| Phase 2 before Phase 1 | Fiber timeout | Always load inputs first, then await outputs |
| Circular await | Fiber timeout | Break cycle; one plugin must load without awaiting |

---

## 13. Naming Conventions

| Item | Convention | Example |
|------|-----------|---------|
| Registers | `${periphName}_xxxReg` | `timer_countReg` |
| Memories | `${periphName}_xxxBuffer` | `video_lineBuffer` |
| Pipeline stage signals | `${periphName}_xxx` inside named Area | `edgeDetector_rising` |
| Plugin class | `XxxPlugin` | `TimerPlugin` |
| Core object | `XxxCore` | `TimerCore` |
| IO ports | `snake_case` via `setName()` | `above_flag`, `rising_edge` |
| Test class | `XxxCoreTest` | `TimerCoreTest` |
| Harness | `XxxHarness` | `TimerHarness` |

### Why `periphName` prefix?

In flat Verilog, all registers live in one namespace. Without prefixes, `countReg` from
two different cores would collide. The prefix ensures uniqueness:
`timer_countReg`, `comparator_aboveReg`, `edgeDetector_rising`.

---

## 14. Memory and Synthesis Attributes

```scala
val lineBuffer = Mem(UInt(8 bits), 1920)
lineBuffer.setName(s"${periphName}_lineBuffer")
lineBuffer.addAttribute("syn_ramstyle", "block_ram")
```

- **`readSync`**: registered read, maps to BRAM. 1-cycle latency. Preferred.
- **`readAsync`**: combinational read, maps to LUT/distributed RAM.

---

## 15. Stream Protocol Conventions

### Monitoring Taps

```scala
val fired   = pixelStream.fire     // observe without touching ready
val payload = pixelStream.payload
```

### Sink Pattern (absent consumer)

```scala
Try(host[ConsumerPlugin]).toOption match {
  case Some(_) => // consumer drives ready
  case None    => someStream.ready := True  // sink
}
```

---

## 16. Optional Plugins: EdgeDetector and APB

### EdgeDetectorPlugin

Detects rising and falling edges on any `ThresholdResult` output.
Add to `Params.plugins` when edge events are needed:

```scala
def plugins = Seq(
  TimerPlugin(width = timerWidth),
  PassThroughPlugin(),
  ComparatorPlugin(threshold = threshold),  // or HysteresisPlugin(...)
  EdgeDetectorPlugin(),                     // add this
  TopIoExportPlugin()
)
```

`EdgeDetectorPlugin` implements `EdgeResult` and consumes `ThresholdResult`. It works
unchanged regardless of whether `ComparatorPlugin` or `HysteresisPlugin` is in Stage 3.

Outputs: `rising_edge`, `falling_edge`. Latency: 1 cycle from `aboveFlag` transition.

### ApbMonitorPlugin

Exposes timer and comparator state as APB3-accessible read registers.
Add when an APB3 master needs to read peripheral status:

```scala
def plugins = Seq(
  TimerPlugin(width = timerWidth),
  PassThroughPlugin(),
  HysteresisPlugin(loThreshold = 64, hiThreshold = 192),  // or ComparatorPlugin(...)
  ApbMonitorPlugin(),                                       // add this
  TopIoExportPlugin()
)
```

`ApbMonitorPlugin` consumes `ThresholdResult` via `host[ThresholdResult]`, so it works
with either Stage 3 implementation.

Register map:

| Address | Content | Access |
|---------|---------|--------|
| `0x00` | Timer count (zero-extended to 32 bits) | Read |
| `0x04` | Threshold result above flag (bit 0) | Read |

---

## 17. Design Rules Summary

```
+--------------------------------------------------------------+
|  Design Rules                                                |
+--------------------------------------------------------------+
|                                                              |
|  Components only at edges:                                   |
|    - MyTop              (the one real Component)             |
|    - BlackBoxes         (vendor hard-IP, PLLs, etc.)         |
|    - StreamFifoCC       (CDC primitive)                      |
|                                                              |
|  Everything else is FiberPlugin + object XxxCore.build():    |
|    - Core logic in stateless object XxxCore                  |
|    - Plugin wraps core, publishes Handle[T]                  |
|    - Plain case class Io; NOT extends Bundle                 |
|    - No Component hierarchy in the flat design               |
|                                                              |
|  Service traits define stage boundaries:                     |
|    - One trait per meaningful pipeline stage output          |
|    - Plugin implements a trait to publish; consumes to snap  |
|    - host[Trait] not host[ConcretePlugin] in consumers       |
|    - TopIoExportPlugin consumes traits, not concrete types   |
|                                                              |
|  Pipeline stages:                                            |
|    - Use named Areas inside Core.build()                     |
|    - Each stage: new Area { val reg = RegNext(...) init x }  |
|    - Stage names prefix waveform signal names                |
|    - Document latency in Core scaladoc                       |
|                                                              |
|  Cross-plugin wiring:                                        |
|    - Handles resolve via Fiber (order-independent)           |
|    - TopIoExportPlugin is the single IO wiring point         |
|    - Two-phase: load inputs first, then await outputs        |
|    - Try(host[X]).toOption for all optional plugins          |
|    - Case None must drive safe defaults                      |
|                                                              |
|  Naming:                                                     |
|    - periphName prefix on all registers and memories         |
|    - setName() on top IO for clean Verilog port names        |
|                                                              |
|  Testing:                                                    |
|    - Cores tested in isolation via Harness wrappers          |
|    - ElaborationTest validates the full Fiber graph          |
|    - parallelExecution := false (Verilator builds conflict)  |
|                                                              |
+--------------------------------------------------------------+
```

---

## 18. Checklist for Adding a New Feature

- [ ] **Create `XxxCore.scala`**: `object XxxCore` with `case class Io` and `def build()`
  - `periphName` as first parameter
  - `require()` guards for all signal arguments
  - `setName(s"${periphName}_xxxReg")` on every register
  - Named Areas for each pipeline stage (if multi-stage)
  - Scaladoc noting pipeline latency in cycles
- [ ] **Create `XxxPlugin.scala`**: `case class XxxPlugin extends FiberPlugin`
  - Implement the appropriate service trait (e.g. `with ThresholdResult`)
  - Consume the upstream service trait via `host[UpstreamTrait]`
  - Declare all published Handles
  - In `during build`: await inputs, call `XxxCore.build()`, load outputs
- [ ] **Add or update `PipelineTraits.scala`** if a new stage boundary trait is needed
- [ ] **Add to `Params.plugins`** (or document as optional)
- [ ] **Wire in `TopIoExportPlugin`**
  - Phase 1: load inputs
  - Phase 2: await outputs via `host[Trait]`, use `Try(host[X]).toOption`
  - `case None` drives safe defaults
- [ ] **Add IO to `MyTop.io`** (if external ports needed)
  - `setName()` for every new port
- [ ] **Create `XxxHarness.scala`** in `testhelpers/`
- [ ] **Create `XxxCoreTest.scala`** with mandatory test cases:
  - Happy path
  - Boundary values
  - Reset behavior
  - Pipeline latency (wait N cycles after input before asserting output)
- [ ] **Update `ElaborationTest`** with new plugin configurations
- [ ] **Run `sbt test`**: all tests must pass
- [ ] **Run `sbt "runMain mydesign.GenVerilog"`**: verify clean Verilog output
- [ ] **Update `docs/ARCHITECTURE.md`** with the new feature
