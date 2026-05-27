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

    // ── Timer ──────────────────────────────────────────────────
    timerWidth: Int = 8,

    // ── Comparator ─────────────────────────────────────────────
    threshold:  Int = 128,

    // ── Hierarchy Configuration ────────────────────────────────
    globalHierarchy: Option[Boolean] = None
) {
  require(timerWidth >= 1 && timerWidth <= 32,
    s"timerWidth must be 1..32, got $timerWidth")

  require(threshold >= 0,
    s"threshold must be >= 0, got $threshold")

  /** Default plugin list — order does not matter; Fiber resolves dependencies.
    *
    * Stage 2 — swap PassThroughPlugin ↔ ScalePlugin(shift = N)
    * Stage 3 — swap ComparatorPlugin  ↔ HysteresisPlugin(lo = N, hi = M)
    *
    * Optional stages not included by default:
    *   EdgeDetectorPlugin() — Stage 4: rising/falling edge detection
    *   ApbMonitorPlugin()   — side channel: exposes timer/threshold state via APB3
    */
  def plugins: Seq[FiberPlugin] = Seq(
    TimerPlugin(width = timerWidth, hierarchical = globalHierarchy.getOrElse(false)), // Stage 1: signal source
    PassThroughPlugin(),                        // Stage 2: swap ↔ ScalePlugin(shift = 2)
    ComparatorPlugin(threshold = threshold),    // Stage 3: swap ↔ HysteresisPlugin(lo = 64, hi = 192)
    TopIoExportPlugin()
  )
}
