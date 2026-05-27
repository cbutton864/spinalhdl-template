package mydesign

import spinal.core._
import spinal.core.fiber._
import spinal.lib._
import spinal.lib.bus.amba3.apb._
import spinal.lib.misc.plugin._

import scala.util.Try

/** Plugin: APB3 read-only status register bank.
  *
  * Exposes plugin output values as APB3-accessible registers.
  * Add this plugin to Params.plugins to enable register read-back
  * over an APB3 bus (e.g., from a RISC-V processor or a debug master).
  *
  * Register map (byte addresses, 32-bit data):
  *   0x00  Timer count value     (read only)
  *   0x04  Comparator above flag (read only, bit 0)
  *
  * APB3 interface:
  *   apb_PADDR, apb_PSEL, apb_PENABLE, apb_PWRITE, apb_PWDATA  — inputs  (from master)
  *   apb_PREADY, apb_PRDATA, apb_PSLVERR                        — outputs (to master)
  *
  * Write transactions are silently ignored (PREADY asserted, no state change).
  * PSLVERR (PSLVERROR in SpinalHDL's Apb3 bundle) is always deasserted.
  *
  * This plugin directly drives the top-level APB ports via Component.current.
  * TopIoExportPlugin drives the APB ports to safe defaults when this plugin is absent.
  */
case class ApbMonitorPlugin() extends FiberPlugin {

  val logic = during build new Area {
    val top = Component.current.asInstanceOf[MyTop]

    // ── Reconstruct the APB3 bus from individual top-level signals ──────────
    // We now have a standard, unified Apb3 bundle on high-level io.apb!
    val apb = top.io.apb

    // ── Register map via Apb3SlaveFactory ────────────────────────────────────
    val factory = Apb3SlaveFactory(apb)

    // 0x00 — Timer count (read only, zero-extended to 32 bits)
    Try(host[TimerPlugin]).toOption.foreach { timer =>
      factory.read(timer.countOut.await.resize(32), 0x00)
    }

    // 0x04 — Stage 3 threshold result (read only, bit 0) — works with any ThresholdResult impl
    Try(host[ThresholdResult]).toOption.foreach { thresh =>
      factory.read(thresh.aboveFlag.await.asUInt.resize(32), 0x04)
    }
  }
}
