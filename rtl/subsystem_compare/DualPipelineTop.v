// Generator : SpinalHDL v1.14.0    git head : 95a5e6c65c54acfc4707c8fe6ef8b5d297cfcbde
// Component : DualPipelineTop
// Git hash  : 31194528dbdc9d465f9b5015904854658df8641f

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
    .outSig             (timerA_TimerSub_outSig[7:0]), //o
    .when_TimerCore_l36 (TimerPlugin_logic_enable   ), //i
    .clk                (clk                        ), //i
    .reset              (reset                      )  //i
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
    .sub_timer_count    (timerB_TimerCoreSub_sub_timer_count[7:0]), //o
    .when_TimerCore_l36 (sub_enable                              ), //i
    .clk                (clk                                     ), //i
    .reset              (reset                                   )  //i
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

module timerA_TimerSub (
  output wire [7:0]    outSig,
  input  wire          when_TimerCore_l36,
  input  wire          clk,
  input  wire          reset
);

  reg        [7:0]    timerA_countReg;

  assign outSig = timerA_countReg;
  always @(posedge clk or posedge reset) begin
    if(reset) begin
      timerA_countReg <= 8'h0;
    end else begin
      if(when_TimerCore_l36) begin
        timerA_countReg <= (timerA_countReg + 8'h01);
      end
    end
  end


endmodule

module timerB_TimerCoreSub (
  output wire [7:0]    sub_timer_count,
  input  wire          when_TimerCore_l36,
  input  wire          clk,
  input  wire          reset
);

  reg        [7:0]    timerB_countReg;

  assign sub_timer_count = timerB_countReg;
  always @(posedge clk or posedge reset) begin
    if(reset) begin
      timerB_countReg <= 8'h0;
    end else begin
      if(when_TimerCore_l36) begin
        timerB_countReg <= (timerB_countReg + 8'h01);
      end
    end
  end


endmodule
