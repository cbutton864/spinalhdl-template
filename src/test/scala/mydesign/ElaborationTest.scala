package mydesign

import spinal.core._
import spinal.core.sim._
import org.scalatest.funsuite.AnyFunSuite
import mydesign.util._

/** Elaboration test: verifies the full design generates Verilog without errors.
  *
  * This is the "smoke test" — if this passes, the Fiber dependency graph
  * resolves correctly and all plugin wiring is valid.
  *
  * Optional plugins are tested by overriding `def plugins` in a Params subclass.
  * Since `plugins` is a `def`, anonymous subclassing works cleanly.
  */
class ElaborationTest extends AnyFunSuite {

  private def cfg = SpinalConfig(
    targetDirectory              = "target/tmp_rtl",
    defaultClockDomainFrequency  = FixedFrequency(100 MHz),
    defaultConfigForClockDomains = ClockDomainConfig(
      resetKind        = ASYNC,
      resetActiveLevel = HIGH
    )
  )

  // ── Default configuration ──────────────────────────────────────────────────

  test("MyTop elaborates with default Params") {
    cfg.generateVerilog(new MyTop(Params()))
  }

  test("MyTop elaborates with custom timer width and threshold") {
    cfg.generateVerilog(new MyTop(Params(
      timerWidth = 16,
      threshold  = 1000
    )))
  }

  // ── Stage 2 swap: PassThroughPlugin ↔ ScalePlugin ─────────────────────────

  test("Stage 2: ScalePlugin compresses signal range before comparator") {
    val params = new Params() {
      override def plugins = Seq(
        TimerPlugin(width = timerWidth),
        ScalePlugin(shift = 2),              // 0–255 → 0–63 in 8-bit container
        ComparatorPlugin(threshold = 32),    // threshold adjusted for scaled range
        TopIoExportPlugin()
      )
    }
    cfg.generateVerilog(new MyTop(params))
  }

  // ── Stage 3 swap: ComparatorPlugin ↔ HysteresisPlugin ─────────────────────

  test("Stage 3: HysteresisPlugin eliminates chatter near the trip point") {
    val params = new Params() {
      override def plugins = Seq(
        TimerPlugin(width = timerWidth),
        PassThroughPlugin(),
        HysteresisPlugin(loThreshold = 64, hiThreshold = 192),
        TopIoExportPlugin()
      )
    }
    cfg.generateVerilog(new MyTop(params))
  }

  // ── Combined swap: both middle stages replaced ─────────────────────────────

  test("Stage 2 + 3: ScalePlugin into HysteresisPlugin") {
    val params = new Params() {
      override def plugins = Seq(
        TimerPlugin(width = timerWidth),
        ScalePlugin(shift = 2),
        HysteresisPlugin(loThreshold = 16, hiThreshold = 48),
        TopIoExportPlugin()
      )
    }
    cfg.generateVerilog(new MyTop(params))
  }

  // ── Optional Stage 4 and side-channel ─────────────────────────────────────

  test("Stage 4: EdgeDetectorPlugin works after ComparatorPlugin") {
    val params = new Params() {
      override def plugins = Seq(
        TimerPlugin(width = timerWidth),
        PassThroughPlugin(),
        ComparatorPlugin(threshold = threshold),
        EdgeDetectorPlugin(),
        TopIoExportPlugin()
      )
    }
    cfg.generateVerilog(new MyTop(params))
  }

  test("Stage 4: EdgeDetectorPlugin works after HysteresisPlugin") {
    val params = new Params() {
      override def plugins = Seq(
        TimerPlugin(width = timerWidth),
        PassThroughPlugin(),
        HysteresisPlugin(loThreshold = 64, hiThreshold = 192),
        EdgeDetectorPlugin(),
        TopIoExportPlugin()
      )
    }
    cfg.generateVerilog(new MyTop(params))
  }

  test("ApbMonitorPlugin works with any ThresholdResult implementation") {
    val params = new Params() {
      override def plugins = Seq(
        TimerPlugin(width = timerWidth),
        PassThroughPlugin(),
        HysteresisPlugin(loThreshold = 64, hiThreshold = 192),
        ApbMonitorPlugin(),
        TopIoExportPlugin()
      )
    }
    cfg.generateVerilog(new MyTop(params))
  }

  test("Full pipeline: all four stages plus APB monitor") {
    val params = new Params() {
      override def plugins = Seq(
        TimerPlugin(width = timerWidth),
        ScalePlugin(shift = 2),
        HysteresisPlugin(loThreshold = 16, hiThreshold = 48),
        EdgeDetectorPlugin(),
        ApbMonitorPlugin(),
        TopIoExportPlugin()
      )
    }
    cfg.generateVerilog(new MyTop(params))
  }

  // ── Hierarchical BuildBlock Demonstration ──────────────────────────────────────────

  test("TimerPlugin compiles FLAT in FlatBuild mode") {
    val params = new Params() {
      override def plugins = Seq(
        TimerPlugin(width = timerWidth, buildEnv = BuildEnv(FlatBuild), periphName = "timer"),
        PassThroughPlugin(),
        ComparatorPlugin(threshold = threshold),
        TopIoExportPlugin()
      )
    }
    val design = cfg.generateVerilog(new MyTop(params))
    // Verify that "timer_TimerSub" is NOT instantiated as a module inside the MyTop verilog
    val verilogCode = java.nio.file.Files.readString(
      java.nio.file.Paths.get("target/tmp_rtl", "MyTop.v")
    )
    assert(!verilogCode.contains("module timer_TimerSub"), "Verilog should not contain module timer_TimerSub in flat mode")
  }

  test("TimerPlugin compiles HIERARCHICAL in HierarchicalBuild mode (buildBlock meta-hierarchy)") {
    val params = new Params() {
      override def plugins = Seq(
        TimerPlugin(width = timerWidth, buildEnv = BuildEnv(HierarchicalBuild), periphName = "timer"),
        PassThroughPlugin(),
        ComparatorPlugin(threshold = threshold),
        TopIoExportPlugin()
      )
    }
    val design = cfg.generateVerilog(new MyTop(params))
    // Verify that "timer_TimerSub" IS instantiated as a separate component/module inside MyTop verilog
    val verilogCode = java.nio.file.Files.readString(
      java.nio.file.Paths.get("target/tmp_rtl", "MyTop.v")
    )
    assert(verilogCode.contains("module timer_TimerSub"), "Verilog should contain module timer_TimerSub in hierarchical mode")
    assert(verilogCode.contains("timer_TimerSub timer_TimerSub (") || verilogCode.contains("timer_TimerSub timer_TimerSub_inst"), "timer_TimerSub should be instantiated inside top-level component")
  }
}
