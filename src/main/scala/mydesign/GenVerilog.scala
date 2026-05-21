package mydesign

import spinal.core._

object GenVerilog extends App {
  val report = SpinalConfig(
    targetDirectory              = "rtl",
    defaultClockDomainFrequency  = FixedFrequency(100 MHz),
    defaultConfigForClockDomains = ClockDomainConfig(
      resetKind        = ASYNC,
      resetActiveLevel = HIGH
    )
  ).generateVerilog {
    val top = new MyTop(Params())
    // Rename clock/reset ports to match board pinout if needed:
    // top.clockDomain.clock.setName("sys_clk")
    // top.clockDomain.reset.setName("sys_rst")
    top
  }
  report.printPruned()
}
