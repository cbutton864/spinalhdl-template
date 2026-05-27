package mydesign.util

import spinal.core._
import spinal.lib._

class PrefixArea(prefix: String) extends Area {
  this.setName(prefix)
}

sealed trait BuildMode
case object DebugBuild      extends BuildMode // Hierarchical by default
case object ProductionBuild extends BuildMode // Flat by default
case object CustomBuild     extends BuildMode

case class BuildEnv(
    mode: BuildMode = ProductionBuild,
    globalHierarchy: Option[Boolean] = None
) {
  def useHierarchy(pluginDefault: Boolean): Boolean = {
    globalHierarchy.getOrElse {
      mode match {
        case DebugBuild      => true
        case ProductionBuild => false
        case CustomBuild     => pluginDefault
      }
    }
  }
}

object BuildHelper {
  /** Automatically walks a structure and calls .pull() on any Data signal if `enabled` is true.
    * Supports single Data signals, Tuples, and Sequences.
    */
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
          // If the item is anything other than a supported Spinal Data signal, Tuple, or Seq,
          // throw a clear, domain-specific developer warning so they know exactly why compilation stopped.
          SpinalError(
            s"Auto-pulling failed: ${other.getClass.getName} is not a supported wire or connection type! " +
            s"Ensure you only pass Spinal Data signals (like Bool, UInt, Bits, or Bundle), Tuples of those signals, " +
            s"or standard Sequences (Seq/List) of those signals into your buildBlock parameter list."
          )
          other
      }
    }
  }

  /** Conditionally wraps a hardware block in a sub-Component if `hierarchical` is true.
    * Uses SpinalHDL's auto-pull mechanism to route implicit inputs.
    */
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

  /** Conditionally wraps a hardware block in a sub-Component if `hierarchical` is true, 
    * automatically pulling any external inputs passed in across the component boundary.
    */
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

  /** Conditionally wraps arbitrary execution block (e.g. subsystem / multiple plugins)
    * in a sub-Component if `hierarchical` is true.
    */
  def buildSubsystem(
      hierarchical: Boolean,
      name: String
  )(body: => Unit): Unit = {
    if (hierarchical) {
      val block = new Component {
        body
      }
      block.setDefinitionName(name)
      block.setName(name)
    } else {
      body
    }
  }
}
