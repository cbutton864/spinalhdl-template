// Generator : SpinalHDL v1.14.0    git head : 95a5e6c65c54acfc4707c8fe6ef8b5d297cfcbde
// Component : timer_TimerSub
// Git hash  : ff19e29ce3b916d620956902527df4458707c875

`timescale 1ns/1ps 
module timer_TimerSub (
  output wire [7:0]    outSig,
  input  wire          pulledInputs,
  input  wire          clk,
  input  wire          reset
);

  reg        [7:0]    timer_countReg;

  assign outSig = timer_countReg;
  always @(posedge clk or posedge reset) begin
    if(reset) begin
      timer_countReg <= 8'h0;
    end else begin
      if(pulledInputs) begin
        timer_countReg <= (timer_countReg + 8'h01);
      end
    end
  end


endmodule
