package mydesign

import spinal.core._
import spinal.core.fiber._

/** Stage-boundary contracts for the reconfigurable signal pipeline.
  *
  * Each trait names one boundary between pipeline stages.
  * Producing plugins implement the trait; consuming plugins access
  * upstream data via host[Trait] rather than host[ConcretePlugin].
  * Swapping a stage means replacing the plugin that implements its
  * output trait — nothing downstream changes.
  *
  * Pipeline topology (Stage 2 and Stage 3 are swappable):
  *
  *   TimerPlugin ── SignalSource ──► [Stage 2] ── ProcessedSignal ──► [Stage 3] ── ThresholdResult ──► [Stage 4]
  *
  *   Stage 2 options:  PassThroughPlugin   — identity, no transform
  *                     ScalePlugin         — right-shifts the signal
  *
  *   Stage 3 options:  ComparatorPlugin    — simple level compare (>= threshold)
  *                     HysteresisPlugin    — dual-threshold latch (eliminates chatter)
  *
  *   Stage 4:          EdgeDetectorPlugin  — rising/falling edge detection (optional)
  */

/** Stage 1 → Stage 2: raw signal from the count source. */
trait SignalSource {
  val signalOut: Handle[UInt]
}

/** Stage 2 → Stage 3: signal after optional transformation (scale, filter, etc.). */
trait ProcessedSignal {
  val processedOut: Handle[UInt]
}

/** Stage 3 → Stage 4 / output: binary threshold decision. */
trait ThresholdResult {
  val aboveFlag: Handle[Bool]
}

/** Stage 4 → output: registered edge flags from the threshold signal. */
trait EdgeResult {
  val risingEdge:  Handle[Bool]
  val fallingEdge: Handle[Bool]
}
