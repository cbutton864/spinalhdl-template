package mydesign

import spinal.core._
import spinal.lib._
import mydesign.util.PrefixArea

/** Bus-agnostic free-running timer core.
  *
  * Increments on each clock when `enable` is asserted.
  * Wraps at `(1 << width) - 1`.
  *
  * Core pattern: stateless `object` with `build()` method.
  * Returns a plain Scala `case class Io` — NOT a Bundle.
  * All signals are top-level peers (no Component hierarchy).
  */
object TimerCore {

  /** Plain Scala case class — NOT a Bundle.
    * Fields are references to signals created by `build()`.
    */
  case class Io(
      count: UInt
  )

  def build(
      periphName: String = "timer",
      width:      Int    = 8,
      enable:     Bool   = null
  ): Io = {
    require(enable != null, "enable signal is required")
    require(width >= 1,     s"width must be >= 1, got $width")

    val logic = new PrefixArea(periphName) {
      val countReg = Reg(UInt(width bits)) init 0

      when(enable) {
        countReg := countReg + 1
      }
    }

    Io(count = logic.countReg)
  }
}
