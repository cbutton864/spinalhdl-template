// Generator : SpinalHDL v1.14.0    git head : 95a5e6c65c54acfc4707c8fe6ef8b5d297cfcbde
// Component : MyTop
// Git hash  : 32fd2f85cb83b2bc36ccb2d191467bb238a6f5e2

`timescale 1ns/1ps

module MyTop (
  input  wire          enable,
  output wire [7:0]    count,
  output wire          above_flag,
  output wire          rising_edge,
  output wire          falling_edge,
  input  wire [7:0]    apb_PADDR,
  input  wire          apb_PSEL,
  input  wire          apb_PENABLE,
  input  wire          apb_PWRITE,
  input  wire [31:0]   apb_PWDATA,
  output wire          apb_PREADY,
  output wire [31:0]   apb_PRDATA,
  output wire          apb_PSLVERR,
  input  wire          clk,
  input  wire          reset
);

  wire                TimerPlugin_logic_enable;
  wire       [7:0]    TimerPlugin_logic_timerCount;
  reg        [7:0]    timer_countReg;
  reg                 comparator_aboveReg;

  assign TimerPlugin_logic_enable = enable;
  assign TimerPlugin_logic_timerCount = timer_countReg;
  assign count = TimerPlugin_logic_timerCount;
  assign above_flag = comparator_aboveReg;
  assign rising_edge = 1'b0;
  assign falling_edge = 1'b0;
  assign apb_PREADY = 1'b1;
  assign apb_PRDATA = 32'h0;
  assign apb_PSLVERR = 1'b0;
  always @(posedge clk or posedge reset) begin
    if(reset) begin
      timer_countReg <= 8'h0;
      comparator_aboveReg <= 1'b0;
    end else begin
      if(TimerPlugin_logic_enable) begin
        timer_countReg <= (timer_countReg + 8'h01);
      end
      comparator_aboveReg <= (8'h80 <= TimerPlugin_logic_timerCount);
    end
  end


endmodule
