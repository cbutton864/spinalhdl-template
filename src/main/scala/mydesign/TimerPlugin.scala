package mydesign

import spinal.core._
import spinal.core.fiber._
import spinal.lib._
import spinal.lib.misc.plugin._
import mydesign.util._

/** Stage 1 — free-running timer with enable gate.
  *
  * Implements SignalSource so Stage 2 plugins (PassThroughPlugin,
  * ScalePlugin) can consume the raw count without naming this
  * concrete type.
  *
  * Consumes:
  *   - `enableIn: Handle[Bool]` — loaded by TopIoExportPlugin from pad
  *
  * Publishes:
  *   - `countOut:  Handle[UInt]` — current timer value (concrete name)
  *   - `signalOut: Handle[UInt]` — same Handle; satisfies SignalSource
  *
  * Build mode is controlled by `buildEnv`. In FlatBuild the timer
  * core is flat (no sub-component). In HierarchicalBuild it is wrapped in a
  * named Component boundary for floorplanning and wave tracing.
  */
case class TimerPlugin(
    width:      Int      = 8,
    buildEnv:   BuildEnv = BuildEnv(),
    periphName: String   = "timer"
) extends FiberPlugin with SignalSource {

  val countOut:  Handle[UInt] = Handle[UInt]()
  val signalOut: Handle[UInt] = countOut   // SignalSource boundary — same Handle, two names
  val enableIn:  Handle[Bool] = Handle[Bool]()

  val logic = during build new Area {
    val enableRaw = enableIn.await

    val enable = Bool()
    enable := enableRaw

    val timerCount = BuildHelper.buildBlock(
      HardType(UInt(width bits)),
      buildEnv.useHierarchy(false),
      s"${periphName}_TimerSub",
      enable
    ) { pulledEnable => outSig =>
      val core = TimerCore.build(periphName = periphName, width = width, enable = pulledEnable)
      outSig := core.count
    }

    countOut.load(timerCount)
  }
}
