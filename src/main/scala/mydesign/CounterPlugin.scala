package mydesign

import spinal.core._
import spinal.core.fiber._
import spinal.lib._
import spinal.lib.misc.plugin._

/** Plugin: free-running counter with enable gate.
  *
  * Consumes:
  *   - `enableIn: Handle[Bool]` — loaded by TopIoExportPlugin from pad
  *
  * Publishes:
  *   - `countOut: Handle[UInt]` — current counter value
  */
case class CounterPlugin(
    width: Int = 8
) extends FiberPlugin {

  // ── Published Handles ──────────────────────────────────────
  val countOut:  Handle[UInt] = Handle[UInt]()

  // ── Input Handle (loaded by TopIoExportPlugin) ─────────────
  val enableIn:  Handle[Bool] = Handle[Bool]()

  val logic = during build new Area {
    val enable = enableIn.await  // block until TopIoExport loads this

    val core = CounterCore.build(
      periphName = "counter",
      width      = width,
      enable     = enable
    )

    countOut.load(core.count)
  }
}
