# How This Template Works

This template exists to solve one problem: hardware designs that are hard to reconfigure.
In a standard SpinalHDL project, adding or removing a feature means editing glue code across many files.
In this pattern, you add or remove features by changing one list in one file.

The tradeoff is that the pattern has real indirection. This guide exists to close that gap.

---

## The Three Building Blocks

Every hardware module in this pattern has exactly three parts.

**The Core** holds the RTL. It is a Scala `object` with a `build()` method. Call it, get hardware back.
It has no knowledge of the system around it.

**The Plugin** holds the wiring. It connects the Core to the rest of the design. It has no RTL.

**Params** holds the list. It decides which Plugins exist and what settings they get.

That division is the whole pattern. RTL in Cores. Wiring in Plugins. Nothing else matters until you know that.

---

## Cores

```scala
object TimerCore {
  case class Io(count: UInt)

  def build(periphName: String = "timer", width: Int = 8, enable: Bool = null): Io = {
    require(enable != null, "enable signal is required")
    val logic = new PrefixArea(periphName) {
      val countReg = Reg(UInt(width bits)) init 0
      when(enable) { countReg := countReg + 1 }
    }
    Io(count = logic.countReg)
  }
}
```

Three things to notice.

**It is an `object`, not a class.** No state lives on the Core itself. All state is in the hardware nodes
that `build()` creates.

**It returns a `case class Io`, not a Bundle.** This is a plain Scala container for hardware references.
It carries no direction semantics and cannot cause wiring errors. A Bundle is for Component ports.
This is not that.

**Registers go inside `PrefixArea(periphName)`.** This gives every register a unique name in the flat
generated Verilog. Without the prefix, two Cores both defining `countReg` would collide in one module.
With it, you get `timer_countReg` and `comparator_aboveReg` and they never conflict.

---

## Plugins

```scala
case class TimerPlugin(width: Int = 8) extends FiberPlugin with SignalSource {
  val countOut: Handle[UInt] = Handle[UInt]()
  val signalOut: Handle[UInt] = countOut   // satisfies SignalSource; same Handle, two names
  val enableIn: Handle[Bool]  = Handle[Bool]()

  val logic = during build new Area {
    val enable = enableIn.await
    val core   = TimerCore.build(periphName = "timer", width = width, enable = enable)
    countOut.load(core.count)
  }
}
```

A Plugin has two sections, and the boundary between them is strict.

**Outside `during build`:** only Handle declarations. No RTL here, ever.

**Inside `during build`:** everything else. Await inputs, call the Core, load outputs.

If you write `Reg()` or `when()` outside `during build`, it will crash at elaboration time.
There is no Component context at that point. The hardware graph does not exist yet.

---

## Handles: The Communication Primitive

A `Handle[T]` is a typed placeholder for a hardware signal.

Think of it as a promise. The plugin that produces a signal calls `.load(value)` to fulfill the promise.
Any plugin that needs the signal calls `.await` to block until the promise is fulfilled.

```scala
// Producer
val countOut: Handle[UInt] = Handle[UInt]()
countOut.load(core.count)   // fulfills the promise

// Consumer
val count = host[TimerPlugin].countOut.await   // waits for the promise
```

The Fiber system runs all `during build` blocks and resolves the Handle graph automatically.
The order of plugins in the `Params` list does not matter. Fiber figures out the correct execution
order from the load and await relationships.

---

## The Two-Phase Rule

This is the most important rule. Get it wrong and you get a silent Fiber deadlock.

The `TopIoExportPlugin` connects physical pad signals to plugin Handles. It runs in two phases.

**Phase 1: load all inputs.** Push pad signals into plugin Handles. No `.await` calls here.

**Phase 2: await all outputs.** Read plugin Handle values and drive pads. No `.load` calls here.

```scala
val logic = during build new Area {

  // Phase 1: load inputs (non-blocking)
  Try(host[TimerPlugin]).toOption.foreach { timer =>
    timer.enableIn.load(top.io.enable)
  }

  // Phase 2: await outputs and drive pads (blocking)
  Try(host[TimerPlugin]).toOption match {
    case Some(timer) => top.io.count := timer.countOut.await
    case None        => top.io.count := 0
  }

  Try(host[ThresholdResult]).toOption match {
    case Some(thresh) => top.io.aboveFlag := thresh.aboveFlag.await
    case None         => top.io.aboveFlag := False
  }
}
```

### Why this order is required

If you call `.await` before loading inputs, here is what happens step by step.

`TopIoExportPlugin` blocks waiting for a plugin's output Handle. That plugin is blocked waiting
for its input Handle to be loaded. The input Handle has not been loaded yet because Phase 1 is
not done. Nothing moves. The Fiber system times out with a cryptic error.

The fix is always the same: complete all `.load` calls before any `.await` call.

---

## Service Traits: Swappable Stage Boundaries

Plugins do not depend on each other by name. They depend on traits.

