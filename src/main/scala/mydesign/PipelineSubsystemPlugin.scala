package mydesign

import spinal.core._
import spinal.core.fiber._
import spinal.lib._
import spinal.lib.misc.plugin._
import mydesign.util._

/** Composite coordinator plugin: Timer + Comparator inside one optional physical boundary.
  *
  * Uses BuildHelper.buildSubsystem to create an optional Component wrapper for
  * floorplanning or waveform tracing. The component boundary crossing is resolved
  * internally — consumers just call .await on countOut / flagOut without knowing
  * whether this subsystem is hierarchical or flat.
  *
  * Build mode is controlled by `buildEnv`:
  *   FlatBuild         → flat, no Component boundaries
  *   HierarchicalBuild → full Component hierarchy (subsystem + inner timer block)
  *   CustomBuild       → hierarchical by default (pluginDefault = true)
  *
  * Consumes:
  *   - `enableIn: Handle[Bool]` — loaded by the caller's IoExport plugin
  *
  * Publishes:
  *   - `countOut: Handle[UInt]` — timer count, parent-context signal
  *   - `flagOut:  Handle[Bool]` — comparator above flag, parent-context signal
  */
case class PipelineSubsystemPlugin(
    width:         Int      = 8,
    threshold:     Int      = 128,
    buildEnv:      BuildEnv = BuildEnv(mode = HierarchicalBuild),
    subsystemName: String   = "PipelineSubsystem",
    periphName:    String   = "timerB"
) extends FiberPlugin {

  val enableIn: Handle[Bool] = Handle[Bool]()
  val countOut: Handle[UInt] = Handle[UInt]()
  val flagOut:  Handle[Bool] = Handle[Bool]()

  val logic = during build new Area {
    val enableRaw = enableIn.await
    val hier      = buildEnv.useHierarchy(true)

    val (rawCount, rawFlag) = BuildHelper.buildSubsystem(hier, subsystemName) {
      val pulledEnable = BuildHelper.autoPull(enableRaw, hier)
      pulledEnable.setName("sub_enable")

      val timerCount = BuildHelper.buildBlock(
        HardType(UInt(width bits)), hier, s"${periphName}_TimerCoreSub", pulledEnable
      ) { timerEnable => outSig =>
        val core = TimerCore.build(periphName = periphName, width = width, enable = timerEnable)
        outSig := core.count
      }
      timerCount.setName("sub_timer_count")

      val comparator = ComparatorCore.build(
        periphName = "comparatorB",
        threshold  = threshold,
        countIn    = timerCount
      )
      comparator.above.setName("sub_comparator_above")

      (timerCount, comparator.above)
    }

    val crossedCount = BuildHelper.autoPull(rawCount, hier)
    val crossedFlag  = BuildHelper.autoPull(rawFlag,  hier)
    crossedCount.setName("countOut")
    crossedFlag.setName("flagOut")
    countOut.load(crossedCount)
    flagOut.load(crossedFlag)
  }
}
