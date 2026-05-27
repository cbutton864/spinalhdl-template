package mydesign

import org.scalatest.funsuite.AnyFunSuite
import spinal.core._
import spinal.core.fiber._
import spinal.lib._
import spinal.lib.misc.plugin._
import mydesign.util.BuildHelper

/** PipelineBSubsystemPlugin acts as a composite wrapper plugin.
  * It groups multiple underlying peripheral logic cores (Timer, PassThrough, Comparator)
  * and uses BuildHelper.buildSubsystem to package them in a single physical component if hierarchical is enabled,
  * completely avoiding nested PluginHost deadlocks while reusing the shared top-level host.
  */
case class PipelineBSubsystemPlugin(
    width: Int = 8,
    threshold: Int = 128,
    hierarchical: Boolean = true,
    hierarchicalTimer: Boolean = true, // individual nested hierarchical flag
    subsystemName: String = "PipelineBSubsystem",
    periphName: String = "timerB"
) extends FiberPlugin {

  val enableIn:  Handle[Bool] = Handle[Bool]()
  val countOut:  Handle[UInt] = Handle[UInt]()
  val flagOut:   Handle[Bool] = Handle[Bool]()

  val logic = during build new Area {
    val enableRaw = enableIn.await

    // We reuse our BuildHelper to construct the physical boundary!
    BuildHelper.buildSubsystem(hierarchical, subsystemName) {
      // Pull inputs if we are in a separate physical component (pulls from parent to subComp)
      val pulledEnable = if (hierarchical) enableRaw.pull() else enableRaw
      pulledEnable.setName("sub_enable")

      // 1. Build Timer Core conditionally inside its own sub-component using buildBlock!
      val timerCount = BuildHelper.buildBlock(HardType(UInt(width bits)), hierarchicalTimer, s"${periphName}_TimerCoreSub") { outSig =>
        val timerEnable = if (hierarchicalTimer) pulledEnable.pull() else pulledEnable
        val core = TimerCore.build(
          periphName = periphName,
          width      = width,
          enable     = timerEnable
        )
        outSig := core.count
      }

      // 2. Stage 2 - PassThrough (identity)
      val processed = timerCount

      // 3. Build Comparator Core
      val comparator = ComparatorCore.build(
        periphName = "comparatorB",
        threshold  = threshold,
        countIn    = processed
      )

      // Load outputs (we load the child component's internal signals directly; 
      // the parent/client will pull them across the boundary)
      timerCount.setName("sub_timer_count")
      comparator.above.setName("sub_comparator_above")

      countOut.load(timerCount)
      flagOut.load(comparator.above)
    }
  }
}

/** DualPipelineTop is a testing architecture which instantiates:
  * 1. A FLAT standard pipeline (Pipeline A)
  * 2. An identical hierarchical pipeline (Pipeline B) wrapped inside a single composite plugin using BuildHelper.
  *
  * This allows a direct side-by-side comparison of flat vs composite hierarchy in the generated Verilog!
  */
class DualPipelineTop(hierarchicalB: Boolean = true) extends Component {
  val io = new Bundle {
    val enable = in Bool()
    val countA = out UInt(8 bits)
    val flagA  = out Bool()
    val countB = out UInt(8 bits)
    val flagB  = out Bool()
  }
  io.enable.setName("enable")
  io.countA.setName("countA")
  io.flagA.setName("flagA")
  io.countB.setName("countB")
  io.flagB.setName("flagB")

  // --- Pipeline A: Standard Flat Plugins ---
  val parentHost = new PluginHost()

  // We define standard plugins for Pipeline A, but set timerA to be hierarchical!
  val timerA = TimerPlugin(width = 8, hierarchical = true, periphName = "timerA")
  val passA  = PassThroughPlugin()
  val compA  = ComparatorPlugin(threshold = 128, periphName = "comparatorA")
  
  // Custom simple IO export plugin for Pipeline A
  case class IoExportA() extends FiberPlugin {
    val logic = during build new Area {
      timerA.enableIn.load(io.enable)
      io.countA := timerA.countOut.await
      io.flagA  := compA.aboveFlag.await
    }
  }

  // --- Pipeline B: Composite Grouped Subsystem ---
  val subSystemB = PipelineBSubsystemPlugin(
    width        = 8,
    threshold    = 128,
    hierarchical = hierarchicalB,
    subsystemName = "PipelineBSubsystem"
  )
  
  case class IoExportB() extends FiberPlugin {
    val logic = during build new Area {
      subSystemB.enableIn.load(io.enable)
      
      val countBWire = UInt(8 bits)
      val flagBWire  = Bool()
      countBWire.setName("sub_countB_out")
      flagBWire.setName("sub_flagB_out")
      
      countBWire := (if (hierarchicalB) subSystemB.countOut.await.pull() else subSystemB.countOut.await)
      flagBWire  := (if (hierarchicalB) subSystemB.flagOut.await.pull() else subSystemB.flagOut.await)
      
      io.countB := countBWire
      io.flagB  := flagBWire
    }
  }

  // Register everything to the parent host
  parentHost.asHostOf(Seq(
    timerA, passA, compA, IoExportA(),
    subSystemB, IoExportB()
  ))
}

class SubsystemCompositeTest extends AnyFunSuite {
  test("Compile dual pipelines and review side-by-side flat vs composite structure") {
    val config = SpinalConfig(
      targetDirectory              = "rtl/subsystem_compare",
      defaultClockDomainFrequency  = FixedFrequency(100 MHz),
      defaultConfigForClockDomains = ClockDomainConfig(
        resetKind        = ASYNC,
        resetActiveLevel = HIGH
      )
    )

    println("Generating Dual Pipeline Verilog with composite configuration...")
    config.generateVerilog(new DualPipelineTop(hierarchicalB = true))
  }
}
