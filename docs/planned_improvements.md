# Recommended SpinalHDL Refactoring Action Plan

Below is a detailed analysis of the recommended architectural improvements for `spinalhdl-template`. For each change, we break down the **conceptual mechanics**, the **refactoring complexity**, the **scope of affected files**, and the direct **impact** on the generated RTL.

---

## 1. Automated Name Prefixing inside Cores (Automated Namespacing)
Currently, to prevent name collisions in the production flat Verilog build, every register must be manually prefixed and renamed (e.g., `countReg.setName(s"${periphName}_countReg")`).

### Mechanics
We can create a custom `Area` class (e.g., `PrefixArea`) that overrides or intercepts the naming callbacks for all signals declared inside its scope. During SpinalHDL's elaboration phase, it walks the signal hierarchy of that prefix area, automatically prefixing any contained `Reg` or `Mem` with a provided suffix/prefix.
Alternatively, we can write a clean Scala wrapper that applies prefixing to all child components/areas.

### Refactoring Complexity: **Low**
- **Saves boilerplate:** Highly effective at eliminating human error (forgetting a `setName`).
- **No changes to logic:** Simply cleans up the core definitions.

### Scope of Affected Files
- **New helper:** `src/main/scala/mydesign/util/PrefixArea.scala` (or add to [src/main/scala/mydesign/util/BuildHelper.scala](src/main/scala/mydesign/util/BuildHelper.scala))
- **Cores cleanup:** [src/main/scala/mydesign/TimerCore.scala](src/main/scala/mydesign/TimerCore.scala), [src/main/scala/mydesign/EdgeDetectorCore.scala](src/main/scala/mydesign/EdgeDetectorCore.scala), [src/main/scala/mydesign/ComparatorCore.scala](src/main/scala/mydesign/ComparatorCore.scala)

---

## 2. Standard Bus Bundles in Top (Port Cleanup)
Currently, [src/main/scala/mydesign/Top.scala](src/main/scala/mydesign/Top.scala) flattens the APB3 slave port manually into 8 separate discrete signals, then manually renames each one.

### Mechanics
We replace the 8 discrete ports in `MyTop` with a single `val apb = slave(Apb3(Apb3Config(...)))` port. We then write or use a small renaming walker in `Top` that iterates through all signals within the nested bus bundle and strips/replaces their names (e.g., setting the pin name directly to `apb_PADDR`, `apb_PWDATA`, etc., to match ARM APB3 specifications).

### Refactoring Complexity: **Low to Medium**
- **SpinalHDL native:** Embraces Spinal's native `Apb3` and Master/Slave port directions.
- **Top Connection:** Simplifies downstream wiring in the TopIO plugin.

### Scope of Affected Files
- [src/main/scala/mydesign/Top.scala](src/main/scala/mydesign/Top.scala)
- [src/main/scala/mydesign/ApbMonitorPlugin.scala](src/main/scala/mydesign/ApbMonitorPlugin.scala)
- [src/main/scala/mydesign/TopIoExportPlugin.scala](src/main/scala/mydesign/TopIoExportPlugin.scala)

---

## 3. Automated Input Pulling in `BuildHelper.buildBlock`
When hierarchical mode is configured, signals that are declared in the parent component and consumed inside the subcomponent must have `.pull()` called on them manually to resolve physical hierarchy cross-routing.

### Mechanics
We can augment `BuildHelper.buildBlock` / `buildSubsystem` to automatically trace the inputs being captured by the inner logic. SpinalHDL has an internal representation of the signal graph. By intercepting expressions or creating a custom `Component` subclass, we can automatically traverse and identify signals that belong to a parent clock domain or a parent component context, and automatically invoke `.pull()` on them during elaboration.

### Refactoring Complexity: **Medium to High**
- Requires deeper knowledge of SpinalHDL's Fiber mechanics and elaboration-phase signal parent checks.
- Extremely beneficial because it hides complex hierarchical routing code from regular plugin developments.

### Scope of Affected Files
- [src/main/scala/mydesign/util/BuildHelper.scala](src/main/scala/mydesign/util/BuildHelper.scala)
- Plugins using the hierarchical builder: [src/main/scala/mydesign/TimerPlugin.scala](src/main/scala/mydesign/TimerPlugin.scala)

---

## 4. Integration with `spinal.lib.misc.pipeline`
Instead of using custom Scala traits and monadic `Handle[T]` objects to pass floating wires, we can utilize SpinalHDL's formal `pipeline` framework.

### Mechanics
In `spinal.lib.misc.pipeline`, stages are represented by explicit `Stage` objects. Data is passed from one stage to another using typed `Payload[T]` constructs. Controls (stalls, handshakes, valid/ready) are automatically propagated down the stages. 

### Refactoring Complexity: **High**
- Completely changes the communication pattern between plugins.
- Highly recommended for modern SpinalHDL pipelines, but changes the overall simplicity of the template's trait structures.

### Scope of Affected Files
- Almost all plugins and traits files: [src/main/scala/mydesign/PipelineTraits.scala](src/main/scala/mydesign/PipelineTraits.scala), [src/main/scala/mydesign/Params.scala](src/main/scala/mydesign/Params.scala), [src/main/scala/mydesign/TimerPlugin.scala](src/main/scala/mydesign/TimerPlugin.scala), etc.

---

## Step-by-Step Selection

Which change would you like to explore and execute first? We recommend starting with **Change 1 (Automated Name Prefixing)** or **Change 2 (APB3 Bus Bundles)**, since they offer instantaneous, clean, and low-risk design improvements before diving into the more advanced internal framework transformations.
