package mydesign

import spinal.core._
import spinal.core.fiber._
import spinal.lib.misc.plugin._

/** Stage 2 — identity: passes the signal through unchanged.
  *
  * Implements ProcessedSignal so downstream Stage 3 plugins
  * (ComparatorPlugin, HysteresisPlugin) work without modification.
  *
  * Swap this with ScalePlugin to compress the effective signal range.
  */
case class PassThroughPlugin() extends FiberPlugin with ProcessedSignal {

  val processedOut: Handle[UInt] = Handle[UInt]()

  val logic = during build new Area {
    val signal = host[SignalSource].signalOut.await
    processedOut.load(signal)
  }
}
