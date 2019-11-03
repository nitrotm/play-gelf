import interplay.ScalaVersions

name := """org.tmsrv.play.gelf"""
version := "1.0.0-SNAPSHOT"

val PlayVersion = "2.6.13"
val NettyVersion = "4.1.42.Final"
val Slf4jVersion = "1.7.28"
val LogbackVersion = "1.2.3"

lazy val root = (project in file("."))
  .settings(
    scalaVersion := ScalaVersions.scala212,
    crossScalaVersions := Seq("2.11.12", ScalaVersions.scala212, ScalaVersions.scala213),
    scalacOptions ++= Seq(
      "-target:jvm-1.8",
      "-deprecation",
      "-encoding", "UTF-8",
      "-feature",
      "-unchecked",

      "-Ywarn-unused:imports",
      "-Xlint:nullary-unit",

      "-Xlint",
      "-Ywarn-dead-code"
    ),
    javacOptions ++= Seq(
      "-Xlint:unchecked",
      "-Xlint:deprecation"
    ),
    libraryDependencies ++= Seq(
      "com.typesafe.play" %% "play-json" % PlayVersion,
      "io.netty" % "netty-all" % NettyVersion,
      "org.slf4j" % "slf4j-api" % Slf4jVersion,
      "ch.qos.logback" % "logback-core" % LogbackVersion,
      "ch.qos.logback" % "logback-classic" % LogbackVersion,
    ),
    // scalariformAutoformat := true,
    // ScalariformKeys.preferences := ScalariformKeys.preferences.value
    //   .setPreference(SpacesAroundMultiImports, true)
    //   .setPreference(SpaceInsideParentheses, false)
    //   .setPreference(DanglingCloseParenthesis, Preserve)
    //   .setPreference(PreserveSpaceBeforeArguments, true)
    //   .setPreference(DoubleIndentConstructorArguments, true),
  )

playBuildRepoName in ThisBuild := "play-gelf"
