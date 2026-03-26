# SpinalHDL Flat-Plugin Template

A template project demonstrating the **FiberPlugin flat architecture** pattern for
SpinalHDL FPGA designs. All RTL logic lives in composable plugins — no component
hierarchy. Generates clean, flat Verilog ready for any vendor toolchain.

## Quick Start

```bash
# Prerequisites: JDK 11+, sbt, Verilator (for simulation)

# Generate Verilog
sbt "runMain mydesign.GenVerilog"
# Output: rtl/MyTop.v

# Run all tests
sbt test

# Run a specific test
sbt "testOnly mydesign.CounterCoreTest"
```

## Project Structure

```
├── build.sbt                            # SpinalHDL 1.14.0 + ScalaTest
├── src/main/scala/mydesign/
│   ├── Params.scala                     # Parameters + plugin list
│   ├── Top.scala                        # Top Component (IO only)
│   ├── GenVerilog.scala                 # Verilog generation
│   ├── CounterPlugin.scala              # Example: counter feature plugin
│   ├── CounterCore.scala                # Example: counter RTL logic
│   ├── ThresholdPlugin.scala            # Example: threshold comparator plugin
│   ├── ThresholdCore.scala              # Example: threshold RTL logic
│   └── TopIoExportPlugin.scala          # IO wiring hub
├── src/test/scala/mydesign/
│   ├── CounterCoreTest.scala            # Counter tests (4 tests)
│   ├── ThresholdCoreTest.scala          # Threshold tests (3 tests)
│   ├── ElaborationTest.scala            # Smoke test: design elaborates
│   └── testhelpers/
│       ├── CounterHarness.scala         # Sim wrapper for CounterCore
│       └── ThresholdHarness.scala       # Sim wrapper for ThresholdCore
├── docs/
│   └── ARCHITECTURE.md                  # Full design pattern documentation
└── rtl/                                 # Generated Verilog
```

## Architecture Overview

See [docs/ARCHITECTURE.md](docs/ARCHITECTURE.md) for the complete design guide.

**Key pattern:** Plugin wraps Core, publishes Handles. TopIoExportPlugin wires Handles to IO.

```
  Params.plugins: Seq[FiberPlugin]
        │
        ▼
  ┌──────────────┐   Handle[UInt]   ┌──────────────────┐   Handle[Bool]
  │CounterPlugin │ ──────────────►  │ThresholdPlugin   │ ──────────────►
  │  └ CounterCore│                 │  └ ThresholdCore │
  └──────────────┘                  └──────────────────┘
        ▲                                                        │
        │ Handle[Bool]                                           │
  ┌─────┴──────────────────────────────────────────────────────┐ │
  │                 TopIoExportPlugin                           │◄┘
  │  Phase 1: load inputs (enable → CounterPlugin.enableIn)    │
  │  Phase 2: await outputs (count, aboveFlag → MyTop.io)      │
  └────────────────────────────────────────────────────────────┘
```

## Using as a Template

1. **Clone/fork** this repo
2. **Rename the package** from `mydesign` to your project name
3. **Delete** the example plugins (`Counter*`, `Threshold*`) or keep them as reference
4. **Add your plugins** following the pattern in `docs/ARCHITECTURE.md`
5. **Update `Params.scala`** with your parameters and plugin list
6. **Update `Top.scala`** with your IO ports
7. **Update `TopIoExportPlugin.scala`** to wire your new Handles

## Dependencies

| Dependency | Version | Purpose |
|------------|---------|---------|
| Scala | 2.13.16 | Language |
| sbt | 1.10.11 | Build tool |
| SpinalHDL | 1.14.0 | HDL framework |
| ScalaTest | 3.2.18 | Test framework |
| Verilator | 4.x+ | RTL simulation |

## Commands

| Command | Description |
|---------|-------------|
| `sbt "runMain mydesign.GenVerilog"` | Generate Verilog to `rtl/` |
| `sbt test` | Run all tests |
| `sbt "testOnly mydesign.*"` | Run tests matching pattern |
| `sbt compile` | Compile without generating Verilog |

## License

MIT — feel free to use this template for any project.
