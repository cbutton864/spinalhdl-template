package mydesign

import spinal.core._
import spinal.core.fiber._
import spinal.lib._
import spinal.lib.misc.plugin._

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
    width: Int = 8
) extends FiberPlugin with SignalSource {

  // ── Published Handles ──────────────────────────────────────
  val countOut:  Handle[UInt] = Handle[UInt]()
  val signalOut: Handle[UInt] = countOut   // SignalSource boundary — same Handle, two names

  // ── Input Handle (loaded by TopIoExportPlugin) ─────────────
  val enableIn: Handle[Bool] = Handle[Bool]()

  val logic = during build new Area {
    val enable = enableIn.await  // block until TopIoExport loads this

    val core = TimerCore.build(
      periphName = "timer",
      width      = width,
      enable     = enable
    )

    countOut.load(core.count)
  }
}
