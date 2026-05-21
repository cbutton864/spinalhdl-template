package mydesign.testhelpers

import spinal.core._
import spinal.lib._
import mydesign.TimerCore

/** Simulation wrapper: wraps TimerCore.build() in a Component.
  *
  * Test harness pattern:
  *   - Core objects cannot be simulated directly (no Component boundary).
  *   - This wrapper provides the Component shell for Verilator.
  *   - Keeps the core bus-agnostic; test specifics live here.
  */
class TimerHarness(width: Int = 8) extends Component {

  val io = new Bundle {
    val enable = in  Bool()
    val count  = out UInt(width bits)
  }

  val core = TimerCore.build(
    periphName = "timer",
    width      = width,
    enable     = io.enable
  )

  io.count := core.count
}
