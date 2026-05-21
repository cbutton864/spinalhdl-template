package mydesign

import spinal.core._
import spinal.core.fiber._
import spinal.lib.misc.plugin._

/** Stage 3 — hysteresis threshold: eliminates chatter near the trip point.
  *
  * Latches high when signal >= hiThreshold.
  * Latches low  when signal <  loThreshold.
  * Output is unchanged while signal is between the two thresholds.
  *
  * Implements ThresholdResult — swap with ComparatorPlugin for a
  * simple level compare without touching Stage 2 or Stage 4.
  *
  * Example with 8-bit signal, lo=64, hi=192:
  *   signal rises  past 192 → aboveFlag goes True
  *   signal between 64–192  → aboveFlag holds its last value
  *   signal falls below  64 → aboveFlag goes False
  */
case class HysteresisPlugin(
    loThreshold: Int = 64,
    hiThreshold: Int = 192
) extends FiberPlugin with ThresholdResult {

  require(loThreshold >= 0,
    s"loThreshold must be >= 0, got $loThreshold")
  require(hiThreshold > loThreshold,
    s"hiThreshold ($hiThreshold) must be > loThreshold ($loThreshold)")

  val aboveFlag: Handle[Bool] = Handle[Bool]()

  val logic = during build new Area {
    val signal = host[ProcessedSignal].processedOut.await

    val aboveReg = RegInit(False)
    aboveReg.setName("hysteresis_aboveReg")

    when(signal >= U(hiThreshold)) { aboveReg := True  }
    when(signal <  U(loThreshold)) { aboveReg := False }

    aboveFlag.load(aboveReg)
  }
}
