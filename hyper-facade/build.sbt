organization := "eu.inn"

name := "hyper-facade"

scalaVersion := "2.11.8"

libraryDependencies ++= Seq(
  "ch.qos.logback"                 % "logback-classic"              % "1.1.2",
  "ch.qos.logback"                 % "logback-core"                 % "1.1.2",
  "com.typesafe.akka"              %% "akka-actor"                  % "2.4.1",
  "com.typesafe.akka"              %% "akka-cluster"                % "2.4.1",
  "com.wandoulabs.akka"            %% "spray-websocket"             % "0.1.4",
  "eu.inn"                         %% "binders-core"                % "0.12.85",
  "eu.inn"                         %% "hyper-facade-model"          % version.value,
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
  "io.spray"                       %% "spray-client"                % "1.3.3"     % "test",
  "org.scaldi"                     %% "scaldi"                      % "0.5.7",
  "org.scalatest"                  %% "scalatest"                   % "2.2.6"     % "test",
  "org.pegdown"                    % "pegdown"                      % "1.4.2"     % "test"
)

fork in Test := true

addCompilerPlugin("org.scalamacros" % "paradise" % "2.1.0" cross CrossVersion.full)