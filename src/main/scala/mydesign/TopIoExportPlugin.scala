package mydesign

import spinal.core._
import spinal.core.fiber._
import spinal.lib._
import spinal.lib.misc.plugin._

import scala.util.Try

/** Single point that wires plugin Handles <-> top-level static IO.
  *
  * Two-phase pattern to avoid Fiber deadlocks:
  *   Phase 1: load all input Handles (non-blocking) — feeds pad signals into plugins
  *   Phase 2: await all output Handles (blocking)   — wires plugin outputs to pads
  *
  * This guarantees no circular dependency: input loads complete immediately,
  * unblocking plugins that await them, which then load their outputs,
  * which this plugin awaits in phase 2.
  *
  * Try(host[XxxPlugin]).toOption makes each section safe when a plugin is absent.
  * This supports modular plugin lists.
  */
case class TopIoExportPlugin() extends FiberPlugin {

  val logic = during build new Area {
    val top = Component.current.asInstanceOf[MyTop]

    // ══════════════════════════════════════════════════════════
    // Phase 1: LOAD all input Handles (non-blocking, runs immediately)
    // ══════════════════════════════════════════════════════════

    Try(host[CounterPlugin]).toOption.foreach { counter =>
      counter.enableIn.load(top.io.enable)
    }

    // ══════════════════════════════════════════════════════════
    // Phase 2: AWAIT all output Handles (blocking)
    // ══════════════════════════════════════════════════════════

    Try(host[CounterPlugin]).toOption match {
      case Some(counter) =>
        top.io.count := counter.countOut.await
      case None =>
        top.io.count := 0
    }

    Try(host[ThresholdPlugin]).toOption match {
      case Some(thresh) =>
        top.io.aboveFlag := thresh.aboveFlag.await
      case None =>
        top.io.aboveFlag := False
    }
  }
}
