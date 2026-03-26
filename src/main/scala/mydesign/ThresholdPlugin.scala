package mydesign

import spinal.core._
import spinal.core.fiber._
import spinal.lib._
import spinal.lib.misc.plugin._

/** Plugin: threshold comparator.
  *
  * Consumes:
  *   - `host[CounterPlugin].countOut` — counter value (via Fiber await)
  *
  * Publishes:
  *   - `aboveFlag: Handle[Bool]` — true when counter >= threshold
  */
case class ThresholdPlugin(
    threshold: Int = 128
) extends FiberPlugin {

  // ── Published Handles ──────────────────────────────────────
  val aboveFlag: Handle[Bool] = Handle[Bool]()

  val logic = during build new Area {
    val counterPlugin = host[CounterPlugin]
    val countVal      = counterPlugin.countOut.await

    val core = ThresholdCore.build(
      periphName = "threshold",
      threshold  = threshold,
      countIn    = countVal
    )

    aboveFlag.load(core.above)
  }
}
