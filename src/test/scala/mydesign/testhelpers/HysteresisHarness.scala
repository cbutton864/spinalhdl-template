package mydesign.testhelpers

import spinal.core._
import mydesign.HysteresisCore

/** Simulation wrapper: wraps HysteresisCore.build() in a Component.
  *
  * Cores are stateless objects with no Component boundary; this shell
  * provides the Verilator simulation target.
  */
class HysteresisHarness(
    width:       Int = 8,
    loThreshold: Int = 64,
    hiThreshold: Int = 192
) extends Component {

  val io = new Bundle {
    val signal = in  UInt(width bits)
    val above  = out Bool()
  }

  val core = HysteresisCore.build(
    periphName  = "hysteresis",
    loThreshold = loThreshold,
    hiThreshold = hiThreshold,
    signal      = io.signal
  )

  io.above := core.above
}
