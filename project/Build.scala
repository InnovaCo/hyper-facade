import sbt.Keys._
import sbt._

object Build extends sbt.Build {
  val projectMajorVersion = settingKey[String]("Defines the major version number")
  val projectBuildNumber = settingKey[String]("Defines the build number")

  override lazy val settings =
    super.settings ++ Seq(
      organization := "eu.inn",
      scalaVersion := "2.11.7",
      projectMajorVersion := "0.1",
      projectBuildNumber := "SNAPSHOT",
      version := projectMajorVersion.value + "." + projectBuildNumber.value,

      scalacOptions ++= Seq(
        "-feature",
        "-deprecation",
        "-unchecked",
        "-optimise",
        "-target:jvm-1.7",
        "-encoding", "UTF-8"
      ),

      javacOptions ++= Seq(
        "-source", "1.7",
        "-target", "1.7",
        "-encoding", "UTF-8",
        "-Xlint:unchecked",
        "-Xlint:deprecation"
      ),

      publishTo := Some("Innova libs repo" at "http://repproxy.srv.inn.ru/artifactory/libs-release-local"),

      credentials += Credentials(Path.userHome / ".ivy2" / ".innova_credentials")
    )

  lazy val `hyperbus-facade` = project.in(file("hyperbus-facade"))
}
