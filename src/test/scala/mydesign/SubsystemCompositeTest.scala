package mydesign

import org.scalatest.funsuite.AnyFunSuite
import spinal.core._
import spinal.core.fiber._
import spinal.lib._
import spinal.lib.misc.plugin._
import mydesign.util.BuildHelper

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
  val subSystemB = PipelineSubsystemPlugin(
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
      
      countBWire := BuildHelper.autoPull(subSystemB.countOut.await, hierarchicalB)
      flagBWire  := BuildHelper.autoPull(subSystemB.flagOut.await, hierarchicalB)
      
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
      oneFilePerComponent          = true,
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
