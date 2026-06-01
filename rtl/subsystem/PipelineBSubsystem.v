// Generator : SpinalHDL v1.14.0    git head : 95a5e6c65c54acfc4707c8fe6ef8b5d297cfcbde
// Component : PipelineBSubsystem
// Git hash  : 3f206faed5e62aec749d5b088d08fba0028ecb27

`timescale 1ns/1ps 
module PipelineBSubsystem (
  input  wire          sub_enable,
  output wire [7:0]    countOut,
  output wire          flagOut,
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
  assign countOut = timerB_TimerCoreSub_sub_timer_count;
  assign flagOut = sub_comparator_above;
  always @(posedge clk or posedge reset) begin
    if(reset) begin
      sub_comparator_above <= 1'b0;
    end else begin
      sub_comparator_above <= (8'h80 <= timerB_TimerCoreSub_sub_timer_count);
    end
  end


endmodule
