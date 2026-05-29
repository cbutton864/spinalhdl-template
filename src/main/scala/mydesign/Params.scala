package mydesign

import spinal.core._
import spinal.core.fiber._
import spinal.lib._
import spinal.lib.misc.plugin._
import mydesign.util._

/** Board / project parameters.
  *
  * Centralises hardware config, build strategy, and the plugin list.
  * Plugin list order is irrelevant — Fiber resolves all dependencies.
  *
  * Build strategy is set once via `buildEnv` and propagates to all plugins:
  *   BuildEnv(FlatBuild)         — flat RTL, maximum synthesis optimisation
  *   BuildEnv(HierarchicalBuild) — component hierarchy, wave tracing, floorplan partitions
  *   BuildEnv(CustomBuild)       — per-plugin defaults (each plugin decides independently)
  */
case class Params(
    // ── Clock ──────────────────────────────────────────────────
    sysClkHz: HertzNumber = 100 MHz,

    // ── Timer ──────────────────────────────────────────────────
    timerWidth: Int = 8,

    // ── Comparator ─────────────────────────────────────────────
    threshold:  Int = 128,

    // ── Build Strategy ─────────────────────────────────────────
    buildEnv: BuildEnv = BuildEnv()
) {
  require(timerWidth >= 1 && timerWidth <= 32,
    s"timerWidth must be 1..32, got $timerWidth")

  require(threshold >= 0,
    s"threshold must be >= 0, got $threshold")

  require(timerWidth >= 32 || threshold < (1 << timerWidth),
    s"threshold ($threshold) doesn't fit in $timerWidth-bit timer (max ${(1 << timerWidth) - 1})")

  /** Plugin list — order does not matter; Fiber resolves dependencies.
    *
    * Stage 2 — swap PassThroughPlugin ↔ ScalePlugin(shift = N)
    * Stage 3 — swap ComparatorPlugin  ↔ HysteresisPlugin(lo = N, hi = M)
    *
    * Optional stages not included by default:
    *   EdgeDetectorPlugin() — Stage 4: rising/falling edge detection
    *   ApbMonitorPlugin()   — side channel: exposes timer/threshold state via APB3
    */
  def plugins: Seq[FiberPlugin] = Seq(
    TimerPlugin(width = timerWidth, buildEnv = buildEnv),            // Stage 1: signal source
    PassThroughPlugin(),                                              // Stage 2: swap ↔ ScalePlugin(shift = 2)
    ComparatorPlugin(threshold = threshold, buildEnv = buildEnv),    // Stage 3: swap ↔ HysteresisPlugin(lo, hi)
    TopIoExportPlugin()
  )
}

/** Pre-built configuration profiles. */
object Params {

  /** Flat build — no component hierarchy, maximum global synthesis optimisation. */
  def productionFlat = Params(
    timerWidth = 8,
    threshold  = 128,
    buildEnv   = BuildEnv(mode = FlatBuild)
  )

  /** Hierarchical build — component boundaries for floorplanning and wave tracing. */
  def debugHierarchical = Params(
    timerWidth = 12,
    threshold  = 512,
    buildEnv   = BuildEnv(mode = HierarchicalBuild)
  )

  /** Composite subsystem build — hierarchical with wider timer for subsystem comparison. */
  def subsystemComposite = Params(
    timerWidth = 16,
    threshold  = 1024,
    buildEnv   = BuildEnv(mode = HierarchicalBuild)
  )
}
