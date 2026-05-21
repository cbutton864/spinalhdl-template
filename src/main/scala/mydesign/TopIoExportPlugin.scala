package mydesign

import spinal.core._
import spinal.core.fiber._
import spinal.lib._
import spinal.lib.misc.plugin._

import scala.util.Try

/** Single point that wires plugin Handles ↔ top-level static IO.
  *
  * Two-phase pattern to avoid Fiber deadlocks:
  *   Phase 1: load all input Handles (non-blocking) — feeds pad signals into plugins
  *   Phase 2: await all output Handles (blocking)   — wires plugin outputs to pads
  *
  * Stage outputs are accessed via service traits (ThresholdResult, EdgeResult)
  * rather than concrete plugin types — swapping a pipeline stage requires no
  * changes here.
  *
  * Exception: TimerPlugin is accessed by concrete type in both phases because:
  *   - Phase 1: enableIn is TimerPlugin-specific (not part of any output trait)
  *   - Phase 2: top.io.count shows the raw count, not the processed signal
  *
  * APB ports (apb_PREADY / apb_PRDATA / apb_PSLVERR) are driven by
  * ApbMonitorPlugin when present, or by safe defaults here when absent.
  */
case class TopIoExportPlugin() extends FiberPlugin {

  val logic = during build new Area {
    val top = Component.current.asInstanceOf[MyTop]

    // ══════════════════════════════════════════════════════════
    // Phase 1: LOAD all input Handles (non-blocking, runs immediately)
    // ══════════════════════════════════════════════════════════

    Try(host[TimerPlugin]).toOption.foreach { timer =>
      timer.enableIn.load(top.io.enable)
    }

    // ══════════════════════════════════════════════════════════
    // Phase 2: AWAIT all output Handles (blocking)
    // ══════════════════════════════════════════════════════════

    // Timer count
    Try(host[TimerPlugin]).toOption match {
      case Some(timer) => top.io.count := timer.countOut.await
      case None        => top.io.count := 0
    }

    // Stage 3 threshold result — works with ComparatorPlugin or HysteresisPlugin
    Try(host[ThresholdResult]).toOption match {
      case Some(thresh) => top.io.aboveFlag := thresh.aboveFlag.await
      case None         => top.io.aboveFlag := False
    }

    // Stage 4 edge result — works with EdgeDetectorPlugin or any future EdgeResult impl
    Try(host[EdgeResult]).toOption match {
      case Some(edge) =>
        top.io.risingEdge  := edge.risingEdge.await
        top.io.fallingEdge := edge.fallingEdge.await
      case None =>
        top.io.risingEdge  := False
        top.io.fallingEdge := False
    }

    // APB3 safe defaults — driven here only when ApbMonitorPlugin is absent.
    // When ApbMonitorPlugin is present, it drives these ports directly.
    Try(host[ApbMonitorPlugin]).toOption match {
      case None =>
        top.io.apb_PREADY  := True              // always ready (no slave)
        top.io.apb_PRDATA  := B(0, 32 bits)
        top.io.apb_PSLVERR := False
      case Some(_) => // ApbMonitorPlugin drives these in its own logic block
    }
  }
}
