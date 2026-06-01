# Project Guidelines: SpinalHDL Flat-Plugin Template

This workspace uses the SpinalHDL Flat-Plugin and Component-Split Architecture. All modifications, upgrades, and feature additions must comply with these architectural principles and style guides to ensure generated RTL remains optimal, timing-closed, and highly readable.

---

## 1. Quick Navigation & References

For exhaustive design and style details, always consult:
- System Architecture: [docs/ARCHITECTURE.md](docs/ARCHITECTURE.md)
- Code Style Rules: [docs/STYLE_GUIDE.md](docs/STYLE_GUIDE.md)
- Dual Compilation Pipelines: [src/main/scala/mydesign/DualPipelineTop.scala](src/main/scala/mydesign/DualPipelineTop.scala)

---

## 2. Core Architectural Philosophy

### Plugin-Core Separation (Mandatory)
* **What stays in Plugins:** Only declaritive `Handle` boundaries, Fiber connections (`host[Trait]`), parameter routing, and wiring inside `during build new Area { ... }`. Strictly **no RTL logic statements** allowed in the plugin body.
* **What stays in Cores:** Pure, stateless, bus-agnostic RTL generation methods (typically `object XxxCore.build()`). They return a plain Scala case class `Io` enclosing hardware nodes (never a direction-carrying `Bundle`).

### Multi-Phase Elaboration Mechanics
To prevent compile-time logic deadlocks in the Fiber system, all wiring in the top exporter follows a strict sequence:
1. **Phase 1 (Non-Blocking Loads):** Inputs / configuration signals are broadcasted to the target plugins using `handle.load(padSignal)`.
2. **Phase 2 (Blocking Awaits):** The wiring block halts using `handle.await` on output values to bind downstream results to top-level pins.
* *Deadlock Prevention:* Never invoke `.await` before or during Phase 1. Complete all `.load` commands first.

### Interface Decoupling via Service Traits
- Do not make plugins depend on concrete sibling plugins.
- Define zero-hardware Scala traits (e.g. `ThresholdResult`, `EdgeResult`) to act as stage boundaries.
- Downstream plugins call `host[TraitName].outputHandle.await` to access data abstractly, allowing immediate plug-and-play swaps of intermediate stages inside parameter profiles.

---

## 3. Essential Workspace Commands

| Task | Shell Command | Notes |
|:---|:---|:---|
| **Build & Compile** | `sbt compile` | Validates Scala syntax and IDSL typing |
| **Run Unit Tests** | `sbt test` | Executes all simulation suites (keeps parallel run disabled) |
| **Verify Specific Test** | `sbt "testOnly mydesign.TimerCoreTest"` | Runs a single target test class with Verilator |
| **Regenerate Verilog** | `sbt "runMain mydesign.GenVerilog"` | Generates three distinct output directories in [rtl/](rtl/) |

---

## 4. Key Implementation Rules (For LLMs)

1. **Naming Conventions:** All generated registers in Cores must be wrapped in `PrefixArea` blocks to provide deterministic prefix strings in generated flat Verilog files (e.g. `timer_countReg`).
2. **Hierarchical Controls:** Every configurable module must support hierarchy injection via the global `BuildEnv` setting. Plugins must check `buildEnv.useHierarchy(...)` to decide whether to wrap Core logic inside standalone blocks (`buildBlock` or `buildSubsystem`).
3. **No Thread Slices:** In `build.sbt`, `parallelExecution` under the `Test` scope must always remain `false` to avoid Verilator compilations overlapping and corrupting cache segments.
