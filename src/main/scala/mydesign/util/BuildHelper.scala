package mydesign.util

import spinal.core._
import spinal.lib._

// Named wrapper block. Saves manual prefixing on child nets.
class PrefixArea(prefix: String) extends Area {
  this.setName(prefix)
}

// BuildMode / BuildEnv — build strategy control.
// Plugins accept a `buildEnv: BuildEnv` parameter and call `buildEnv.useHierarchy(default)`
// when deciding whether to create a Component boundary. Set the mode once in Params and it
// propagates to every plugin without per-plugin flag management.
//   FlatBuild         → always flat   (pluginDefault ignored)
//   HierarchicalBuild → always hier   (pluginDefault ignored)
//   CustomBuild       → uses pluginDefault (each plugin decides independently)
sealed trait BuildMode
case object HierarchicalBuild extends BuildMode // component hierarchy for floorplanning, subsystem partitions, wave tracing
case object FlatBuild         extends BuildMode // flat RTL, no Component boundaries, maximum global synthesis optimisation
case object CustomBuild       extends BuildMode // per-plugin default

case class BuildEnv(
    mode: BuildMode = FlatBuild,
    globalHierarchy: Option[Boolean] = None
) {
  def useHierarchy(pluginDefault: Boolean): Boolean = {
    globalHierarchy.getOrElse {
      mode match {
        case HierarchicalBuild => true
        case FlatBuild         => false
        case CustomBuild       => pluginDefault
      }
    }
  }
}

object BuildHelper {
  // Recursively calls .pull() on Data wires inside tuples or lists when hierarchical is on.
  def autoPull[T](signal: T, enabled: Boolean): T = {
    if (!enabled) signal
    else {
      signal match {
        case d: Data => d.pull().asInstanceOf[T]
        case (a, b) =>
          (autoPull(a, true), autoPull(b, true)).asInstanceOf[T]
        case (a, b, c) =>
          (autoPull(a, true), autoPull(b, true), autoPull(c, true)).asInstanceOf[T]
        case (a, b, c, d) =>
          (autoPull(a, true), autoPull(b, true), autoPull(c, true), autoPull(d, true)).asInstanceOf[T]
        case seq: Seq[_] =>
          seq.map(item => autoPull(item, true)).asInstanceOf[T]
        case other =>
          SpinalError(
            s"Auto-pulling failed: ${other.getClass.getName} is not a supported wire or connection type! " +
            s"Make sure you only pass Spinal Data signals, Tuples of signals, " +
            s"or standard Sequences into the buildBlock helper parameter list."
          )
          other
      }
    }
  }

  // Wraps a block in a subcomponent for floorplanning, partitions, and tracing logic.
  // WARNING: the body must not capture signals from the enclosing scope when hierarchical=true.
  // Any such reference crosses a Component boundary without a .pull(), which triggers
  // PhaseCheckHierarchy errors. Use the inputs overload below for any signal that needs
  // to cross the boundary — it calls autoPull automatically.
  def buildBlock[T <: Data](
      outputType: HardType[T],
      hierarchical: Boolean,
      name: String
  )(body: T => Unit): T = {
    if (hierarchical) {
      val block = new Component {
        val outSig = outputType() match {
          case ms: IMasterSlave => master(ms).asInstanceOf[T]
          case other => out(other)
        }
        body(outSig)
      }
      block.setDefinitionName(name)
      block.setName(name)
      block.outSig
    } else {
      val sig = outputType()
      body(sig)
      sig
    }
  }

  // Wraps a block in a subcomponent, automatically pulling inputs. Good for floorplanning.
  def buildBlock[T <: Data, K](
      outputType: HardType[T],
      hierarchical: Boolean,
      name: String,
      inputs: K
  )(body: K => T => Unit): T = {
    if (hierarchical) {
      val block = new Component {
        val outSig = outputType() match {
          case ms: IMasterSlave => master(ms).asInstanceOf[T]
          case other => out(other)
        }
        val pulledInputs = autoPull(inputs, true)
        body(pulledInputs)(outSig)
      }
      block.setDefinitionName(name)
      block.setName(name)
      block.outSig
    } else {
      val sig = outputType()
      body(inputs)(sig)
      sig
    }
  }

  // Groups cores into a single block. Helpful for physical placement.
  // Returns the body's result so callers can capture internal signals and cross
  // the boundary with autoPull before loading parent-scope Handles.
  def buildSubsystem[T](
      hierarchical: Boolean,
      name: String
  )(body: => T): T = {
    if (hierarchical) {
      var result: T = null.asInstanceOf[T]
      val block = new Component {
        result = body
      }
      block.setDefinitionName(name)
      block.setName(name)
      result
    } else {
      body
    }
  }
}
