package mydesign

import spinal.core._

object GenVerilog extends App {
  val report = SpinalConfig(
    targetDirectory = "rtl",
    defaultClockDomainFrequency = FixedFrequency(100 MHz),
    defaultConfigForClockDomains = ClockDomainConfig(
      resetKind        = ASYNC,
      resetActiveLevel = HIGH
    )
  ).generateVerilog {
    val top = new MyTop(Params())
    top
  }
  report.printPruned()
}
