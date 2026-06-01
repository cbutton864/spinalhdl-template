package mydesign

import org.scalatest.funsuite.AnyFunSuite
import spinal.core._
import spinal.core.fiber._
import spinal.lib._
import spinal.lib.misc.plugin._
import mydesign.util._

class SubsystemCompositeTest extends AnyFunSuite {
  test("Compile dual pipelines and review side-by-side flat vs composite structure") {
    val config = SpinalConfig(
      targetDirectory              = "rtl/subsystem_compare",
      oneFilePerComponent          = true,
      defaultClockDomainFrequency  = FixedFrequency(100 MHz),
      defaultConfigForClockDomains = ClockDomainConfig(
        resetKind        = ASYNC,
        resetActiveLevel = HIGH
      )
    )

    println("Generating Dual Pipeline Verilog with composite configuration...")
    config.generateVerilog(new DualPipelineTop(BuildEnv(HierarchicalBuild)))
  }
}
