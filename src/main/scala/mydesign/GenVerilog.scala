package mydesign

import spinal.core._
import mydesign.util._

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
    val top = new MyTop(Params.productionFlat)
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
    val top = new MyTop(Params.debugHierarchical)
    top
  }

  // ── Build 3: COMPOSITE SUBSYSTEM Build ───────────────────────────
  println("Generating COMPOSITE SUBSYSTEM Verilog to 'rtl/subsystem/'...")
  SpinalConfig(
    targetDirectory              = "rtl/subsystem",
    oneFilePerComponent          = true,
    defaultClockDomainFrequency  = FixedFrequency(100 MHz),
    defaultConfigForClockDomains = ClockDomainConfig(
      resetKind        = ASYNC,
      resetActiveLevel = HIGH
    )
  ).generateVerilog {
    // For build 3, we demonstrate compiling a modular subsystem layout using DualPipelineTop as our top design shell!
    val top = new DualPipelineTop(DualPipelineParams(buildEnv = BuildEnv(HierarchicalBuild)))
    top
  }
}
