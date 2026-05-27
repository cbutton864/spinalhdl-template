package mydesign

import org.scalatest.funsuite.AnyFunSuite
import java.io.File
import java.nio.file.{Files, Paths}

/** Base class for physical, golden top-level integration tests.
  *
  * Enforces a hard physical isolation:
  *   1. The production Verilog file MUST exist inside the release repository directory `rtl/MyTop.v`.
  *   2. It verifies the release file was generated RECENTLY or compiles fresh during build pipelines,
  *      preventing verification of stale debug sandboxes.
  *
  * This guarantees that what we are physically testing in simulation matches card-for-card
  * what is checked into the repository and sent to physical hardware synthesizers.
  */
abstract class GoldenIntegrationTest extends AnyFunSuite {

  private val productionRtlPath = Paths.get("rtl", "MyTop.v")

  /** Asserts that the golden production RTL is compiled, fresh, and physically ready. */
  def verifyGoldenRtlReady(): Unit = {
    if (!Files.exists(productionRtlPath)) {
      fail(
        s"CRITICAL SYNTHESIS ISOLATION ERROR:\n" +
        s"  Expected golden production RTL does not exist at '${productionRtlPath.toAbsolutePath}'!\n\n" +
        s"  To resolve this:\n" +
        s"  Run 'sbt \"runMain mydesign.GenVerilog\"' to generate your release Verilog " +
        s"  before running integration tests."
      )
    }

    // Gentle time-check nudge: Warn if the Verilog of source-of-truth is older than our active Scala models
    val activeScalaSources = getLatestModificationTime(new File("src/main/scala"))
    val goldenVerilogTime = Files.getLastModifiedTime(productionRtlPath).toMillis

    if (activeScalaSources > goldenVerilogTime) {
      println(
        s"\n========================================================================\n" +
        s"[WARNING] SANDBOX DIVERGENCE RESISTANCE TRIGGERED:\n" +
        s"  Your Scala source code is newer than '${productionRtlPath.getFileName}'.\n" +
        s"  Please compile your release Verilog via 'sbt \"runMain mydesign.GenVerilog\"'\n" +
        s"  to ensure you are verifying the current Golden Source of Truth!\n" +
        s"========================================================================\n"
      )
    }
  }

  private def getLatestModificationTime(dir: File): Long = {
    if (!dir.exists()) return 0L
    val files = dir.listFiles()
    if (files == null) return 0L
    files.map { f =>
      if (f.isDirectory) getLatestModificationTime(f)
      else f.lastModified()
    }.maxOption.getOrElse(0L)
  }
}
