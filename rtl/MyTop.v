// Generator : SpinalHDL v1.14.0    git head : 95a5e6c65c54acfc4707c8fe6ef8b5d297cfcbde
// Component : MyTop
// Git hash  : f3eb254c121a5af402fe1cd0e1318ad72411fc23

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

  wire       [5:0]    _zz_ScalePlugin_processedOut;
  wire       [31:0]   _zz_ApbMonitorPlugin_logic_apb_PRDATA;
  wire       [31:0]   _zz_ApbMonitorPlugin_logic_apb_PRDATA_1;
  wire       [0:0]    _zz_ApbMonitorPlugin_logic_apb_PRDATA_2;
  wire       [7:0]    ApbMonitorPlugin_logic_apb_PADDR;
  wire       [0:0]    ApbMonitorPlugin_logic_apb_PSEL;
  wire                ApbMonitorPlugin_logic_apb_PENABLE;
  wire                ApbMonitorPlugin_logic_apb_PREADY;
  wire                ApbMonitorPlugin_logic_apb_PWRITE;
  wire       [31:0]   ApbMonitorPlugin_logic_apb_PWDATA;
  reg        [31:0]   ApbMonitorPlugin_logic_apb_PRDATA;
  wire                ApbMonitorPlugin_logic_apb_PSLVERROR;
  wire                ApbMonitorPlugin_logic_factory_readErrorFlag;
  wire                ApbMonitorPlugin_logic_factory_writeErrorFlag;
  wire                ApbMonitorPlugin_logic_factory_askWrite;
  wire                ApbMonitorPlugin_logic_factory_askRead;
  wire                ApbMonitorPlugin_logic_factory_doWrite;
  wire                ApbMonitorPlugin_logic_factory_doRead;
  reg        [7:0]    timer_countReg;
  wire       [7:0]    ScalePlugin_processedOut;
  reg                 hysteresis_aboveReg;
  wire                when_HysteresisPlugin_l39;
  wire                when_HysteresisPlugin_l40;
  reg                 edgeDetector_prev;
  reg                 edgeDetector_rising;
  reg                 edgeDetector_falling;

  assign _zz_ScalePlugin_processedOut = (timer_countReg >>> 2'd2);
  assign _zz_ApbMonitorPlugin_logic_apb_PRDATA = {24'd0, timer_countReg};
  assign _zz_ApbMonitorPlugin_logic_apb_PRDATA_2 = hysteresis_aboveReg;
  assign _zz_ApbMonitorPlugin_logic_apb_PRDATA_1 = {31'd0, _zz_ApbMonitorPlugin_logic_apb_PRDATA_2};
  assign ApbMonitorPlugin_logic_apb_PADDR = apb_PADDR;
  assign ApbMonitorPlugin_logic_apb_PSEL = apb_PSEL;
  assign ApbMonitorPlugin_logic_apb_PENABLE = apb_PENABLE;
  assign ApbMonitorPlugin_logic_apb_PWRITE = apb_PWRITE;
  assign ApbMonitorPlugin_logic_apb_PWDATA = apb_PWDATA;
  assign apb_PREADY = ApbMonitorPlugin_logic_apb_PREADY;
  assign apb_PRDATA = ApbMonitorPlugin_logic_apb_PRDATA;
  assign apb_PSLVERR = ApbMonitorPlugin_logic_apb_PSLVERROR;
  assign ApbMonitorPlugin_logic_factory_readErrorFlag = 1'b0;
  assign ApbMonitorPlugin_logic_factory_writeErrorFlag = 1'b0;
  assign ApbMonitorPlugin_logic_apb_PREADY = 1'b1;
  always @(*) begin
    ApbMonitorPlugin_logic_apb_PRDATA = 32'h0;
    case(ApbMonitorPlugin_logic_apb_PADDR)
      8'h0 : begin
        ApbMonitorPlugin_logic_apb_PRDATA[31 : 0] = _zz_ApbMonitorPlugin_logic_apb_PRDATA;
      end
      8'h04 : begin
        ApbMonitorPlugin_logic_apb_PRDATA[31 : 0] = _zz_ApbMonitorPlugin_logic_apb_PRDATA_1;
      end
      default : begin
      end
    endcase
  end

  assign ApbMonitorPlugin_logic_factory_askWrite = ((ApbMonitorPlugin_logic_apb_PSEL[0] && ApbMonitorPlugin_logic_apb_PENABLE) && ApbMonitorPlugin_logic_apb_PWRITE);
  assign ApbMonitorPlugin_logic_factory_askRead = ((ApbMonitorPlugin_logic_apb_PSEL[0] && ApbMonitorPlugin_logic_apb_PENABLE) && (! ApbMonitorPlugin_logic_apb_PWRITE));
  assign ApbMonitorPlugin_logic_factory_doWrite = (((ApbMonitorPlugin_logic_apb_PSEL[0] && ApbMonitorPlugin_logic_apb_PENABLE) && ApbMonitorPlugin_logic_apb_PREADY) && ApbMonitorPlugin_logic_apb_PWRITE);
  assign ApbMonitorPlugin_logic_factory_doRead = (((ApbMonitorPlugin_logic_apb_PSEL[0] && ApbMonitorPlugin_logic_apb_PENABLE) && ApbMonitorPlugin_logic_apb_PREADY) && (! ApbMonitorPlugin_logic_apb_PWRITE));
  assign ApbMonitorPlugin_logic_apb_PSLVERROR = ((ApbMonitorPlugin_logic_factory_doWrite && ApbMonitorPlugin_logic_factory_writeErrorFlag) || (ApbMonitorPlugin_logic_factory_doRead && ApbMonitorPlugin_logic_factory_readErrorFlag));
  assign ScalePlugin_processedOut = {2'd0, _zz_ScalePlugin_processedOut};
  assign count = timer_countReg;
  assign when_HysteresisPlugin_l39 = (8'h30 <= ScalePlugin_processedOut);
  assign when_HysteresisPlugin_l40 = (ScalePlugin_processedOut < 8'h10);
  assign above_flag = hysteresis_aboveReg;
  assign rising_edge = edgeDetector_rising;
  assign falling_edge = edgeDetector_falling;
  always @(posedge clk or posedge reset) begin
    if(reset) begin
      timer_countReg <= 8'h0;
      hysteresis_aboveReg <= 1'b0;
      edgeDetector_prev <= 1'b0;
      edgeDetector_rising <= 1'b0;
      edgeDetector_falling <= 1'b0;
    end else begin
      if(enable) begin
        timer_countReg <= (timer_countReg + 8'h01);
      end
      if(when_HysteresisPlugin_l39) begin
        hysteresis_aboveReg <= 1'b1;
      end
      if(when_HysteresisPlugin_l40) begin
        hysteresis_aboveReg <= 1'b0;
      end
      edgeDetector_prev <= hysteresis_aboveReg;
      edgeDetector_rising <= (hysteresis_aboveReg && (! edgeDetector_prev));
      edgeDetector_falling <= ((! hysteresis_aboveReg) && edgeDetector_prev);
    end
  end


endmodule
