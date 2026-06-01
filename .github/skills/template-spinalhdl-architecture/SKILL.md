---
name: template-spinalhdl-architecture
description: 'How to design high-performance, deadlock-free SpinalHDL designs using stateless Cores and Plugin configurations under the flat-plugin architecture.'
argument-hint: 'create a new stage, decouple plugin from core, add a service boundary'
user-invocable: true
disable-model-invocation: false
---

# SpinalHDL Flat-Plugin Architecture & Design Guide

This skill guides you through implementing or refactoring pipeline stages, separating RTL from Fiber configuration boundaries, and preventing circular elaboration deadlocks.

## When to Use
- Creating a new hardware processing block or conditioning step (Stage).
- Splitting monolithic plugins into decouple-able Cores and configuration wrappers.
- Wiring high-speed hardware nodes over dynamic Fiber boundaries.

## Step-by-Step Procedures

### Procedure 1: Write the Stateless RTL Core
RTL must live in a stateless `object` wrapping logic inside a `def build` method. Use `PrefixArea` to enforce deterministic, prefix-tracked name-mangling in flat outputs:

```scala
package mydesign

import spinal.core._
import spinal.lib._
import mydesign.util.PrefixArea

object FilterCore {
  // Plain Scala case class containing output signals. Do not extend Bundle here.
  case class Io(dataOut: UInt)

  def build(
      periphName: String = "filter",
      width:      Int    = 8,
      inputSig:   UInt   = null
  ): Io = {
    require(inputSig != null, "inputSig must not be null")

    val logic = new PrefixArea(periphName) {
      // Registers will be prefixed inside Verilog netlists: filter_shifterReg
      val shifterReg = Reg(UInt(width bits)) init 0
      shifterReg := (shifterReg >> 1) + inputSig
    }

    Io(dataOut = logic.shifterReg)
  }
}
```

### Procedure 2: Write the Plugin & Output Service Trait
Service traits form decoupled boundary agreements that allow you to swap pipeline steps (such as replacing a comparator with hysteresis logic) without changing downstream consumers:

```scala
package mydesign

import spinal.core._
import spinal.core.fiber._
import spinal.lib.misc.plugin._

// Zero-hardware trait acting as the stable stage boundary
trait FilterResult {
  val filterOut: Handle[UInt]
}

case class FilterPlugin(width: Int = 8, buildEnv: BuildEnv = BuildEnv())
    extends FiberPlugin with FilterResult {

  // Declare high-speed communication Handles
  val filterOut: Handle[UInt] = Handle[UInt]()

  val logic = during build new Area {
    // 1. Resolve upstream dependency traits and wait for their values
    val sourceValue = host[SignalSource].signalOut.await

    // 2. Delegate RTL build, checking hierarchy configurations
    val core = buildEnv.useHierarchy(pluginDefault = false) match {
      case true =>
        // Generate physical component entity block
        buildBlock("FilterSubsystem") {
          FilterCore.build("filter", width, sourceValue)
        }
      case false =>
        // Inline flat registers
        FilterCore.build("filter", width, sourceValue)
    }

    // 3. Load results into the published outputs
    filterOut.load(core.dataOut)
  }
}
```

### Procedure 3: Connect Outputs in TopIoExportPlugin
To export the results onto the static primary PCB pins, integrate the newly introduced pipeline handles inside `TopIoExportPlugin.scala`:
1. **Phase 1 (Input/Control non-blocking loads):** Load enable inputs, reference clocks, or configuration flags first.
2. **Phase 2 (Output blocking awaits):** Safely await the traits output and drive top-level pins.

```scala
// Phase 1: Call .load() for ALL input and control handles (Non-blocking)
Try(host[MyPlugin]).toOption.foreach { plug =>
  plug.someInputHandle.load(top.io.physicalPin)
}

// Phase 2: Call .await for ALL output data handles (Blocking)
Try(host[MyStageResult]).toOption match {
  case Some(stage) => top.io.outPin := stage.signalOut.await
  case None        => top.io.outPin := 0
}
```
* **Critical Design Warning:** Never issue `.await` before all inputs are fully `.load`ed – doing so blocks parent Fiber elaboration and results in compilation lock-up.
