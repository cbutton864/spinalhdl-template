ThisBuild / scalaVersion := "2.13.16"

lazy val root = (project in file("."))
  .configs(IntegrationTest)
  .settings(
    Defaults.itSettings,
    name := "spinalhdl-template",
    version := "0.1.0",

    // SpinalHDL 1.14.0
    libraryDependencies ++= Seq(
      "com.github.spinalhdl" %% "spinalhdl-core" % "1.14.0",
      "com.github.spinalhdl" %% "spinalhdl-lib"  % "1.14.0",
      "com.github.spinalhdl" %% "spinalhdl-sim"  % "1.14.0",

      // Testing (Available both to standard test and integration test compile scopes)
      "org.scalatest" %% "scalatest" % "3.2.18" % "test,it",

      // SpinalHDL compiler plugin — required for PostInitCallback.
      // Without this, Component constructors never call postInitCallback(),
      // so child Components (BlackBox, StreamFifoCC, etc.) never pop
      // themselves from the component stack, causing hierarchy violations.
      compilerPlugin("com.github.spinalhdl" %% "spinalhdl-idsl-plugin" % "1.14.0")
    ),

    // Spinal/Verilator compiles can step on each other if tests run in parallel.
    Test / parallelExecution := false,
    IntegrationTest / parallelExecution := false,

    // Forking tests is usually safer for native compilers and cleaner stdout.
    Test / fork := true,
    IntegrationTest / fork := true,

    // Directories
    Test / scalaSource := baseDirectory.value / "src" / "test" / "scala",
    IntegrationTest / scalaSource := baseDirectory.value / "src" / "it" / "scala"
  )
