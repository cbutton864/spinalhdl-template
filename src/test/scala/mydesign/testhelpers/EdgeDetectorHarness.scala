package mydesign.testhelpers

import spinal.core._
import spinal.lib._
import mydesign.EdgeDetectorCore

/** Simulation wrapper: wraps EdgeDetectorCore.build() in a Component.
  *
  * Tests the core directly — bypasses the plugin layer for unit testing.
  * EdgeDetectorCore has 2-cycle latency; tests must account for this.
  */
class EdgeDetectorHarness extends Component {

  val io = new Bundle {
    val input   = in  Bool()
    val rising  = out Bool()
    val falling = out Bool()
  }

  val core = EdgeDetectorCore.build(
    periphName = "edgeDetector",
    input      = io.input
  )

  io.rising  := core.rising
  io.falling := core.falling
}
