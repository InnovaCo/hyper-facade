import sbt.Keys._

organization := "eu.inn"

name := "hyper-facade"

projectMajorVersion := "0.1"

projectBuildNumber := "SNAPSHOT"

version := projectMajorVersion.value + "." + projectBuildNumber.value

scalaVersion := "2.11.8"

crossScalaVersions := Seq("2.11.8")

scalacOptions ++= Seq(
  "-language:postfixOps",
  "-feature",
  "-deprecation",
  "-unchecked",
  "-target:jvm-1.8",
  "-encoding", "UTF-8"
)

javacOptions ++= Seq(
  "-source", "1.8",
  "-target", "1.8",
  "-encoding", "UTF-8",
  "-Xlint:unchecked",
  "-Xlint:deprecation"
)

resolvers ++= Seq(
  "Innova libs repo" at "http://repproxy.srv.inn.ru/artifactory/libs-release-local",
  "Innova ext repo" at "http://repproxy.srv.inn.ru/artifactory/ext-release-local",
  Resolver.sonatypeRepo("public")
)

//ramlHyperbusSource := file("hyper-facade.raml")
//
//ramlHyperbusPackageName := "eu.inn.facade.api"

buildInfoPackage := "eu.inn.facade"

buildInfoKeys := Seq[BuildInfoKey](name, version, scalaVersion, sbtVersion)

lazy val root = (project in file(".")). enablePlugins(BuildInfoPlugin) //, Raml2Hyperbus)

val projectMajorVersion = settingKey[String]("Defines the major version number")

val projectBuildNumber = settingKey[String]("Defines the build number")

addCompilerPlugin("org.scalamacros" % "paradise" % "2.1.0" cross CrossVersion.full)

libraryDependencies ++= Seq(
  "ch.qos.logback"       % "logback-classic"              % "1.1.2",
  "ch.qos.logback"       % "logback-core"                 % "1.1.2",
  "com.typesafe.akka"    %% "akka-actor"                  % "2.4.1",
  "com.typesafe.akka"    %% "akka-cluster"                % "2.4.1",
  "com.wandoulabs.akka"  %% "spray-websocket"             % "0.1.4",
  "eu.inn"               %% "binders-core"                % "0.12.93",
  "eu.inn"               %% "expression-parser"           % "0.1.29",
  "eu.inn"               %% "auth-service-model"          % "0.1.7",
  "eu.inn"               %% "hyperbus"                    % "0.1.83",
  "eu.inn"               %% "hyperbus-model"              % "0.1.83",
  "eu.inn"               %% "hyperbus-transport"          % "0.1.83",
  "eu.inn"               %% "hyperbus-t-kafka"            % "0.1.83",
  "eu.inn"               %% "hyperbus-t-distributed-akka" % "0.1.83",
  "eu.inn"               % "raml-parser-2"                % "1.0.1.35",
  "eu.inn"               %% "service-control"             % "0.2.17",
  "eu.inn"               %% "service-config"              % "0.1.6",
  "eu.inn"               %% "service-metrics"             % "0.1.6",
  "jline"                % "jline"                        % "2.12.1",
  "io.reactivex"         %% "rxscala"                     % "0.26.3",
  "io.spray"             %% "spray-can"                   % "1.3.3",
  "io.spray"             %% "spray-routing-shapeless2"    % "1.3.3",
  "io.spray"             %% "spray-client"                % "1.3.3"     % "test",
  "org.scaldi"           %% "scaldi"                      % "0.5.7",
  "org.scalatest"        %% "scalatest"                   % "2.2.6"     % "test",
  "eu.inn"               %% "simple-auth-service"         % "0.1.13"    % "test",
  "org.pegdown"          % "pegdown"                      % "1.4.2"     % "test"
)

fork in Test := true

parallelExecution in Test := false