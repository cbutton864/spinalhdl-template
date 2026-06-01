// Generator : SpinalHDL v1.14.0    git head : 95a5e6c65c54acfc4707c8fe6ef8b5d297cfcbde
// Component : DualPipelineTop
// Git hash  : 3f206faed5e62aec749d5b088d08fba0028ecb27

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
  wire       [7:0]    PipelineBSubsystem_countOut;
  wire                PipelineBSubsystem_flagOut;
  wire                TimerPlugin_logic_enable;
  reg                 comparatorA_aboveReg;

  timerA_TimerSub timerA_TimerSub (
    .outSig       (timerA_TimerSub_outSig[7:0]), //o
    .pulledInputs (TimerPlugin_logic_enable   ), //i
    .clk          (clk                        ), //i
    .reset        (reset                      )  //i
  );
  PipelineBSubsystem PipelineBSubsystem (
    .sub_enable (enable                          ), //i
    .countOut   (PipelineBSubsystem_countOut[7:0]), //o
    .flagOut    (PipelineBSubsystem_flagOut      ), //o
    .clk        (clk                             ), //i
    .reset      (reset                           )  //i
  );
  assign TimerPlugin_logic_enable = enable;
  assign countA = timerA_TimerSub_outSig;
  assign countB = PipelineBSubsystem_countOut;
  assign flagB = PipelineBSubsystem_flagOut;
  assign flagA = comparatorA_aboveReg;
  always @(posedge clk or posedge reset) begin
    if(reset) begin
      comparatorA_aboveReg <= 1'b0;
    end else begin
      comparatorA_aboveReg <= (8'h80 <= timerA_TimerSub_outSig);
    end
  end


endmodule
