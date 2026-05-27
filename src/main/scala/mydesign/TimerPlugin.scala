package mydesign

import spinal.core._
import spinal.core.fiber._
import spinal.lib._
import spinal.lib.misc.plugin._
import mydesign.util.BuildHelper

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
  */
case class TimerPlugin(
    width: Int = 8,
    hierarchical: Boolean = false,
    periphName: String = "timer"
) extends FiberPlugin with SignalSource {

  // ── Published Handles ──────────────────────────────────────
  val countOut:  Handle[UInt] = Handle[UInt]()
  val signalOut: Handle[UInt] = countOut   // SignalSource boundary — same Handle, two names

  // ── Input Handle (loaded by TopIoExportPlugin) ─────────────
  val enableIn: Handle[Bool] = Handle[Bool]()

  val logic = during build new Area {
    val enableRaw = enableIn.await  // block until TopIoExport loads this
    
    // Create an intermediate directionless wire in the parent context
    val enable = Bool()
    enable := enableRaw

    // Conditional hierarchy using our buildBlock helper with automated input pulling!
    val timerCount = BuildHelper.buildBlock(HardType(UInt(width bits)), hierarchical, s"${periphName}_TimerSub", enable) { pulledEnable => outSig =>
      val core = TimerCore.build(
        periphName = periphName,
        width      = width,
        enable     = pulledEnable
      )
      outSig := core.count
    }

    countOut.load(timerCount)
  }
}
