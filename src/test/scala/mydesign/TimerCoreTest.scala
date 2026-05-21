package mydesign

import spinal.core._
import spinal.core.sim._
import org.scalatest.funsuite.AnyFunSuite
import mydesign.testhelpers.TimerHarness

/** Unit tests for TimerCore.
  *
  * Tests the free-running timer with enable gate.
  * Uses the TimerHarness wrapper for Verilator simulation.
  *
  * Timing note: `waitSampling()` lands at the rising edge. A `sleep(1)`
  * after lets Verilator's combinational evaluation settle, so register
  * outputs reflect the state computed at that edge.
  */
class TimerCoreTest extends AnyFunSuite {

  val width = 8

  def compile() = SimConfig
    .withWave
    .workspacePath("simWorkspace/TimerCoreTest")
    .compile(new TimerHarness(width = width))

  test("Timer increments when enabled") {
    compile().doSim("increment") { dut =>
      dut.clockDomain.forkStimulus(period = 10)
      dut.io.enable #= false
      dut.clockDomain.waitSampling(5)
      sleep(1)

      assert(dut.io.count.toInt == 0, s"Expected 0, got ${dut.io.count.toInt}")

      dut.io.enable #= true
      for (expected <- 1 to 10) {
        dut.clockDomain.waitSampling()
        sleep(1)
        assert(dut.io.count.toInt == expected,
          s"Cycle $expected: expected $expected, got ${dut.io.count.toInt}")
      }
    }
  }

  test("Timer holds when disabled") {
    compile().doSim("hold") { dut =>
      dut.clockDomain.forkStimulus(period = 10)
      dut.io.enable #= false
      dut.clockDomain.waitSampling(5)

      dut.io.enable #= true
      for (_ <- 0 until 5) {
        dut.clockDomain.waitSampling()
      }
      sleep(1)
      assert(dut.io.count.toInt == 5,
        s"Expected 5 after 5 enabled cycles, got ${dut.io.count.toInt}")

      dut.io.enable #= false
      for (cycle <- 0 until 5) {
        dut.clockDomain.waitSampling()
        sleep(1)
        assert(dut.io.count.toInt == 5,
          s"Hold cycle $cycle: expected 5, got ${dut.io.count.toInt}")
      }
    }
  }

  test("Timer wraps at max value") {
    compile().doSim("wrap") { dut =>
      dut.clockDomain.forkStimulus(period = 10)
      dut.io.enable #= true
      dut.clockDomain.waitSampling(5)
      sleep(1)

      val current   = dut.io.count.toInt
      val maxVal    = (1 << width) - 1
      val remaining = maxVal - current

      for (_ <- 0 until remaining) {
        dut.clockDomain.waitSampling()
      }
      sleep(1)
      assert(dut.io.count.toInt == maxVal,
        s"Expected $maxVal, got ${dut.io.count.toInt}")

      dut.clockDomain.waitSampling()
      sleep(1)
      assert(dut.io.count.toInt == 0,
        s"Expected 0 after wrap, got ${dut.io.count.toInt}")
    }
  }

  test("Timer starts at zero after reset") {
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
