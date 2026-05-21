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
    // Individual signals are declared in Top.scala for clean port naming.
    // Here we wire them into an Apb3 bundle for use with Apb3SlaveFactory.
    val apbConfig = Apb3Config(addressWidth = 8, dataWidth = 32)
    val apb       = Apb3(apbConfig)

    apb.PADDR   := top.io.apb_PADDR
    apb.PSEL    := top.io.apb_PSEL.asBits   // Top declares Bool; Apb3 bundle uses Bits(1 bits)
    apb.PENABLE := top.io.apb_PENABLE
    apb.PWRITE  := top.io.apb_PWRITE
    apb.PWDATA  := top.io.apb_PWDATA

    top.io.apb_PREADY  := apb.PREADY
    top.io.apb_PRDATA  := apb.PRDATA
    top.io.apb_PSLVERR := apb.PSLVERROR     // SpinalHDL Apb3 field is PSLVERROR

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
