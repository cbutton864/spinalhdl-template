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

/** Companion object offering pre-built configuration profiles demonstrating different designs.
  */
object Params {
  // 1. Single massive flat block optimization for tapeout area/timing performance
  def productionFlat = Params(
    timerWidth      = 8,
    threshold       = 128,
    globalHierarchy = Some(false)
  )

  // 2. Hierarchical blocks with debug counter sizes for timing partitions and wave tracing
  def debugHierarchical = Params(
    timerWidth      = 12,
    threshold       = 512,
    globalHierarchy = Some(true)
  )

  // 3. Composite Subsystem config swapping standard plugins for dynamic nested subsystems
  def subsystemComposite = Params(
    timerWidth      = 16,
    threshold       = 1024,
    globalHierarchy = Some(true)
  )
}
