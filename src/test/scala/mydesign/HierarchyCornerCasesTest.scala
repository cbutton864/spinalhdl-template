package mydesign

import org.scalatest.funsuite.AnyFunSuite
import spinal.core._
import spinal.lib._
import mydesign.util.BuildHelper

class HierarchyCornerCasesTest extends AnyFunSuite {

  // Custom bidirectional interface bundle using IMasterSlave
  class CustomStreamIo extends Bundle with IMasterSlave {
    val input  = Stream(UInt(8 bits))
    val output = Stream(UInt(8 bits))

    override def asMaster(): Unit = {
      master(output)
      slave(input)
    }
  }

  test("Corner Case 1: Mixed-Direction Bundle (Stream) with Hierarchical compile") {
    val config = SpinalConfig(targetDirectory = "target/tmp_rtl")
    
    // This should compile successfully now without any hierarchy violations!
    config.generateVerilog(new Component {
      val io = new Bundle {
        val input  = slave(Stream(UInt(8 bits)))
        val output = master(Stream(UInt(8 bits)))
      }
      
      // Use buildBlock with the CustomStreamIo bundle
      val blockIo = BuildHelper.buildBlock(HardType(new CustomStreamIo), true, "StreamWrapper") { bIo =>
        // Inside the block, we simply connect input to output
        bIo.output << bIo.input
      }
      
      // Wire up at parent level
      blockIo.input << io.input
      io.output << blockIo.output
    })
  }

  test("Corner Case 2: Custom Clock Domain Area") {
    val config = SpinalConfig(targetDirectory = "target/tmp_rtl")
    
    // We will verify that compiling with custom clock domains works in both modes
    for {
      hier <- Seq(false, true)
    } {
      config.generateVerilog(new Component {
        val io = new Bundle {
          val clk2   = in Bool()
          val rst2   = in Bool()
          val enable = in Bool()
          val count  = out UInt(8 bits)
        }
        
        // Define a secondary clock domain
        val clkDomain2 = ClockDomain(io.clk2, io.rst2)
        
        val cdArea = new ClockingArea(clkDomain2) {
          // Wrap dynamic logic that uses registers in the custom clock domain
          val subCount = BuildHelper.buildBlock(HardType(UInt(8 bits)), hier, s"ClockDomainSub_hier_$hier", io.enable) { pulledEnable => outSig =>
            val countReg = Reg(UInt(8 bits)) init 0
            when(pulledEnable) {
              countReg := countReg + 1
            }
            outSig := countReg
          }
        }
        
        io.count := cdArea.subCount
      })
    }
  }

  test("Corner Case 3: Nested Meta-Hierarchy Blocks") {
    val config = SpinalConfig(targetDirectory = "target/tmp_rtl")
    
    // We will verify nested calls of buildBlock in both modes
    for {
      outerHier <- Seq(false, true)
      innerHier <- Seq(false, true)
    } {
      config.generateVerilog(new Component {
        val io = new Bundle {
          val enable = in Bool()
          val count  = out UInt(8 bits)
        }
        
        val outerCount = BuildHelper.buildBlock(HardType(UInt(8 bits)), outerHier, s"OuterBlock_H${outerHier}_H$innerHier", io.enable) { pulledOuterEnable => outerSig =>
          
          // Nested block inside the outer block!
          val innerSig = BuildHelper.buildBlock(HardType(UInt(8 bits)), innerHier, s"InnerBlock_H${outerHier}_H$innerHier", pulledOuterEnable) { pulledInnerEnable => innerSigSlot =>
            
            val countReg = Reg(UInt(8 bits)) init 0
            when(pulledInnerEnable) {
              countReg := countReg + 1
            }
            innerSigSlot := countReg
          }
          
          outerSig := innerSig
        }
        
        io.count := outerCount
      })
    }
  }
}
