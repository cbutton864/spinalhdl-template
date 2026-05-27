package mydesign

import spinal.core._

object GenVerilog extends App {
  // ── Build 1: Standard FLAT Production Build ───────────────────────
  println("Generating FLAT Production Verilog to 'rtl/flat/'...")
  SpinalConfig(
    targetDirectory              = "rtl/flat",
    oneFilePerComponent          = true,
    defaultClockDomainFrequency  = FixedFrequency(100 MHz),
    defaultConfigForClockDomains = ClockDomainConfig(
      resetKind        = ASYNC,
      resetActiveLevel = HIGH
    )
  ).generateVerilog {
    val top = new MyTop(Params(globalHierarchy = Some(false)))
    top
  }

  // ── Build 2: HIERARCHICAL Debug Build ─────────────────────────────
  println("Generating HIERARCHICAL Debug Verilog to 'rtl/hierarchical/'...")
  SpinalConfig(
    targetDirectory              = "rtl/hierarchical",
    oneFilePerComponent          = true,
    defaultClockDomainFrequency  = FixedFrequency(100 MHz),
    defaultConfigForClockDomains = ClockDomainConfig(
      resetKind        = ASYNC,
      resetActiveLevel = HIGH
    )
  ).generateVerilog {
    val top = new MyTop(Params(globalHierarchy = Some(true)))
    top
  }
}
