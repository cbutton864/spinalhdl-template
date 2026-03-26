package mydesign

import spinal.core._
import spinal.core.sim._
import org.scalatest.funsuite.AnyFunSuite

/** Elaboration test: verifies the full design generates Verilog without errors.
  *
  * This is the "smoke test" — if this passes, the Fiber dependency graph
  * resolves correctly and all plugin wiring is valid.
  */
class ElaborationTest extends AnyFunSuite {

  test("MyTop elaborates with default Params") {
    SpinalConfig(
      targetDirectory = "rtl",
      defaultClockDomainFrequency = FixedFrequency(100 MHz),
      defaultConfigForClockDomains = ClockDomainConfig(
        resetKind        = ASYNC,
        resetActiveLevel = HIGH
      )
    ).generateVerilog(new MyTop(Params()))
  }

  test("MyTop elaborates with custom Params") {
    SpinalConfig(
      targetDirectory = "rtl"
    ).generateVerilog(new MyTop(Params(
      counterWidth = 16,
      threshold    = 1000
    )))
  }
}
