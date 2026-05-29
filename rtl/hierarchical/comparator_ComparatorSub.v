// Generator : SpinalHDL v1.14.0    git head : 95a5e6c65c54acfc4707c8fe6ef8b5d297cfcbde
// Component : comparator_ComparatorSub
// Git hash  : 3f206faed5e62aec749d5b088d08fba0028ecb27

`timescale 1ns/1ps 
module comparator_ComparatorSub (
  output wire          outSig,
  input  wire [11:0]   pulledInputs,
  input  wire          clk,
  input  wire          reset
);

  reg                 comparator_aboveReg;

  assign outSig = comparator_aboveReg;
  always @(posedge clk or posedge reset) begin
    if(reset) begin
      comparator_aboveReg <= 1'b0;
    end else begin
      comparator_aboveReg <= (12'h200 <= pulledInputs);
    end
  end


endmodule
