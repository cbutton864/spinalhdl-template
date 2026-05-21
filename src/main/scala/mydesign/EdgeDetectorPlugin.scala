package mydesign

import spinal.core._
import spinal.core.fiber._
import spinal.lib._
import spinal.lib.misc.plugin._

/** Stage 4 — edge detector on the threshold result.
  *
  * Consumes ThresholdResult so it works after any Stage 3 plugin
  * (ComparatorPlugin or HysteresisPlugin) without modification.
  * Implements EdgeResult so TopIoExportPlugin accesses the output
  * via the trait — a future alternative edge detector can replace
  * this plugin without touching any other file.
  *
  * Consumes:
  *   - `host[ThresholdResult].aboveFlag` — any Stage 3 output
  *
  * Publishes:
  *   - `risingEdge:  Handle[Bool]` — pulses one cycle when aboveFlag goes false → true
  *   - `fallingEdge: Handle[Bool]` — pulses one cycle when aboveFlag goes true → false
  *
  * Pipeline latency: 2 cycles (EdgeDetectorCore has 2 register stages).
  *
  * Requires a ThresholdResult plugin in Params.plugins.
  * Is itself optional — TopIoExportPlugin accesses it via Try(host[EdgeResult]).
  */
case class EdgeDetectorPlugin() extends FiberPlugin with EdgeResult {

  // ── Published Handles ──────────────────────────────────────
  val risingEdge:  Handle[Bool] = Handle[Bool]()
  val fallingEdge: Handle[Bool] = Handle[Bool]()

  val logic = during build new Area {
    val above = host[ThresholdResult].aboveFlag.await

    val core = EdgeDetectorCore.build(
      periphName = "edgeDetector",
      input      = above
    )

    risingEdge.load(core.rising)
    fallingEdge.load(core.falling)
  }
}
