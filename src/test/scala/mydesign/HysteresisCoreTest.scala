package mydesign

import spinal.core._
import spinal.core.sim._
import org.scalatest.funsuite.AnyFunSuite
import mydesign.testhelpers.HysteresisHarness

/** Unit tests for HysteresisCore.
  *
  * Dead band is [loThreshold, hiThreshold):
  *   signal >= hiThreshold → latch high
  *   signal <  loThreshold → latch low
  *   signal in dead band   → hold last value
  *
  * Timing note: HysteresisCore has 1 cycle latency (one RegInit stage).
  * After driving signal, waitSampling(2): one to capture, one to read stable output.
  */
class HysteresisCoreTest extends AnyFunSuite {

  val width       = 8
  val loThreshold = 64
  val hiThreshold = 192

  def compile() = SimConfig
    .withWave
    .workspacePath("simWorkspace/HysteresisCoreTest")
    .compile(new HysteresisHarness(width = width, loThreshold = loThreshold, hiThreshold = hiThreshold))

  test("Reset state: aboveFlag initializes low") {
    compile().doSim("reset") { dut =>
      dut.clockDomain.forkStimulus(period = 10)
      dut.io.signal #= 0
      dut.clockDomain.waitSampling(2)
      assert(!dut.io.above.toBoolean, "aboveFlag should initialize to False on reset")
    }
  }

  test("Latches high when signal reaches hiThreshold") {
    compile().doSim("latch_high") { dut =>
      dut.clockDomain.forkStimulus(period = 10)
      dut.io.signal #= 0
      dut.clockDomain.waitSampling(3)

      dut.io.signal #= hiThreshold
      dut.clockDomain.waitSampling(2)
      assert(dut.io.above.toBoolean,
        s"Expected above=true at signal=$hiThreshold (hiThreshold=$hiThreshold)")
    }
  }

  test("Latches low when signal falls below loThreshold") {
    compile().doSim("latch_low") { dut =>
      dut.clockDomain.forkStimulus(period = 10)
      dut.io.signal #= 0
      dut.clockDomain.waitSampling(3)

      dut.io.signal #= hiThreshold
      dut.clockDomain.waitSampling(2)
      assert(dut.io.above.toBoolean, "Setup: should be latched high")

      dut.io.signal #= loThreshold - 1
      dut.clockDomain.waitSampling(2)
      assert(!dut.io.above.toBoolean,
        s"Expected above=false at signal=${loThreshold - 1} (loThreshold=$loThreshold)")
    }
  }

  test("Holds high in dead band when previously latched high") {
    compile().doSim("hold_high") { dut =>
      dut.clockDomain.forkStimulus(period = 10)
      dut.io.signal #= 0
      dut.clockDomain.waitSampling(3)

      dut.io.signal #= hiThreshold
      dut.clockDomain.waitSampling(2)

      for (v <- Seq(loThreshold, loThreshold + 1, 128, hiThreshold - 1)) {
        dut.io.signal #= v
        dut.clockDomain.waitSampling(2)
        assert(dut.io.above.toBoolean,
          s"Expected above=true in dead band at signal=$v (was latched high)")
      }
    }
  }

  test("Holds low in dead band when previously latched low") {
    compile().doSim("hold_low") { dut =>
      dut.clockDomain.forkStimulus(period = 10)
      dut.io.signal #= 0
      dut.clockDomain.waitSampling(3)

      for (v <- Seq(loThreshold, loThreshold + 1, 128, hiThreshold - 1)) {
        dut.io.signal #= v
        dut.clockDomain.waitSampling(2)
        assert(!dut.io.above.toBoolean,
          s"Expected above=false in dead band at signal=$v (was latched low)")
      }
    }
  }

  test("Boundary: loThreshold exactly is in dead band (condition is strictly <)") {
    compile().doSim("boundary_lo") { dut =>
      dut.clockDomain.forkStimulus(period = 10)
      dut.io.signal #= 0
      dut.clockDomain.waitSampling(3)

      dut.io.signal #= hiThreshold
      dut.clockDomain.waitSampling(2)

      dut.io.signal #= loThreshold
      dut.clockDomain.waitSampling(2)
      assert(dut.io.above.toBoolean,
        s"signal=loThreshold ($loThreshold) is dead band — flag should hold, not latch low")
    }
  }

  test("Boundary: hiThreshold exactly latches high (condition is >=)") {
    compile().doSim("boundary_hi") { dut =>
      dut.clockDomain.forkStimulus(period = 10)
      dut.io.signal #= 0
      dut.clockDomain.waitSampling(3)

      dut.io.signal #= hiThreshold
      dut.clockDomain.waitSampling(2)
      assert(dut.io.above.toBoolean,
        s"signal=hiThreshold ($hiThreshold) should latch high (condition is >=)")
    }
  }
}
