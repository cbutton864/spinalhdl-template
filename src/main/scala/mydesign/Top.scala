package mydesign

import spinal.core._
import spinal.lib._
import spinal.lib.misc.plugin._

/** Top-level component.
  *
  * Static IO is declared here. All logic lives in plugins.
  * The PluginHost assembles everything from `params.plugins`.
  *
  * Port groups:
  *   Core     — timer enable, count output, comparator flag
  *   Edge     — edge detector outputs (driven by EdgeDetectorPlugin if present)
  *   APB3     — register read-back bus (driven by ApbMonitorPlugin if present)
  *
  * Optional plugin ports are always declared. TopIoExportPlugin drives them
  * to safe defaults when the corresponding plugin is absent.
  */
class MyTop(params: Params = Params()) extends Component {

  val io = new Bundle {

    // ── Core inputs ───────────────────────────────────────────
    val enable = in Bool()

    // ── Core outputs ──────────────────────────────────────────
    val count     = out UInt(params.timerWidth bits)
    val aboveFlag = out Bool()

    // ── Edge detector outputs (EdgeDetectorPlugin) ────────────
    val risingEdge  = out Bool()
    val fallingEdge = out Bool()

    // ── APB3 slave (ApbMonitorPlugin) — ARM APB3 naming ───────
    val apb_PADDR   = in  UInt(8 bits)
    val apb_PSEL    = in  Bool()
    val apb_PENABLE = in  Bool()
    val apb_PWRITE  = in  Bool()
    val apb_PWDATA  = in  Bits(32 bits)
    val apb_PREADY  = out Bool()
    val apb_PRDATA  = out Bits(32 bits)
    val apb_PSLVERR = out Bool()
  }

  // Flatten port names — remove "io_" prefix for clean Verilog
  io.enable.setName("enable")
  io.count.setName("count")
  io.aboveFlag.setName("above_flag")

  io.risingEdge.setName("rising_edge")
  io.fallingEdge.setName("falling_edge")

  io.apb_PADDR.setName("apb_PADDR")
  io.apb_PSEL.setName("apb_PSEL")
  io.apb_PENABLE.setName("apb_PENABLE")
  io.apb_PWRITE.setName("apb_PWRITE")
  io.apb_PWDATA.setName("apb_PWDATA")
  io.apb_PREADY.setName("apb_PREADY")
  io.apb_PRDATA.setName("apb_PRDATA")
  io.apb_PSLVERR.setName("apb_PSLVERR")

  // All logic is in plugins. TopIoExportPlugin wires handles → these ports.
  val host = new PluginHost
  host.asHostOf(params.plugins)
}
