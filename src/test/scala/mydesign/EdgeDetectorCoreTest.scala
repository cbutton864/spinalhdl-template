package mydesign

import spinal.core._
import spinal.core.sim._
import org.scalatest.funsuite.AnyFunSuite
import mydesign.testhelpers.EdgeDetectorHarness

/** Unit tests for EdgeDetectorCore.
  *
  * EdgeDetectorCore has 1-cycle pipeline latency:
  *   Cycle 0: input changes
  *   Cycle 1: prevStage and edgeStage update simultaneously — output valid
  *
  * Both registers (prev and rising/falling) update on the same rising edge.
  * The detection `input && !prev` uses the PRE-edge value of prev, so the edge
  * is captured at the same cycle that prev latches the new input.
  *
  * All assertions read the output 1 cycle after driving the input.
  */
class EdgeDetectorCoreTest extends AnyFunSuite {

  def compile() = SimConfig
    .withWave
    .workspacePath("simWorkspace/EdgeDetectorCoreTest")
    .compile(new EdgeDetectorHarness)

  test("Rising edge detected when input goes low to high") {
    compile().doSim("rising") { dut =>
      dut.clockDomain.forkStimulus(period = 10)
      dut.io.input #= false
      dut.clockDomain.waitSampling(5)

      // Drive rising edge
      dut.io.input #= true
      dut.clockDomain.waitSampling(1)   // 1-cycle latency
      sleep(1)
      assert(dut.io.rising.toBoolean,
        "Expected rising=true one cycle after input went high")
      assert(!dut.io.falling.toBoolean,
        "Expected falling=false on rising transition")

      // Rising pulse lasts exactly one cycle
      dut.clockDomain.waitSampling()
      sleep(1)
      assert(!dut.io.rising.toBoolean,
        "Rising pulse must be exactly one cycle wide")
    }
  }

  test("Falling edge detected when input goes high to low") {
    compile().doSim("falling") { dut =>
      dut.clockDomain.forkStimulus(period = 10)
      dut.io.input #= true
      dut.clockDomain.waitSampling(5)

      // Drive falling edge
      dut.io.input #= false
      dut.clockDomain.waitSampling(1)
      sleep(1)
      assert(!dut.io.rising.toBoolean,
        "Expected rising=false on falling transition")
      assert(dut.io.falling.toBoolean,
        "Expected falling=true one cycle after input went low")

      // Falling pulse lasts exactly one cycle
      dut.clockDomain.waitSampling()
      sleep(1)
      assert(!dut.io.falling.toBoolean,
        "Falling pulse must be exactly one cycle wide")
    }
  }

  test("No edges when input is stable") {
    compile().doSim("stable") { dut =>
      dut.clockDomain.forkStimulus(period = 10)

      // Stable high — no edges
      dut.io.input #= true
      dut.clockDomain.waitSampling(10)
      sleep(1)
      assert(!dut.io.rising.toBoolean,  "No rising edge on stable high input")
      assert(!dut.io.falling.toBoolean, "No falling edge on stable high input")

      // Stable low — no edges
      dut.io.input #= false
      dut.clockDomain.waitSampling(3)   // settle past the transition
      dut.clockDomain.waitSampling(5)
      sleep(1)
      assert(!dut.io.rising.toBoolean,  "No rising edge on stable low input")
      assert(!dut.io.falling.toBoolean, "No falling edge on stable low input")
    }
  }

  test("Both outputs are deasserted after reset") {
    compile().doSim("reset") { dut =>
      dut.clockDomain.forkStimulus(period = 10)
      dut.io.input #= false
      dut.clockDomain.waitSampling(3)
      sleep(1)
      assert(!dut.io.rising.toBoolean,  "rising should be false after reset")
      assert(!dut.io.falling.toBoolean, "falling should be false after reset")
    }
  }
}
