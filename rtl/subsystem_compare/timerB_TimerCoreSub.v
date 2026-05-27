// Generator : SpinalHDL v1.14.0    git head : 95a5e6c65c54acfc4707c8fe6ef8b5d297cfcbde
// Component : timerB_TimerCoreSub
// Git hash  : cfa9374703afa8115ae63767704f776d0da8d337

`timescale 1ns/1ps 
module timerB_TimerCoreSub (
  output wire [7:0]    sub_timer_count,
  input  wire          pulledInputs,
  input  wire          clk,
  input  wire          reset
);

  reg        [7:0]    timerB_countReg;

  assign sub_timer_count = timerB_countReg;
  always @(posedge clk or posedge reset) begin
    if(reset) begin
      timerB_countReg <= 8'h0;
    end else begin
      if(pulledInputs) begin
        timerB_countReg <= (timerB_countReg + 8'h01);
      end
    end
  end


endmodule
