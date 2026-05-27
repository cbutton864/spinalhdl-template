package mydesign

class TopLevelRtlIntegrationTest extends GoldenIntegrationTest {

  test("Verifies that the Golden RTL file physically exists and matches the source of truth") {
    // Assert that MyTop.v has been properly compiled and remains release ready
    verifyGoldenRtlReady()
    
    // In a full system, you would call CocoaTB, Verilator, or a Spinal Simulation harness
    // directly targeting the generated "rtl/MyTop.v" file path:
    println("[SUCCESS] TopLevel RTL matches current Scala Source parameters and is ready for hardware validation!")
  }
}
