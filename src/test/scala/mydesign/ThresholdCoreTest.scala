package mydesign

import spinal.core._
import spinal.core.sim._
import org.scalatest.funsuite.AnyFunSuite
import mydesign.testhelpers.ThresholdHarness

/** Unit tests for ThresholdCore.
  *
  * Tests the threshold comparator with various input values.
  * Uses the ThresholdHarness wrapper for Verilator simulation.
  */
class ThresholdCoreTest extends AnyFunSuite {

  val width = 8
  val threshold = 128

  def compile() = SimConfig
    .withWave
    .workspacePath("simWorkspace/ThresholdCoreTest")
    .compile(new ThresholdHarness(width = width, threshold = threshold))

  test("Flag is low when count is below threshold") {
    compile().doSim("below") { dut =>
      dut.clockDomain.forkStimulus(period = 10)
      dut.io.countIn #= 0
      dut.clockDomain.waitSampling(3)

      for (v <- Seq(0, 1, 64, 100, 127)) {
        dut.io.countIn #= v
        dut.clockDomain.waitSampling(2) // 1 cycle for registered output
        assert(!dut.io.above.toBoolean,
          s"Expected above=false for count=$v (threshold=$threshold)")
      }
    }
  }

  test("Flag is high when count is at or above threshold") {
    compile().doSim("above") { dut =>
      dut.clockDomain.forkStimulus(period = 10)
      dut.io.countIn #= 0
      dut.clockDomain.waitSampling(3)

      for (v <- Seq(128, 129, 200, 255)) {
        dut.io.countIn #= v
        dut.clockDomain.waitSampling(2) // 1 cycle for registered output
        assert(dut.io.above.toBoolean,
          s"Expected above=true for count=$v (threshold=$threshold)")
      }
    }
  }

  test("Flag transitions correctly at threshold boundary") {
    compile().doSim("boundary") { dut =>
      dut.clockDomain.forkStimulus(period = 10)
      dut.io.countIn #= 0
      dut.clockDomain.waitSampling(3)

      // Ramp from 126 to 130, check transition
      dut.io.countIn #= 126
      dut.clockDomain.waitSampling(2)
      assert(!dut.io.above.toBoolean, "126 should be below threshold")

      dut.io.countIn #= 127
      dut.clockDomain.waitSampling(2)
      assert(!dut.io.above.toBoolean, "127 should be below threshold")

      dut.io.countIn #= 128
      dut.clockDomain.waitSampling(2)
      assert(dut.io.above.toBoolean, "128 should be at threshold")

      dut.io.countIn #= 129
      dut.clockDomain.waitSampling(2)
      assert(dut.io.above.toBoolean, "129 should be above threshold")
    }
  }
}
