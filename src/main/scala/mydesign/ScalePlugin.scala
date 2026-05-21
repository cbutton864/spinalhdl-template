package mydesign

import spinal.core._
import spinal.core.fiber._
import spinal.lib.misc.plugin._

/** Stage 2 — scale: right-shifts the signal by `shift` bits.
  *
  * Compresses the effective value range: an 8-bit counter (0–255)
  * with shift=2 produces a processed range of 0–63. Downstream
  * threshold comparisons must be adjusted accordingly
  * (e.g., threshold=32 instead of 128 for 50% trip point).
  *
  * Implements ProcessedSignal — swap with PassThroughPlugin for
  * full-range operation without touching any other stage.
  */
case class ScalePlugin(shift: Int = 2) extends FiberPlugin with ProcessedSignal {

  require(shift >= 0, s"shift must be >= 0, got $shift")

  val processedOut: Handle[UInt] = Handle[UInt]()

  val logic = during build new Area {
    val signal = host[SignalSource].signalOut.await
    // Right-shift then resize back to original width so downstream
    // plugins see the same UInt width regardless of shift amount.
    processedOut.load((signal >> shift).resize(signal.getWidth))
  }
}
