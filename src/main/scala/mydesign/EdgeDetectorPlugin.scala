package mydesign

import spinal.core._
import spinal.core.fiber._
import spinal.lib._
import spinal.lib.misc.plugin._
import mydesign.util._

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
case class EdgeDetectorPlugin(
    periphName: String   = "edgeDetector",
    buildEnv:   BuildEnv = BuildEnv()
) extends FiberPlugin with EdgeResult {

  val risingEdge:  Handle[Bool] = Handle[Bool]()
  val fallingEdge: Handle[Bool] = Handle[Bool]()

  val logic = during build new Area {
    val above = host[ThresholdResult].aboveFlag.await
    val hier  = buildEnv.useHierarchy(false)

    val (rawRising, rawFalling) = BuildHelper.buildSubsystem(hier, s"${periphName}_EdgeSub") {
      val pulledAbove = BuildHelper.autoPull(above, hier)
      val core = EdgeDetectorCore.build(periphName = periphName, input = pulledAbove)
      (core.rising, core.falling)
    }

    risingEdge.load(BuildHelper.autoPull(rawRising,  hier))
    fallingEdge.load(BuildHelper.autoPull(rawFalling, hier))
  }
}
