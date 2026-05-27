// Generator : SpinalHDL v1.14.0    git head : 95a5e6c65c54acfc4707c8fe6ef8b5d297cfcbde
// Component : timer_TimerSub
// Git hash  : f3bd2395f1b945373aa3f854f165db897d58a99d

`timescale 1ns/1ps 
module timer_TimerSub (
  output wire [11:0]   outSig,
  input  wire          pulledInputs,
  input  wire          clk,
  input  wire          reset
);

  reg        [11:0]   timer_countReg;

  assign outSig = timer_countReg;
  always @(posedge clk or posedge reset) begin
    if(reset) begin
      timer_countReg <= 12'h0;
    end else begin
      if(pulledInputs) begin
        timer_countReg <= (timer_countReg + 12'h001);
      end
    end
  end


endmodule
