package mydesign

import spinal.core._
import spinal.core.fiber._
import spinal.lib._
import spinal.lib.misc.plugin._

/** Board / project parameters.
  *
  * Centralises hardware config and the plugin list.
  * Plugin list order is irrelevant — Fiber resolves all dependencies.
  */
case class Params(
    // ── Clock ──────────────────────────────────────────────────
    sysClkHz: HertzNumber = 100 MHz,

    // ── Example parameters ────────────────────────────────────
    counterWidth: Int = 8,
    threshold:    Int = 128
) {
  require(counterWidth >= 1 && counterWidth <= 32,
    s"counterWidth must be 1..32, got $counterWidth")

  /** Plugin list — order does not matter; Fiber resolves dependencies. */
  def plugins: Seq[FiberPlugin] = Seq(
    CounterPlugin(width = counterWidth),
    ThresholdPlugin(threshold = threshold),
    TopIoExportPlugin()
  )
}
