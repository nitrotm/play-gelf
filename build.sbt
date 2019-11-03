lazy val `play-gelf` = (project in file("."))

organization := "org.tmsrv"
name := "play-gelf"

homepage := Some(url("https://github.com/nitrotm/play-gelf"))
licenses := Seq("Apache-2.0" -> url("http://www.apache.org/licenses/LICENSE-2.0"))

scmInfo := Some(ScmInfo(url("https://github.com/nitrotm/play-gelf"), "scm:git@github.com:nitrotm/play-gelf.git"))
developers := List(Developer(id="nitrotm", name="Antony Ducommun", email="nitro@tmsrv.org", url=url("https://www.tmsrv.org")))

crossScalaVersions := Seq("2.12.10")

val playVersion = "2.6.13"
val nettyVersion = "4.1.42.Final"
val slf4jVersion = "1.7.28"
val logbackVersion = "1.2.3"

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
  "com.typesafe.play" %% "play-json" % playVersion,
  "io.netty" % "netty-all" % nettyVersion,
  "org.slf4j" % "slf4j-api" % slf4jVersion,
  "ch.qos.logback" % "logback-core" % logbackVersion,
  "ch.qos.logback" % "logback-classic" % logbackVersion,
)

releaseTagName := (version in ThisBuild).value

bintrayOrganization := None
bintrayRepository := "maven"
bintrayPackage := "play-gelf"
bintrayReleaseOnPublish := false

// scalariformAutoformat := true,
// ScalariformKeys.preferences := ScalariformKeys.preferences.value
//   .setPreference(SpacesAroundMultiImports, true)
//   .setPreference(SpaceInsideParentheses, false)
//   .setPreference(DanglingCloseParenthesis, Preserve)
//   .setPreference(PreserveSpaceBeforeArguments, true)
//   .setPreference(DoubleIndentConstructorArguments, true),
