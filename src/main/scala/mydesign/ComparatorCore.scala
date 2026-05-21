package mydesign

import spinal.core._
import spinal.lib._

/** Bus-agnostic threshold comparator core.
  *
  * Outputs a registered flag: true when `countIn >= threshold`.
  *
  * Core pattern: stateless `object` with `build()` method.
  * Returns a plain Scala `case class Io` — NOT a Bundle.
  */
object ComparatorCore {

  /** Plain Scala case class — NOT a Bundle.
    * Fields are references to signals created by `build()`.
    */
  case class Io(
      above: Bool
  )

  def build(
      periphName: String = "comparator",
      threshold:  Int    = 128,
      countIn:    UInt   = null
  ): Io = {
    require(countIn != null, "countIn signal is required")

    val aboveReg = RegInit(False)
    aboveReg.setName(s"${periphName}_aboveReg")

    aboveReg := countIn >= threshold

    Io(above = aboveReg)
  }
}
