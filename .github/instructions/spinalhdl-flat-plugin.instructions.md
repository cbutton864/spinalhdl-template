---
description: "Use when creating, editing, or refactoring SpinalHDL components, FiberPlugins, Cores, simulation tests, or GenVerilog configurations in this workspace."
applyTo: "**/*.scala"
---
# Coding Style Guide for SpinalHDL flat-plugin code

This instruction file guides LLM agents to ensure style and architectural compliance for Scala/SpinalHDL code within the template workspace. See [docs/STYLE_GUIDE.md](docs/STYLE_GUIDE.md) and [docs/ARCHITECTURE.md](docs/ARCHITECTURE.md) for full reference details.

---

## 1. Core Implementation Pattern (Stateless Objects)

Every unit of RTL logic must reside in a stateless `object` inside a `def build` method. **Never** instantiate state or write assignments directly inside a plugin class body.

```scala
import spinal.core._
import spinal.lib._
import mydesign.util.PrefixArea

object MyCore {
  // Plain Scala case class for outputs - NEVER use a direction-oriented Bundle
  case class Io(dataOut: UInt)

  def build(
      periphName: String = "myPeriph",
      width:      Int    = 8,
      inputSignal: UInt   = null
  ): Io = {
    require(inputSignal != null, "inputSignal is mandatory")
    
    // Automatically wraps registers and signals with a deterministic prefix Name-mangler
    val logic = new PrefixArea(periphName) {
      val regVal = Reg(UInt(width bits)) init 0
      
      when(regVal < inputSignal) {
        regVal := regVal + 1
      }
    }

    Io(dataOut = logic.regVal)
  }
}
```

---

## 2. FiberPlugin Pattern

Plugins must only act as configuration wrappers and connection glue between Fiber `Handle` boundaries. Wrap execution hardware within a single `during build new Area` structure.

```scala
import spinal.core._
import spinal.core.fiber._
import spinal.lib.misc.plugin._

// Implement stage boundaries (Traits)
trait MyStageResult {
  val signalOut: Handle[UInt]
}

case class MyPlugin(width: Int = 8, buildEnv: BuildEnv = BuildEnv()) 
    extends FiberPlugin with MyStageResult {

  // Declare all communication Handles at class top-level
  val signalOut: Handle[UInt] = Handle[UInt]()

  val logic = during build new Area {
    // 1. Resolve inputs from upstream plugins
    val sourceVal = host[SignalSource].signalOut.await
    
    // 2. Delegate RTL creation to the stateless Core block
    // Supports physical split-module compilation and hierarchy propagation:
    val core = buildEnv.useHierarchy(pluginDefault = false) match {
      case true =>
        // Wrap logic inside a physical Verilog Component boundary
        buildBlock("MyCoreInstance") {
          MyCore.build(periphName = "mycore", width = width, inputSignal = sourceVal)
        }
      case false =>
        // Inlined flat Area (no physical boundary)
        MyCore.build(periphName = "mycore", width = width, inputSignal = sourceVal)
    }

    // 3. Publish output handles
    signalOut.load(core.dataOut)
  }
}
```

---

## 3. TopIoExportPlugin & Two-Phase Rule

The export plugin couples inner plugin `Handle` objects to physical top-level static IO pads. Follow a strict two-stage process when creating or modifying exports inside [src/main/scala/mydesign/TopIoExportPlugin.scala](src/main/scala/mydesign/TopIoExportPlugin.scala):

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

---

## 4. Simulation Testing Patterns

Do not attempt to test individual plugins. Test the standalone `Core` block by writing a specialized unit `Harness` under `test/scala/`.

```scala
import spinal.core._
import spinal.core.sim._
import org.scalatest.funsuite.AnyFunSuite

// 1. Declare a synthesis wrapper module class
class MyCoreHarness(width: Int) extends Component {
  val io = new Bundle {
    val inputVal = in UInt(width bits)
    val dataOut  = out UInt(width bits)
  }
  // Drive core inside the harness
  val core = MyCore.build(periphName = "test", width = width, inputSignal = io.inputVal)
  io.dataOut := core.dataOut
}

// 2. Setup the scalatest suite
class MyCoreTest extends AnyFunSuite {
  test("verify incremental count") {
    SimConfig.withWave.compile(new MyCoreHarness(8)).doSim { dut =>
      dut.clockDomain.forkStimulus(period = 10)
      
      dut.io.inputVal #= 10
      dut.clockDomain.waitSampling(5)
      
      assert(dut.io.dataOut.toInt > 0)
    }
  }
}
```
