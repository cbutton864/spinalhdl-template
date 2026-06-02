# SpinalHDL Dynamic & Flat Hybrid-Plugin Template

General-use SpinalHDL template demonstrating the **hybrid Fiber-Plugin architecture**. It supports compiling the design as a plain flat module for global area optimization, or generating modular, dynamic boundaries (`buildBlock`) for backend floorplanning, layout placement partitioning, timing exceptions, and selective simulation wave tracing.

All RTL logic core blocks reside within composable software plugins, completely automating manual register namespacing, bus port declarations, and cross-boundary signal routing.

## Quick Start

```bash
# Prerequisites: JDK 11+, sbt, Verilator (for simulation)

# Generate Verilog
sbt "runMain mydesign.GenVerilog"
# Output is compiled inside: target/tmp_rtl

# Run all unit tests (uses Verilator)
sbt test

# Run a specific unit test
sbt "testOnly mydesign.TimerCoreTest"

# Run integration tests (golden baseline checks)
sbt "it:test"
```

## Project Structure

- [build.sbt](build.sbt) — SpinalHDL and ScalaTest build dependencies configuration.
- [src/main/scala/mydesign/util/BuildHelper.scala](src/main/scala/mydesign/util/BuildHelper.scala) — Core compilation utilities featuring automatic reflective namespacing via `PrefixArea` and type-safe recursive input pulling via `autoPull` (with built-in high-severity `SpinalError` custom compiler diagnostic guard).
- [src/main/scala/mydesign/Params.scala](src/main/scala/mydesign/Params.scala) — Global hardware parameters, execution configuration (`DebugBuild` vs `ProductionBuild`), and plugin list.
- [src/main/scala/mydesign/Top.scala](src/main/scala/mydesign/Top.scala) — Dynamic top-level boundary wrapper, featuring consolidated APB3 port bundle interfaces to eliminate loose wire explosions.
- [src/main/scala/mydesign/GenVerilog.scala](src/main/scala/mydesign/GenVerilog.scala) — Main elaboration generation script for Verilog targets.
- [src/main/scala/mydesign/TopIoExportPlugin.scala](src/main/scala/mydesign/TopIoExportPlugin.scala) — The dynamic fiber routing export plugin, utilizing promises to resolve port declarations asynchronously.
- [src/main/scala/mydesign/PipelineTraits.scala](src/main/scala/mydesign/PipelineTraits.scala) — Declarative trait interfaces to pass inputs/outputs cleanly.
- [src/main/scala/mydesign/TimerPlugin.scala](src/main/scala/mydesign/TimerPlugin.scala) — Peripheral timer plugin wrapping [src/main/scala/mydesign/TimerCore.scala](src/main/scala/mydesign/TimerCore.scala).
- [src/main/scala/mydesign/ComparatorPlugin.scala](src/main/scala/mydesign/ComparatorPlugin.scala) — Signal comparator plugin.
- [src/main/scala/mydesign/ScalePlugin.scala](src/main/scala/mydesign/ScalePlugin.scala) — High-performance arithmetic compression pipeline plugin.
- [src/main/scala/mydesign/HysteresisPlugin.scala](src/main/scala/mydesign/HysteresisPlugin.scala) — Chatter reduction filtering plugin.
- [src/main/scala/mydesign/ApbMonitorPlugin.scala](src/main/scala/mydesign/ApbMonitorPlugin.scala) — Configurable APB3 register snooper.
- [src/it/scala/mydesign/GoldenIntegrationTest.scala](src/it/scala/mydesign/GoldenIntegrationTest.scala) — Integration test verifying generated output RTL matches established golden baselines.
- [src/test/scala/mydesign/TimerCoreTest.scala](src/test/scala/mydesign/TimerCoreTest.scala) — Simulation tests verification suites.

## Architecture Overview

See the detailed guide files at [docs/ARCHITECTURE.md](docs/ARCHITECTURE.md) and [docs/STYLE_GUIDE.md](docs/STYLE_GUIDE.md) for full explanations of the design rules and patterns.

**Key dynamic pattern:** Plugins wrap core hardware modules and publish asynchronous handles (`Handle[T]`). Downstream core blocks fetch these handles during elaboration, allowing completely order-independent connection of complex busses and signals.

```
  Params.plugins: Seq[FiberPlugin]
        │
        ▼
  ┌──────────────┐   Handle[UInt]   ┌──────────────────┐   Handle[Bool]
  │ TimerPlugin  │ ──────────────►  │ ComparatorPlugin │ ──────────────►
  │  └ TimerCore │                 │  └ ComparatorCore│
  └──────────────┘                  └──────────────────┘
        ▲                                                        │
        │ Handle[Bool]                                           │
  ┌─────┴──────────────────────────────────────────────────────┐ │
  │                 TopIoExportPlugin                           │◄┘
  │  Handles clock domain linking, assigns top-level APB3 bus, │
  │  and wires external diagnostic pins asynchronously.        │
  └────────────────────────────────────────────────────────────┘
```

## Using as a Template

1. **Clone this repository** to seed your workspace.
2. **Rename the Scala packages** to match your design namespaces.
3. **Configure parameters** in [src/main/scala/mydesign/Params.scala](src/main/scala/mydesign/Params.scala), selecting either a flat physical footprint (`ProductionBuild`) or partitioned layout blocks (`DebugBuild`).
4. **Declare physical interfaces** via cohesive bundles on your top-level boundary wrapper in [src/main/scala/mydesign/Top.scala](src/main/scala/mydesign/Top.scala).
5. **Add new plugins** that publish asynchronous signal handles to build complex pipelines.
6. **Utilize automated reflective prefixes** (`PrefixArea`) to capture logical register structures cleanly without naming-collision hazards.
7. **Pull signals dynamically** across component boundaries using automated lists (`buildBlock` and `autoPull`) in [src/main/scala/mydesign/util/BuildHelper.scala](src/main/scala/mydesign/util/BuildHelper.scala).
8. **Verify your block logic** under unit simulation files like [src/test/scala/mydesign/TimerCoreTest.scala](src/test/scala/mydesign/TimerCoreTest.scala) and declare golden baseline checks inside [src/it/scala/mydesign/GoldenIntegrationTest.scala](src/it/scala/mydesign/GoldenIntegrationTest.scala).

## Technical Dependencies

| Dependency | Version | Purpose |
|------------|---------|---------|
| Scala | 2.13.16 | Language and type elaboration |
| sbt | 1.10.11 | System builder tool |
| SpinalHDL | 1.14.0 | Advanced dynamic hardware description framework |
| ScalaTest | 3.2.18 | Unit validation framework |
| Verilator | 4.x+ | Simulation engine compiler |

## Common Developer Commands

| Command | Description |
|---------|-------------|
| `sbt "runMain mydesign.GenVerilog"` | Elaborate the hardware graph and generate Verilog targets |
| `sbt test` | Run entire unit test simulation suite |
| `sbt "testOnly mydesign.*CoreTest"` | Run tests matching specific core patterns |
| `sbt "it:test"` | Run integration checks comparing RTL emissions with baseline files |
| `sbt compile` | Quickly check for Scala compilation errors |

## License

MIT — feel free to use this template as a modern baseline structure for any FPGA or ASIC IP.
