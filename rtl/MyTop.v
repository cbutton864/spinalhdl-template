// Generator : SpinalHDL v1.14.0    git head : 95a5e6c65c54acfc4707c8fe6ef8b5d297cfcbde
// Component : MyTop

`timescale 1ns/1ps

module MyTop (
  input  wire          enable,
  output wire [7:0]    count,
  output wire          above_flag,
  input  wire          clk,
  input  wire          reset
);

  reg        [7:0]    counter_countReg;
  reg                 threshold_aboveReg;

  assign count = counter_countReg;
  assign above_flag = threshold_aboveReg;
  always @(posedge clk or posedge reset) begin
    if(reset) begin
      counter_countReg <= 8'h0;
      threshold_aboveReg <= 1'b0;
    end else begin
      if(enable) begin
        counter_countReg <= (counter_countReg + 8'h01);
      end
      threshold_aboveReg <= (8'h80 <= counter_countReg);
    end
  end


endmodule
