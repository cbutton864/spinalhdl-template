package mydesign.util

import spinal.core._
import spinal.lib._

// Named wrapper block. Saves manual prefixing on child nets.
class PrefixArea(prefix: String) extends Area {
  this.setName(prefix)
}

// BuildMode / BuildEnv — build strategy control.
// Plugins accept a `buildEnv: BuildEnv` parameter and call `buildEnv.useHierarchy(default)`
// when deciding whether to create a Component boundary. Set the mode once in Params and it
// propagates to every plugin without per-plugin flag management.
//   FlatBuild         → always flat   (pluginDefault ignored)
//   HierarchicalBuild → always hier   (pluginDefault ignored)
//   CustomBuild       → uses pluginDefault (each plugin decides independently)
sealed trait BuildMode
case object HierarchicalBuild extends BuildMode // component hierarchy for floorplanning, subsystem partitions, wave tracing
case object FlatBuild         extends BuildMode // flat RTL, no Component boundaries, maximum global synthesis optimisation
case object CustomBuild       extends BuildMode // per-plugin default

case class BuildEnv(
    mode: BuildMode = FlatBuild,
    globalHierarchy: Option[Boolean] = None
) {
  def useHierarchy(pluginDefault: Boolean): Boolean = {
    globalHierarchy.getOrElse {
      mode match {
        case HierarchicalBuild => true
        case FlatBuild         => false
        case CustomBuild       => pluginDefault
      }
    }
  }
}

object BuildHelper {
  // Recursively calls .pull() on Data wires inside tuples, lists, or Options when hierarchical
  // is on. Use for *reading* signals across a boundary. For an input the child must also drive
  // back on (e.g. a Stream's `ready`), use buildBlock's `inputs` overload — it builds proper
  // direction-correct ports via prepareInput, which plain .pull() cannot express.
  def autoPull[T](signal: T, enabled: Boolean): T = {
    if (!enabled) signal
    else {
      signal match {
        case d: Data => d.pull().asInstanceOf[T]
        case (a, b) =>
          (autoPull(a, true), autoPull(b, true)).asInstanceOf[T]
        case (a, b, c) =>
          (autoPull(a, true), autoPull(b, true), autoPull(c, true)).asInstanceOf[T]
        case (a, b, c, d) =>
          (autoPull(a, true), autoPull(b, true), autoPull(c, true), autoPull(d, true)).asInstanceOf[T]
        case seq: Seq[_] =>
          seq.map(item => autoPull(item, true)).asInstanceOf[T]
        case opt: Option[_] =>
          opt.map(item => autoPull(item, true)).asInstanceOf[T]
        case other =>
          SpinalError(
            s"Auto-pulling failed: ${other.getClass.getName} is not a supported wire or connection type! " +
            s"Make sure you only pass Spinal Data signals, Tuples of signals, " +
            s"or standard Sequences into the buildBlock helper parameter list."
          )
          other
      }
    }
  }

  // Builds a direction-correct child input port for `input` and returns it paired with a thunk
  // that connects it from the parent scope (run after the child Component closes).
  //   Stream[_]               -> slave port, connected with `<<`  (so `ready` flows back out)
  //   IMasterSlave with Data  -> slave port, connected with `<>`
  //   Data                    -> `in` port, connected with `:=`
  //   tuples / Seq / Option   -> recurse element-wise
  // This is what lets a Stream (or any handshake) cross a module boundary correctly; a plain
  // `.pull()` can only express the forward direction.
  def prepareInput[I](input: I): (I, () => Unit) = {
    input match {
      case s: Stream[_] =>
        val port = slave(cloneOf(s)).asInstanceOf[s.type]
        (port.asInstanceOf[I], () => { port << s })

      case ms: IMasterSlave with Data =>
        val port = slave(cloneOf(ms)).asInstanceOf[ms.type]
        (port.asInstanceOf[I], () => { port.asInstanceOf[Data] <> ms.asInstanceOf[Data] })

      case d: Data =>
        val port = in(cloneOf(d)).asInstanceOf[d.type]
        (port.asInstanceOf[I], () => { port := d })

      case (a, b) =>
        val (pa, ca) = prepareInput(a)
        val (pb, cb) = prepareInput(b)
        ((pa, pb).asInstanceOf[I], () => { ca(); cb() })

      case (a, b, c) =>
        val (pa, ca) = prepareInput(a)
        val (pb, cb) = prepareInput(b)
        val (pc, cc) = prepareInput(c)
        ((pa, pb, pc).asInstanceOf[I], () => { ca(); cb(); cc() })

      case (a, b, c, d) =>
        val (pa, ca) = prepareInput(a)
        val (pb, cb) = prepareInput(b)
        val (pc, cc) = prepareInput(c)
        val (pd, cd) = prepareInput(d)
        ((pa, pb, pc, pd).asInstanceOf[I], () => { ca(); cb(); cc(); cd() })

      case seq: Seq[_] =>
        val prepped = seq.map(item => prepareInput(item))
        val ports   = prepped.map(_._1)
        val conns   = prepped.map(_._2)
        (ports.asInstanceOf[I], () => conns.foreach(_()))

      case opt: Option[_] =>
        val prepped = opt.map(item => prepareInput(item))
        val ports   = prepped.map(_._1)
        val conns   = prepped.map(_._2)
        (ports.asInstanceOf[I], () => conns.foreach(_()))

      case other =>
        (other, () => {})
    }
  }