```scala
// The stage contract
trait ProcessedSignal {
  val processedOut: Handle[UInt]
}

// PassThroughPlugin satisfies it
case class PassThroughPlugin() extends FiberPlugin with ProcessedSignal {
  val processedOut: Handle[UInt] = Handle[UInt]()
  ...
}

// ScalePlugin also satisfies it
case class ScalePlugin(shift: Int = 2) extends FiberPlugin with ProcessedSignal {
  val processedOut: Handle[UInt] = Handle[UInt]()
  ...
}

// ComparatorPlugin consumes it
case class ComparatorPlugin(threshold: Int = 128) extends FiberPlugin {
  val logic = during build new Area {
    val count = host[ProcessedSignal].processedOut.await
    ...
  }
}
```

`ComparatorPlugin` does not know whether `PassThroughPlugin` or `ScalePlugin` is in the system.
It knows only that something satisfies `ProcessedSignal`. To swap Stage 2, change one line in `Params`.
Nothing downstream changes.

```scala
// Before
PassThroughPlugin()

// After: ComparatorPlugin, EdgeDetectorPlugin, TopIoExportPlugin all unchanged
ScalePlugin(shift = 2)
```

Define a new trait when a stage boundary is stable and named. One trait per real pipeline
boundary is enough. Do not wrap every signal in a trait.

---

## Optional Plugins

Some plugins are not always present. Use `Try(host[X]).toOption` to handle this safely.

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

Always provide a safe constant in the `None` branch. An undriven output port causes a synthesis error.

Do not call `host[X]` directly for a plugin that might be absent. It throws at elaboration time.

---

## BuildEnv: One Source, Two Outputs

By default this template generates flat Verilog. One module, all signals in one namespace.
Flat Verilog is better for timing closure because the synthesis tool sees everything at once.

For debugging, the same source code can generate hierarchical Verilog. Each plugin wraps its Core
in a named module boundary. Signal names are scoped, waveforms are organized, schematics are readable.

`BuildEnv` controls this globally. Plugins check `buildEnv.useHierarchy(false)` and pass that
result to `BuildHelper.buildBlock`. You change the mode in `Params`. You do not touch individual plugins.

```scala
// Both generated from the same Scala source
SpinalConfig(...).generateVerilog(new MyTop(Params.productionFlat))
SpinalConfig(...).generateVerilog(new MyTop(Params.debugHierarchical))
```

---

## Params and the Plugin List

```scala
case class Params(
    timerWidth: Int       = 8,
    threshold:  Int       = 128,
    buildEnv:   BuildEnv  = BuildEnv()
) {
  def plugins: Seq[FiberPlugin] = Seq(
    TimerPlugin(width = timerWidth, buildEnv = buildEnv),
    PassThroughPlugin(),       // swap for ScalePlugin(shift = 2)
    ComparatorPlugin(threshold = threshold),
    TopIoExportPlugin()
  )
}
```

`plugins` is a `def`, not a `val`. This matters for testing. Each call to `Params().plugins` creates
fresh plugin instances. If it were a `val`, the same objects would be reused across test runs and
accumulate stale state.

Plugin list order does not matter for correctness. Fiber resolves dependencies from the load and await
graph. By convention, put `TopIoExportPlugin` last so the list reads features first, wiring last.

---

## How to Read This Code

**Start at `Params.scala`.** That file tells you what is in the system and what settings it uses.

**Read the Core for a module** to understand what the hardware actually does.

**Read the Plugin only** if you need to understand how it connects to the rest of the design.

**Read `TopIoExportPlugin` last**, only if you are tracing a specific pad signal.

The test harnesses in `src/test/scala/testhelpers/` are the fastest way to understand what
a Core does. Each one wraps a single Core in a minimal Component with no plugin machinery.

---

## How to Add a New Module

1. Write the Core object. Give all registers a `periphName` prefix. Add `require()` guards for signals.
2. Test the Core in isolation with a harness before wiring it into the system.
3. Write a service trait if other plugins need to consume its output.
4. Write the Plugin. Declare Handles outside `during build`. Call the Core inside `during build`.
5. Add the Plugin to `Params.plugins()`.
6. In `TopIoExportPlugin`, load inputs in Phase 1 and await outputs in Phase 2.
7. Add any new ports to `MyTop.scala` with `setName()` calls.

---

## Common Mistakes

**RTL outside `during build`**
Putting `Reg()`, `when()`, or signal assignments in the Plugin class body causes a crash.
There is no Component context there. Move all RTL inside `during build`.

**Calling `.await` in Phase 1 of `TopIoExportPlugin`**
This causes a Fiber deadlock. The error message will not say deadlock. It will say timeout.
Move all `.await` calls to Phase 2, after all `.load` calls are done.

**Missing plugin in `Params.plugins()`**
A plugin awaits a Handle that is never loaded. Fiber times out. The fix is to add the missing plugin
to the list, or remove the `await` if the dependency is no longer needed.

**Returning a `Bundle` from a Core**
Cores return `case class Io`, not `Bundle`. A Bundle carries direction semantics that the Core does
not need and that will cause errors at the plugin boundary.

**Using `host[X]` for an optional plugin**
If the plugin is absent, `host[X]` throws at elaboration time. Use `Try(host[X]).toOption` instead.

**Using `val plugins` instead of `def plugins` in Params**
A `val` shares plugin instances across test runs. Tests will fail non-deterministically as stale
state accumulates. Always use `def`.
