import sbt.Keys._
import sbt._

object Build extends sbt.Build {

  val projectMajorVersion = settingKey[String]("Defines the major version number")
  val projectBuildNumber = settingKey[String]("Defines the build number")

  override lazy val settings =
    super.settings ++ Seq(
        organization := "eu.inn",
        scalaVersion := "2.11.8",
        projectMajorVersion := "0.1",
        projectBuildNumber := "SNAPSHOT",
        version := projectMajorVersion.value + "." + projectBuildNumber.value,

        scalacOptions ++= Seq(
          "-language:postfixOps",
          "-feature",
          "-deprecation",
          "-unchecked",
          "-target:jvm-1.8",
          "-encoding", "UTF-8"
        ),

        javacOptions ++= Seq(
          "-source", "1.8",
          "-target", "1.8",
          "-encoding", "UTF-8",
          "-Xlint:unchecked",
          "-Xlint:deprecation"
        ),

        resolvers ++= Seq(
          "Innova libs repo" at "http://repproxy.srv.inn.ru/artifactory/libs-release-local",
          "Innova ext repo" at "http://repproxy.srv.inn.ru/artifactory/ext-release-local",
          Resolver.sonatypeRepo("public")
        ),

        publishTo := Some("Innova libs repo" at "http://repproxy.srv.inn.ru/artifactory/libs-release-local"),
        credentials += Credentials(Path.userHome / ".ivy2" / ".innova_credentials")
    )

  lazy val `hyper-facade-root` = project.in(file(".")) aggregate (
    `hyper-facade`,
    `hyper-facade-model`,
    `hyper-facade-simpleauth`
    )
  lazy val `hyper-facade` = project.in(file("hyper-facade")) dependsOn (`hyper-facade-model`, `hyper-facade-simpleauth`)
  lazy val `hyper-facade-model` = project.in(file("hyper-facade-model"))
  lazy val `hyper-facade-simpleauth` = project.in(file("hyper-facade-simpleauth")) dependsOn `hyper-facade-model`
}
