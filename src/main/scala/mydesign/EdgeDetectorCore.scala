package mydesign

import spinal.core._
import spinal.lib._

/** Bus-agnostic edge detector core.
  *
  * Detects rising and falling edges on a Bool input.
  * Output is registered: both `rising` and `falling` pulse for exactly one cycle.
  *
  * Pipeline structure (2 registers, named Areas):
  *
  *   Input ──► [ prevStage: RegNext ] ──► [ edgeStage: registered detection ] ──► Output
  *
  * Total latency: 1 clock cycle. Both registers update simultaneously on the same rising
  * edge. The detection `input && !prev` uses the pre-edge value of prev, so the pulse
  * is output at the same edge that prev latches the new input value.
  *
  * This core demonstrates the named-Area pipeline pattern used in VexRiscv
  * and larger SpinalHDL designs. Each stage is a named `Area`; its signals
  * appear in waveforms with the stage name as a prefix (e.g. `prevStage_prev`).
  *
  * Core pattern: stateless `object` with `build()` method.
  * Returns a plain Scala `case class Io` — NOT a Bundle.
  */
object EdgeDetectorCore {

  /** Plain Scala case class — NOT a Bundle. */
  case class Io(
      rising:  Bool,
      falling: Bool
  )

  def build(
      periphName: String = "edgeDetector",
      input:      Bool   = null
  ): Io = {
    require(input != null, "input signal is required")

    // ── Stage 1: capture previous value ──────────────────────────
    // Named Area — signals appear as `prevStage_prev` in waveforms.
    val prevStage = new Area {
      val prev = RegNext(input) init False
      prev.setName(s"${periphName}_prev")
    }

    // ── Stage 2: detect and register edge events ──────────────────
    // Named Area — signals appear as `edgeStage_rising` / `edgeStage_falling`.
    val edgeStage = new Area {
      val rising  = RegNext(input && !prevStage.prev) init False
      val falling = RegNext(!input && prevStage.prev) init False
      rising.setName(s"${periphName}_rising")
      falling.setName(s"${periphName}_falling")
    }

    Io(
      rising  = edgeStage.rising,
      falling = edgeStage.falling
    )
  }
}