  // Names prepared input ports (hierarchical only — they are fresh ports there). `names` maps
  // positionally onto the top-level elements of the prepared structure (a single signal, or the
  // members of a tuple / Seq). Skipped in flat mode, where the body gets the parent's own signals
  // and renaming them would leak across the flat design.
  private def nameInputs(prepared: Any, names: Seq[String]): Unit = {
    if (names.isEmpty) return
    def nameAt(value: Any, idx: Int): Unit =
      if (idx < names.length) value match {
        case d: Data => d.setName(names(idx))
        case _       =>
      }
    prepared match {
      case (a, b)       => nameAt(a, 0); nameAt(b, 1)
      case (a, b, c)    => nameAt(a, 0); nameAt(b, 1); nameAt(c, 2)
      case (a, b, c, d) => nameAt(a, 0); nameAt(b, 1); nameAt(c, 2); nameAt(d, 3)
      case seq: Seq[_]  => seq.zipWithIndex.foreach { case (v, i) => nameAt(v, i) }
      case single       => nameAt(single, 0)
    }
  }

  // Wraps a block in a subcomponent for floorplanning, partitions, and tracing logic.
  // WARNING: the body must not capture signals from the enclosing scope when hierarchical=true.
  // Any such reference crosses a Component boundary without a .pull(), which triggers
  // PhaseCheckHierarchy errors. Use the inputs overload below for any signal that needs
  // to cross the boundary — it calls prepareInput automatically and supports port naming.
  def buildBlock[T <: Data](
      outputType: HardType[T],
      hierarchical: Boolean,
      name: String
  )(body: T => Unit): T = {
    if (hierarchical) {
      val block = new Component {
        val outSig = outputType() match {
          case ms: IMasterSlave => master(ms).asInstanceOf[T]
          case other            => out(other)
        }
        body(outSig)
      }
      block.setDefinitionName(name)
      block.setName(name)
      block.outSig
    } else {
      val sig = outputType()
      body(sig)
      sig
    }
  }

  // Wraps a block in a subcomponent, building direction-correct input ports via prepareInput and
  // deferring the parent-side connection until after the child closes. `outName` / `inNames`
  // rename the output and input ports for a readable review-build module signature (hierarchical
  // only); `inNames` maps positionally onto the top-level elements of `inputs`.
  def buildBlock[T <: Data, K](
      outputType: HardType[T],
      hierarchical: Boolean,
      name: String,
      inputs: K,
      outName: String      = null,
      inNames: Seq[String] = Nil
  )(body: K => T => Unit): T = {
    if (hierarchical) {
      var connectionFn: () => Unit = null
      val block = new Component {
        val outSig = outputType() match {
          case ms: IMasterSlave => master(ms).asInstanceOf[T]
          case other            => out(other)
        }
        if (outName != null) outSig.setName(outName)
        val (preparedInputs, conn) = prepareInput(inputs)
        nameInputs(preparedInputs, inNames)
        connectionFn = conn
        body(preparedInputs)(outSig)
      }
      connectionFn()
      block.setDefinitionName(name)
      block.setName(name)
      block.outSig
    } else {
      val sig = outputType()
      body(inputs)(sig)
      sig
    }
  }

  // Groups cores into a single block. Helpful for physical placement.
  // Returns the body's result so callers can capture internal signals and cross
  // the boundary with autoPull before loading parent-scope Handles.
  def buildSubsystem[T](
      hierarchical: Boolean,
      name: String
  )(body: => T): T = {
    if (hierarchical) {
      var result: T = null.asInstanceOf[T]
      val block = new Component {
        result = body
      }
      block.setDefinitionName(name)
      block.setName(name)
      result
    } else {
      body
    }
  }
}
