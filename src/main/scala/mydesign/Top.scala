package mydesign

import spinal.core._
import spinal.lib._
import spinal.lib.bus.amba3.apb._
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
    val apb = slave(Apb3(Apb3Config(addressWidth = 8, dataWidth = 32)))
  }

  // Flatten port names — remove "io_" prefix for clean Verilog
  io.enable.setName("enable")
  io.count.setName("count")
  io.aboveFlag.setName("above_flag")

  io.risingEdge.setName("rising_edge")
  io.fallingEdge.setName("falling_edge")

  io.apb.PADDR.setName("apb_PADDR")
  io.apb.PSEL.setName("apb_PSEL")
  io.apb.PENABLE.setName("apb_PENABLE")
  io.apb.PWRITE.setName("apb_PWRITE")
  io.apb.PWDATA.setName("apb_PWDATA")
  io.apb.PREADY.setName("apb_PREADY")
  io.apb.PRDATA.setName("apb_PRDATA")
  io.apb.PSLVERROR.setName("apb_PSLVERR")

  // All logic is in plugins. TopIoExportPlugin wires handles → these ports.
  val host = new PluginHost
  host.asHostOf(params.plugins)
}
