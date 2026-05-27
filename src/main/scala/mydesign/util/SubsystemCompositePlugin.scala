package mydesign.util

import spinal.core._
import spinal.core.fiber._
import spinal.lib.misc.plugin._

/** A Generic Composite Fiber Plugin wrapper that groups several sub-plugins.
  * At build time, it dynamically wraps them inside a physical Component boundary
  * if `hierarchical` is true. Under the hood, it creates a nested PluginHost.
  *
  * This is an organic, production-ready RTL tool allowing developers to compose
  * larger subsystem blocks dynamically out of modular leaf-level plugins.
  */
case class SubsystemCompositePlugin(
    subsystemName: String,
    subPluginsCreator: () => Seq[FiberPlugin],
    hierarchical: Boolean
) extends FiberPlugin {

  // Create a nested / child host for this subsystem
  val subHost = new PluginHost()

  /** We need to find external dependencies:
    * Any Handle created in the parent scope that are awaited by our sub-plugins,
    * or any Handle created inside the sub-plugins that are awaited by the parent/other plugins.
    */
  val logic = during build new Area {
    BuildHelper.buildSubsystem(hierarchical, subsystemName) {
      val subPlugins = subPluginsCreator()
      subHost.asHostOf(subPlugins)
    }
  }
}
