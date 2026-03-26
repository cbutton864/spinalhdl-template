package mydesign

import spinal.core._
import spinal.core.sim._
import org.scalatest.funsuite.AnyFunSuite
import mydesign.testhelpers.CounterHarness

/** Unit tests for CounterCore.
  *
  * Tests the free-running counter with enable gate.
  * Uses the CounterHarness wrapper for Verilator simulation.
  *
  * Timing note: `waitSampling()` lands at the rising edge. A `sleep(1)`
  * after lets Verilator's combinational evaluation settle, so register
  * outputs reflect the state computed at that edge.
  */
class CounterCoreTest extends AnyFunSuite {

  val width = 8

  def compile() = SimConfig
    .withWave
    .workspacePath("simWorkspace/CounterCoreTest")
    .compile(new CounterHarness(width = width))

  test("Counter increments when enabled") {
    compile().doSim("increment") { dut =>
      dut.clockDomain.forkStimulus(period = 10)
      dut.io.enable #= false
      dut.clockDomain.waitSampling(5)
      sleep(1)

      // Verify starting at 0
      assert(dut.io.count.toInt == 0, s"Expected 0, got ${dut.io.count.toInt}")

      // Enable counter and verify increments
      dut.io.enable #= true
      for (expected <- 1 to 10) {
        dut.clockDomain.waitSampling()
        sleep(1)
        assert(dut.io.count.toInt == expected,
          s"Cycle $expected: expected $expected, got ${dut.io.count.toInt}")
      }
    }
  }

  test("Counter holds when disabled") {
    compile().doSim("hold") { dut =>
      dut.clockDomain.forkStimulus(period = 10)
      dut.io.enable #= false
      dut.clockDomain.waitSampling(5)

      // Count to 5
      dut.io.enable #= true
      for (_ <- 0 until 5) {
        dut.clockDomain.waitSampling()
      }
      sleep(1)
      assert(dut.io.count.toInt == 5,
        s"Expected 5 after 5 enabled cycles, got ${dut.io.count.toInt}")

      // Disable and verify hold for 5 more cycles
      dut.io.enable #= false
      for (cycle <- 0 until 5) {
        dut.clockDomain.waitSampling()
        sleep(1)
        assert(dut.io.count.toInt == 5,
          s"Hold cycle $cycle: expected 5, got ${dut.io.count.toInt}")
      }
    }
  }

  test("Counter wraps at max value") {
    compile().doSim("wrap") { dut =>
      dut.clockDomain.forkStimulus(period = 10)
      dut.io.enable #= true
      dut.clockDomain.waitSampling(5)
      sleep(1)

      // Read current count, then advance to max
      val current = dut.io.count.toInt
      val maxVal = (1 << width) - 1
      val remaining = maxVal - current

      for (_ <- 0 until remaining) {
        dut.clockDomain.waitSampling()
      }
      sleep(1)
      assert(dut.io.count.toInt == maxVal,
        s"Expected $maxVal, got ${dut.io.count.toInt}")

      // One more cycle should wrap to 0
      dut.clockDomain.waitSampling()
      sleep(1)
      assert(dut.io.count.toInt == 0,
        s"Expected 0 after wrap, got ${dut.io.count.toInt}")
    }
  }

  test("Counter starts at zero after reset") {
    compile().doSim("reset") { dut =>
      dut.clockDomain.forkStimulus(period = 10)
      dut.io.enable #= false
      dut.clockDomain.waitSampling(3)
      sleep(1)

      assert(dut.io.count.toInt == 0,
        s"Expected 0 after reset, got ${dut.io.count.toInt}")
    }
  }
}
