organization := "eu.inn"
name := "hyperbus-facade"

scalaVersion := "2.11.7"
version := "0.1.SNAPSHOT"

scalacOptions ++= Seq(
  "-feature",
  "-deprecation",
  "-unchecked",
  "-optimise",
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
  Resolver.sonatypeRepo("public"),
  Resolver.mavenLocal
)

libraryDependencies ++= Seq(
  "eu.inn"              %% "hyperbus"                       % "0.1.SNAPSHOT",
  "eu.inn"              %% "hyperbus-model"                 % "0.1.SNAPSHOT",
  "eu.inn"              %% "hyperbus-standard-model"        % "0.1.SNAPSHOT",
  "eu.inn"              %% "hyperbus-transport"             % "0.1.SNAPSHOT",
  "eu.inn"              %% "hyperbus-t-kafka"               % "0.1.SNAPSHOT",
  "eu.inn"              %% "hyperbus-t-distributed-akka"    % "0.1.SNAPSHOT",
  "jline"               %  "jline"                          % "2.12.1",
  "io.spray"            %% "spray-can"                      % "1.3.3",
  "io.spray"            %% "spray-routing"                  % "1.3.3",
  "io.spray"            %% "spray-testkit"                  % "1.3.3"            % "test",
  "com.wandoulabs.akka" %% "spray-websocket"                % "0.1.4",
  "com.typesafe.akka"   %% "akka-actor"                     % "2.3.11",
  "com.typesafe.akka"   %% "akka-cluster"                   % "2.3.11",
  "com.typesafe.akka"   %% "akka-testkit"                   % "2.3.11"           % "test",
  "eu.inn"              %% "util-http"                      % "[0.1.172]",
  "java-raml1-tools"    % "java-raml1-parser"               % "0.0.1-SNAPSHOT",
  "java-raml1-tools"    % "javascript-module-holders"       % "0.0.1-SNAPSHOT",
  "org.scaldi"          %% "scaldi"                         % "0.5.7",
  "org.scalatest"       %% "scalatest"                      % "2.2.1"            % "test"
)

addCompilerPlugin("org.scalamacros" % "paradise" % "2.1.0-M5" cross CrossVersion.full)
