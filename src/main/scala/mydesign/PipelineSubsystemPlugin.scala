package mydesign

import spinal.core._
import spinal.core.fiber._
import spinal.lib._
import spinal.lib.misc.plugin._
import mydesign.util.BuildHelper

/** PipelineSubsystemPlugin acts as a composite wrapper plugin inside the main RTL sources.
  * It groups multiple underlying peripheral logic cores (Timer, Comparator)
  * and uses BuildHelper.buildSubsystem to package them into a single physical component block
  * if hierarchical is enabled, completely avoiding nested PluginHost deadlocks while reusing the shared top-level host.
  */
case class PipelineSubsystemPlugin(
    width: Int = 8,
    threshold: Int = 128,
    hierarchical: Boolean = true,
    hierarchicalTimer: Boolean = true, // individual nested hierarchical flag
    subsystemName: String = "PipelineSubsystem",
    periphName: String = "timerB"
) extends FiberPlugin {

  val enableIn:  Handle[Bool] = Handle[Bool]()
  val countOut:  Handle[UInt] = Handle[UInt]()
  val flagOut:   Handle[Bool] = Handle[Bool]()

  val logic = during build new Area {
    val enableRaw = enableIn.await

    // We reuse our BuildHelper to construct the physical boundary!
    BuildHelper.buildSubsystem(hierarchical, subsystemName) {
      // Pull inputs if we are in a separate physical component (pulls from parent to subComp)
      val pulledEnable = BuildHelper.autoPull(enableRaw, hierarchical)
      pulledEnable.setName("sub_enable")

      // 1. Build Timer Core conditionally inside its own sub-component using buildBlock with automated pulling!
      val timerCount = BuildHelper.buildBlock(HardType(UInt(width bits)), hierarchicalTimer, s"${periphName}_TimerCoreSub", pulledEnable) { timerEnable => outSig =>
        val core = TimerCore.build(
          periphName = periphName,
          width      = width,
          enable     = timerEnable
        )
        outSig := core.count
      }

      // 2. Stage 2 - PassThrough (identity)
      val processed = timerCount

      // 3. Build Comparator Core
      val comparator = ComparatorCore.build(
        periphName = "comparatorB",
        threshold  = threshold,
        countIn    = processed
      )

      // Load outputs (we load the child component's internal signals directly; 
      // the parent/client will pull them across the boundary)
      timerCount.setName("sub_timer_count")
      comparator.above.setName("sub_comparator_above")

      countOut.load(timerCount)
      flagOut.load(comparator.above)
    }
  }
}
