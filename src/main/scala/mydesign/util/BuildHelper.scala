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
