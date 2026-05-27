// Generator : SpinalHDL v1.14.0    git head : 95a5e6c65c54acfc4707c8fe6ef8b5d297cfcbde
// Component : DualPipelineTop
// Git hash  : f3bd2395f1b945373aa3f854f165db897d58a99d

`timescale 1ns/1ps 
module DualPipelineTop (
  input  wire          enable,
  output wire [7:0]    countA,
  output wire          flagA,
  output wire [7:0]    countB,
  output wire          flagB,
  input  wire          clk,
  input  wire          reset
);

  wire       [7:0]    timerA_TimerSub_outSig;
  wire       [7:0]    PipelineBSubsystem__zz_sub_countB_out;
  wire                PipelineBSubsystem__zz_sub_flagB_out;
  wire       [7:0]    sub_countB_out;
  wire                sub_flagB_out;
  wire                TimerPlugin_logic_enable;
  reg                 comparatorA_aboveReg;

  timerA_TimerSub timerA_TimerSub (
    .outSig       (timerA_TimerSub_outSig[7:0]), //o
    .pulledInputs (TimerPlugin_logic_enable   ), //i
    .clk          (clk                        ), //i
    .reset        (reset                      )  //i
  );
  PipelineBSubsystem PipelineBSubsystem (
    .sub_enable         (enable                                    ), //i
    ._zz_sub_countB_out (PipelineBSubsystem__zz_sub_countB_out[7:0]), //o
    ._zz_sub_flagB_out  (PipelineBSubsystem__zz_sub_flagB_out      ), //o
    .clk                (clk                                       ), //i
    .reset              (reset                                     )  //i
  );
  assign TimerPlugin_logic_enable = enable;
  assign countA = timerA_TimerSub_outSig;
  assign sub_countB_out = PipelineBSubsystem__zz_sub_countB_out;
  assign sub_flagB_out = PipelineBSubsystem__zz_sub_flagB_out;
  assign countB = sub_countB_out;
  assign flagB = sub_flagB_out;
  assign flagA = comparatorA_aboveReg;
  always @(posedge clk or posedge reset) begin
    if(reset) begin
      comparatorA_aboveReg <= 1'b0;
    end else begin
      comparatorA_aboveReg <= (8'h80 <= timerA_TimerSub_outSig);
    end
  end


endmodule
