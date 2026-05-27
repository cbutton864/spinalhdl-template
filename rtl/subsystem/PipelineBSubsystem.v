// Generator : SpinalHDL v1.14.0    git head : 95a5e6c65c54acfc4707c8fe6ef8b5d297cfcbde
// Component : PipelineBSubsystem
// Git hash  : f3bd2395f1b945373aa3f854f165db897d58a99d

`timescale 1ns/1ps 
module PipelineBSubsystem (
  input  wire          sub_enable,
  output wire [7:0]    _zz_sub_countB_out,
  output wire          _zz_sub_flagB_out,
  input  wire          clk,
  input  wire          reset
);

  wire       [7:0]    timerB_TimerCoreSub_sub_timer_count;
  reg                 sub_comparator_above;

  timerB_TimerCoreSub timerB_TimerCoreSub (
    .sub_timer_count (timerB_TimerCoreSub_sub_timer_count[7:0]), //o
    .pulledInputs    (sub_enable                              ), //i
    .clk             (clk                                     ), //i
    .reset           (reset                                   )  //i
  );
  assign _zz_sub_countB_out = timerB_TimerCoreSub_sub_timer_count;
  assign _zz_sub_flagB_out = sub_comparator_above;
  always @(posedge clk or posedge reset) begin
    if(reset) begin
      sub_comparator_above <= 1'b0;
    end else begin
      sub_comparator_above <= (8'h80 <= timerB_TimerCoreSub_sub_timer_count);
    end
  end


endmodule
