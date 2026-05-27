// Generator : SpinalHDL v1.14.0    git head : 95a5e6c65c54acfc4707c8fe6ef8b5d297cfcbde
// Component : MyTop
// Git hash  : ff19e29ce3b916d620956902527df4458707c875

`timescale 1ns/1ps 
module MyTop (
  input  wire          enable,
  output wire [7:0]    count,
  output wire          above_flag,
  output wire          rising_edge,
  output wire          falling_edge,
  input  wire [7:0]    apb_PADDR,
  input  wire [0:0]    apb_PSEL,
  input  wire          apb_PENABLE,
  output wire          apb_PREADY,
  input  wire          apb_PWRITE,
  input  wire [31:0]   apb_PWDATA,
  output wire [31:0]   apb_PRDATA,
  output wire          apb_PSLVERR,
  input  wire          clk,
  input  wire          reset
);

  wire       [7:0]    timer_TimerSub_outSig;
  wire                TimerPlugin_logic_enable;
  reg                 comparator_aboveReg;

  timer_TimerSub timer_TimerSub (
    .outSig       (timer_TimerSub_outSig[7:0]), //o
    .pulledInputs (TimerPlugin_logic_enable  ), //i
    .clk          (clk                       ), //i
    .reset        (reset                     )  //i
  );
  assign TimerPlugin_logic_enable = enable;
  assign count = timer_TimerSub_outSig;
  assign above_flag = comparator_aboveReg;
  assign rising_edge = 1'b0;
  assign falling_edge = 1'b0;
  assign apb_PREADY = 1'b1;
  assign apb_PRDATA = 32'h0;
  assign apb_PSLVERR = 1'b0;
  always @(posedge clk or posedge reset) begin
    if(reset) begin
      comparator_aboveReg <= 1'b0;
    end else begin
      comparator_aboveReg <= (8'h80 <= timer_TimerSub_outSig);
    end
  end


endmodule
