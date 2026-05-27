package mydesign.util

import spinal.core._
import spinal.lib._

// Named wrapper block. Saves manual prefixing on child nets.
class PrefixArea(prefix: String) extends Area {
  this.setName(prefix)
}

sealed trait BuildMode
case object DebugBuild      extends BuildMode // Produces subcomponets for tracing waveforms
case object ProductionBuild extends BuildMode // Produces plain flat RTL for synthesis
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

  // Wraps a block in a subcomponent if hierarchical is on.
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

  // Wraps a block in a subcomponent and automatically pulls external signals across the boundary.
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

  // Groups several plugins or cores into a single physical block if hierarchical is on.
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
