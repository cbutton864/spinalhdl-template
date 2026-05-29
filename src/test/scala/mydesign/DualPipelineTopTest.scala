package mydesign

import org.scalatest.funsuite.AnyFunSuite
import spinal.core._
import mydesign.util._

class DualPipelineTopTest extends AnyFunSuite {

  private def cfg = SpinalConfig(
    targetDirectory              = "target/tmp_rtl",
    oneFilePerComponent          = true,
    defaultClockDomainFrequency  = FixedFrequency(100 MHz),
    defaultConfigForClockDomains = ClockDomainConfig(
      resetKind        = ASYNC,
      resetActiveLevel = HIGH
    )
  )

  test("DualPipelineTop elaborates in HierarchicalBuild mode") {
    cfg.generateVerilog(new DualPipelineTop(BuildEnv(HierarchicalBuild)))
  }

  test("DualPipelineTop elaborates in FlatBuild mode") {
    cfg.generateVerilog(new DualPipelineTop(BuildEnv(FlatBuild)))
  }
}
