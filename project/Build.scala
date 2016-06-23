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
        Resolver.mavenLocal,
        Resolver.sonatypeRepo("public")
      ),

      libraryDependencies ++= Seq(
        "ch.qos.logback"                 % "logback-classic"              % "1.1.2",
        "ch.qos.logback"                 % "logback-core"                 % "1.1.2",
        "com.lihaoyi"                    %% "fastparse"                   % "0.3.7",
        "com.typesafe.akka"              %% "akka-actor"                  % "2.4.1",
        "com.typesafe.akka"              %% "akka-cluster"                % "2.4.1",
        "com.wandoulabs.akka"            %% "spray-websocket"             % "0.1.4",
        "eu.inn"                         %% "binders-core"                % "0.12.85",
        "eu.inn"                         %% "expression-parser"           % "0.1.11",
        "eu.inn"                         %% "auth-service-model"          % "0.1.4",
        "eu.inn"                         %% "hyperbus"                    % "0.1.76",
        "eu.inn"                         %% "hyperbus-model"              % "0.1.76",
        "eu.inn"                         %% "hyperbus-transport"          % "0.1.76",
        "eu.inn"                         %% "hyperbus-t-kafka"            % "0.1.76",
        "eu.inn"                         %% "hyperbus-t-distributed-akka" % "0.1.76",
        "eu.inn"                         % "java-raml1-parser"            % "0.0.32",
        "eu.inn"                         % "javascript-module-holders"    % "0.0.32",
        "eu.inn"                         %% "service-control"             % "0.2.17",
        "eu.inn"                         %% "service-config"              % "0.1.6",
        "eu.inn"                         %% "service-metrics"             % "0.1.6",
        "jline"                          % "jline"                        % "2.12.1",
        "io.spray"                       %% "spray-can"                   % "1.3.3",
        "io.spray"                       %% "spray-routing-shapeless2"    % "1.3.3",
        "org.scaldi"                     %% "scaldi"                      % "0.5.7",
        "io.spray"                       %% "spray-client"                % "1.3.3"     % "test",
        "eu.inn"                         %% "simple-auth-service"         % "0.1.4"     % "test",
        "org.scalatest"                  %% "scalatest"                   % "2.2.6"     % "test"
      ),

      fork in Test := true,
      publishTo := Some("Innova libs repo" at "http://repproxy.srv.inn.ru/artifactory/libs-release-local"),
      credentials += Credentials(Path.userHome / ".ivy2" / ".innova_credentials"),
      addCompilerPlugin("org.scalamacros" % "paradise" % "2.1.0" cross CrossVersion.full)
    )
}
