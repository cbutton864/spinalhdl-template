// Generator : SpinalHDL v1.14.0    git head : 95a5e6c65c54acfc4707c8fe6ef8b5d297cfcbde
// Component : timerA_TimerSub
// Git hash  : cfa9374703afa8115ae63767704f776d0da8d337

`timescale 1ns/1ps 
module timerA_TimerSub (
  output wire [7:0]    outSig,
  input  wire          pulledInputs,
  input  wire          clk,
  input  wire          reset
);

  reg        [7:0]    timerA_countReg;

  assign outSig = timerA_countReg;
  always @(posedge clk or posedge reset) begin
    if(reset) begin
      timerA_countReg <= 8'h0;
    end else begin
      if(pulledInputs) begin
        timerA_countReg <= (timerA_countReg + 8'h01);
      end
    end
  end


endmodule
