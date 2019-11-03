lazy val `play-gelf` = (project in file("."))

val PlayVersion = "2.6.13"
val NettyVersion = "4.1.42.Final"
val Slf4jVersion = "1.7.28"
val LogbackVersion = "1.2.3"

organization := "org.tmsrv"
name := """org.tmsrv.play.gelf"""

homepage := Some(url("https://github.com/nitrotm/play-gelf"))
licenses := Seq("Apache-2.0" -> url("http://www.apache.org/licenses/LICENSE-2.0"))

releaseTagName := (version in ThisBuild).value

bintrayOrganization := None
bintrayRepository := "maven"
bintrayPackage := "play-gelf"
bintrayReleaseOnPublish := false

scalacOptions ++= Seq(
  "-deprecation",
  "-feature",
  "-unchecked",
)

scalacOptions ++= PartialFunction.condOpt(CrossVersion.partialVersion(scalaVersion.value)) {
  case Some((2, v)) if v >= 11 => Seq("-Ywarn-unused:imports")
}.toList.flatten

javacOptions ++= Seq(
  "-Xlint:unchecked",
  "-Xlint:deprecation"
)

libraryDependencies ++= Seq(
  "com.typesafe.play" %% "play-json" % PlayVersion,
  "io.netty" % "netty-all" % NettyVersion,
  "org.slf4j" % "slf4j-api" % Slf4jVersion,
  "ch.qos.logback" % "logback-core" % LogbackVersion,
  "ch.qos.logback" % "logback-classic" % LogbackVersion,
)

// scalariformAutoformat := true,
// ScalariformKeys.preferences := ScalariformKeys.preferences.value
//   .setPreference(SpacesAroundMultiImports, true)
//   .setPreference(SpaceInsideParentheses, false)
//   .setPreference(DanglingCloseParenthesis, Preserve)
//   .setPreference(PreserveSpaceBeforeArguments, true)
//   .setPreference(DoubleIndentConstructorArguments, true),
