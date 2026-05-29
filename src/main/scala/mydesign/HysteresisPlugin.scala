package mydesign

import spinal.core._
import spinal.core.fiber._
import spinal.lib.misc.plugin._

/** Stage 3 — hysteresis threshold: eliminates chatter near the trip point.
  *
  * Delegates to HysteresisCore for all RTL. Implements ThresholdResult so
  * Stage 4 (EdgeDetectorPlugin) and TopIoExportPlugin consume the result
  * via the trait — swap with ComparatorPlugin freely.
  *
  * Example with 8-bit signal, lo=64, hi=192:
  *   signal rises  to 192+  → aboveFlag goes True
  *   signal between 64–191  → aboveFlag holds its last value
  *   signal falls below 64  → aboveFlag goes False
  *
  * Consumes:
  *   - `host[ProcessedSignal].processedOut` — stage 2 output
  *
  * Publishes:
  *   - `aboveFlag: Handle[Bool]` — latched threshold result
  */
case class HysteresisPlugin(
    loThreshold: Int    = 64,
    hiThreshold: Int    = 192,
    periphName:  String = "hysteresis"
) extends FiberPlugin with ThresholdResult {

  require(loThreshold >= 0,
    s"loThreshold must be >= 0, got $loThreshold")
  require(hiThreshold > loThreshold,
    s"hiThreshold ($hiThreshold) must be > loThreshold ($loThreshold)")

  val aboveFlag: Handle[Bool] = Handle[Bool]()

  val logic = during build new Area {
    val signal = host[ProcessedSignal].processedOut.await

    val core = HysteresisCore.build(
      periphName  = periphName,
      loThreshold = loThreshold,
      hiThreshold = hiThreshold,
      signal      = signal
    )

    aboveFlag.load(core.above)
  }
}
