package mydesign

import spinal.core._
import spinal.core.fiber._
import spinal.lib._
import spinal.lib.misc.plugin._
import mydesign.util._

/** DualPipelineTop is a production architecture example which instantiates:
  * 1. A standard pipeline (Pipeline A) — build mode controlled by buildEnv
  * 2. An identical pipeline (Pipeline B) wrapped inside a composite subsystem plugin
  *
  * Allows a direct side-by-side comparison of flat vs composite hierarchy in the
  * generated Verilog. Pass BuildEnv(FlatBuild) for fully flat output or
  * BuildEnv(HierarchicalBuild) for full component hierarchy.
  */
class DualPipelineTop(buildEnv: BuildEnv = BuildEnv(mode = HierarchicalBuild)) extends Component {
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

  val parentHost = new PluginHost()

  // Pipeline A — build mode from buildEnv
  val timerA = TimerPlugin(width = 8, buildEnv = buildEnv, periphName = "timerA")
  val passA  = PassThroughPlugin()
  val compA  = ComparatorPlugin(threshold = 128, periphName = "comparatorA")

  val ioExportA = new FiberPlugin {
    val logic = during build new Area {
      timerA.enableIn.load(io.enable)
      io.countA := timerA.countOut.await
      io.flagA  := compA.aboveFlag.await
    }
  }

  // Pipeline B — composite subsystem, build mode from buildEnv
  val subSystemB = PipelineSubsystemPlugin(
    width         = 8,
    threshold     = 128,
    buildEnv      = buildEnv,
    subsystemName = "PipelineBSubsystem"
  )

  val ioExportB = new FiberPlugin {
    val logic = during build new Area {
      subSystemB.enableIn.load(io.enable)
      io.countB := subSystemB.countOut.await
      io.flagB  := subSystemB.flagOut.await
    }
  }

  parentHost.asHostOf(Seq(
    timerA, passA, compA, ioExportA,
    subSystemB, ioExportB
  ))
}
