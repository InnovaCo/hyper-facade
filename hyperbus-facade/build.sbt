organization := "eu.inn"

name := "hyperbus-facade"

version := "1.0"

scalaVersion := "2.11.7"

libraryDependencies ++= Seq(
  "eu.inn" %% "hyperbus" % "0.1.SNAPSHOT",
  "eu.inn" %% "hyperbus-model" % "0.1.SNAPSHOT",
  "eu.inn" %% "hyperbus-t-kafka" % "0.1.SNAPSHOT",
  "jline" % "jline" % "2.12.1",
  "org.scalatest" %% "scalatest" % "2.2.1" % "test"
)

addCompilerPlugin("org.scalamacros" % "paradise" % "2.1.0-M5" cross CrossVersion.full)