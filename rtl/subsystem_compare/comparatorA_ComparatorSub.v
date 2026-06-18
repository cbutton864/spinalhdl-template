// Generator : SpinalHDL v1.14.0    git head : 95a5e6c65c54acfc4707c8fe6ef8b5d297cfcbde
// Component : comparatorA_ComparatorSub
// Git hash  : a9967d067ca51fdc87041b498e5c06c89dc501b9

`timescale 1ns/1ps 
module comparatorA_ComparatorSub (
  output wire          outSig,
  input  wire [7:0]    pulledInputs,
  input  wire          clk,
  input  wire          reset
);

  reg                 comparatorA_aboveReg;

  assign outSig = comparatorA_aboveReg;
  always @(posedge clk or posedge reset) begin
    if(reset) begin
      comparatorA_aboveReg <= 1'b0;
    end else begin
      comparatorA_aboveReg <= (8'h80 <= pulledInputs);
    end
  end


endmodule
