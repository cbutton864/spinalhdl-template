package mydesign

import spinal.core._
import spinal.lib._
import spinal.lib.misc.plugin._

/** Top-level component.
  *
  * Static IO is declared here. All logic lives in plugins.
  * The PluginHost assembles everything from `params.plugins`.
  * Plugin list order is irrelevant — Fiber resolves dependencies.
  */
class MyTop(params: Params = Params()) extends Component {

  val io = new Bundle {
    // ── Inputs ────────────────────────────────────────────────
    val enable = in  Bool()

    // ── Outputs ───────────────────────────────────────────────
    val count     = out UInt(params.counterWidth bits)
    val aboveFlag = out Bool()
  }

  // Flatten port names — remove "io_" prefix for clean Verilog
  io.enable.setName("enable")
  io.count.setName("count")
  io.aboveFlag.setName("above_flag")

  // All logic is in plugins. TopIoExportPlugin wires handles → these ports.
  val host = new PluginHost
  host.asHostOf(params.plugins)
}
