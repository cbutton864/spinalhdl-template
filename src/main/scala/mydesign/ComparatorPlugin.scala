package mydesign

import spinal.core._
import spinal.core.fiber._
import spinal.lib._
import spinal.lib.misc.plugin._
import mydesign.util._

/** Stage 3 — simple level comparator.
  *
  * Consumes ProcessedSignal so it works after any Stage 2 plugin
  * (PassThroughPlugin or ScalePlugin) without modification.
  * Implements ThresholdResult so Stage 4 (EdgeDetectorPlugin) and
  * TopIoExportPlugin consume the result via the trait, not this
  * concrete type — swap with HysteresisPlugin freely.
  *
  * Consumes:
  *   - `host[ProcessedSignal].processedOut` — stage 2 output
  *
  * Publishes:
  *   - `aboveFlag: Handle[Bool]` — true when processedOut >= threshold
  */
case class ComparatorPlugin(
    threshold:  Int      = 128,
    periphName: String   = "comparator",
    buildEnv:   BuildEnv = BuildEnv()
) extends FiberPlugin with ThresholdResult {

  val aboveFlag: Handle[Bool] = Handle[Bool]()

  val logic = during build new Area {
    val countVal = host[ProcessedSignal].processedOut.await
    val hier     = buildEnv.useHierarchy(false)

    val above = BuildHelper.buildBlock(
      HardType(Bool()),
      hier,
      s"${periphName}_ComparatorSub",
      countVal
    ) { pulledCount => outSig =>
      val core = ComparatorCore.build(periphName = periphName, threshold = threshold, countIn = pulledCount)
      outSig := core.above
    }

    aboveFlag.load(above)
  }
}
