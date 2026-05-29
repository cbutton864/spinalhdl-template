package mydesign

import spinal.core._
import mydesign.util.PrefixArea

/** Bus-agnostic hysteresis threshold core.
  *
  * Eliminates chatter near a trip point using dual thresholds:
  *   - Latches high when signal >= hiThreshold
  *   - Latches low  when signal <  loThreshold
  *   - Holds last value when signal is in [loThreshold, hiThreshold)
  *
  * Pipeline latency: 1 clock cycle (single RegInit stage).
  *
  * Core pattern: stateless `object` with `build()` method.
  * Returns a plain Scala `case class Io` — NOT a Bundle.
  */
object HysteresisCore {

  /** Plain Scala case class — NOT a Bundle.
    * Fields are references to signals created by `build()`.
    */
  case class Io(above: Bool)

  def build(
      periphName:  String = "hysteresis",
      loThreshold: Int    = 64,
      hiThreshold: Int    = 192,
      signal:      UInt   = null
  ): Io = {
    require(signal      != null,           "signal is required")
    require(loThreshold >= 0,              s"loThreshold must be >= 0, got $loThreshold")
    require(hiThreshold > loThreshold,     s"hiThreshold ($hiThreshold) must be > loThreshold ($loThreshold)")
    require(signal.getWidth >= 32 || hiThreshold < (1 << signal.getWidth),
      s"hiThreshold ($hiThreshold) doesn't fit in ${signal.getWidth}-bit signal (max ${(1 << signal.getWidth) - 1})")

    val logic = new PrefixArea(periphName) {
      val aboveReg = RegInit(False)

      when(signal >= U(hiThreshold)) { aboveReg := True  }
      when(signal <  U(loThreshold)) { aboveReg := False }
    }

    Io(above = logic.aboveReg)
  }
}
