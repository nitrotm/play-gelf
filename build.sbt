name := """ch.aducommun.gelf"""
version := "1.0-SNAPSHOT"

lazy val gelf = (project in file("."))

scalaVersion := "2.12.8"

libraryDependencies ++= Seq(
  "com.typesafe.play" %% "play-json" % "2.6.13",
  "io.netty" % "netty-all" % "4.1.42.Final",
  "org.slf4j" % "slf4j-api" % "1.7.28",
  "ch.qos.logback" % "logback-core" % "1.2.3",
  "ch.qos.logback" % "logback-classic" % "1.2.3",
)
