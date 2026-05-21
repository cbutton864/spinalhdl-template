package mydesign.testhelpers

import spinal.core._
import spinal.lib._
import mydesign.ComparatorCore

/** Simulation wrapper: wraps ComparatorCore.build() in a Component.
  *
  * Test harness pattern:
  *   - Core objects cannot be simulated directly (no Component boundary).
  *   - This wrapper provides the Component shell for Verilator.
  *   - Keeps the core bus-agnostic; test specifics live here.
  */
class ComparatorHarness(width: Int = 8, threshold: Int = 128) extends Component {

  val io = new Bundle {
    val countIn = in  UInt(width bits)
    val above   = out Bool()
  }

  val core = ComparatorCore.build(
    periphName = "comparator",
    threshold  = threshold,
    countIn    = io.countIn
  )

  io.above := core.above
}
