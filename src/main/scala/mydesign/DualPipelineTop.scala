package mydesign

import spinal.core._
import spinal.core.fiber._
import spinal.lib._
import spinal.lib.misc.plugin._
import mydesign.util._

/** Hardware parameters for DualPipelineTop.
  *
  * Pipelines A and B may have independent widths and thresholds.
  * A single `buildEnv` controls the hierarchy strategy for both.
  */
case class DualPipelineParams(
    widthA:     Int      = 8,
    thresholdA: Int      = 128,
    widthB:     Int      = 8,
    thresholdB: Int      = 128,
    buildEnv:   BuildEnv = BuildEnv(mode = HierarchicalBuild)
) {
  require(widthA >= 1 && widthA <= 32, s"widthA must be 1..32, got $widthA")
  require(thresholdA >= 0, s"thresholdA must be >= 0, got $thresholdA")
  require(widthA >= 32 || thresholdA < (1 << widthA),
    s"thresholdA ($thresholdA) doesn't fit in $widthA-bit timer (max ${(1 << widthA) - 1})")

  require(widthB >= 1 && widthB <= 32, s"widthB must be 1..32, got $widthB")
  require(thresholdB >= 0, s"thresholdB must be >= 0, got $thresholdB")
  require(widthB >= 32 || thresholdB < (1 << widthB),
    s"thresholdB ($thresholdB) doesn't fit in $widthB-bit timer (max ${(1 << widthB) - 1})")
}

/** Side-by-side demonstration of flat vs composite hierarchy.
  *
  * Pipeline A — standard three-stage pipeline (Timer → PassThrough → Comparator).
  * Pipeline B — same logic wrapped inside a PipelineSubsystemPlugin composite boundary.
  *
  * Pass DualPipelineParams(buildEnv = BuildEnv(FlatBuild)) for fully flat output or
  * DualPipelineParams(buildEnv = BuildEnv(HierarchicalBuild)) for full component hierarchy.
  */
class DualPipelineTop(params: DualPipelineParams = DualPipelineParams()) extends Component {
  val io = new Bundle {
    val enable = in  Bool()
    val countA = out UInt(params.widthA bits)
    val flagA  = out Bool()
    val countB = out UInt(params.widthB bits)
    val flagB  = out Bool()
  }
  io.enable.setName("enable")
  io.countA.setName("countA")
  io.flagA.setName("flagA")
  io.countB.setName("countB")
  io.flagB.setName("flagB")

  val parentHost = new PluginHost()

  // Pipeline A — standard flat or hierarchical pipeline
  val timerA = TimerPlugin(width = params.widthA, buildEnv = params.buildEnv, periphName = "timerA")
  val passA  = PassThroughPlugin()
  val compA  = ComparatorPlugin(threshold = params.thresholdA, periphName = "comparatorA", buildEnv = params.buildEnv)

  val ioExportA = new FiberPlugin {
    val logic = during build new Area {
      timerA.enableIn.load(io.enable)
      io.countA := timerA.countOut.await
      io.flagA  := compA.aboveFlag.await
    }
  }

  // Pipeline B — composite subsystem boundary
  val subSystemB = PipelineSubsystemPlugin(
    width         = params.widthB,
    threshold     = params.thresholdB,
    buildEnv      = params.buildEnv,
    subsystemName = "PipelineBSubsystem",
    periphName    = "timerB"
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
