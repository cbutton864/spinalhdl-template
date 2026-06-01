---
name: sbt-commands
description: 'Workflow skill for running sbt compilation, simulation unit execution, single Verilator checks, and regenerating flat, hierarchical, or split-file Verilog targets.'
argument-hint: 'compile, test, testOnly, runMain'
user-invocable: true
disable-model-invocation: false
---

# sbt Reference and Command Sequences

This skill provides the standard step-by-step procedures to build, compile, test, and generate RTL within the SpinalHDL Flat-Plugin template project.

## When to Use
- Validating Scala syntax and IDSL typing inside the project.
- Executing unit simulation suites or custom verification tests using Verilator.
- Regenerating different target styles of hardware descriptions (flat vs hierarchical vs component-split).

## Standard Procedures

### Procedure 1: Build & Syntax Check
Run this command periodically or in a watcher loop to verify typings, parameter bounds, and Scala file relationships:
```bash
sbt compile
```

### Procedure 2: Execute All Verification Tests
SpinalHDL simulation test classes run native Verilator builds inside isolated cache paths. To verify all 34 simulation checks:
```bash
sbt test
```
*Note:* The template disables parallel execution (`parallelExecution := false` in build.sbt) to prevent cache collision corruption in Verilator temporary workspaces.

### Procedure 3: Run a Single Targeted Simulation
To speed up development, compile and execute simulation stimulus on a specific target test block only:
```bash
sbt "testOnly mydesign.TimerCoreTest"
```

### Procedure 4: Regenerate Physical Verilog Outputs
Runs the generation main script to compile Scala descriptions into synthesis-ready Verilog targets (writing to flat, hierarchical, and component-split subsystems):
```bash
sbt "runMain mydesign.GenVerilog"
```
