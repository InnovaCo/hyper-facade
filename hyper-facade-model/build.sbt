organization := "eu.inn"

name := "hyper-facade-model"

scalaVersion := "2.11.8"

libraryDependencies ++= Seq(
  "com.wandoulabs.akka"            %% "spray-websocket"             % "0.1.4",
  "eu.inn"                         %% "binders-core"                % "0.12.85",
  "eu.inn"                         %% "hyperbus-model"              % "0.1.76",
  "eu.inn"                         %% "hyperbus-transport"          % "0.1.76",
  "io.spray"                       %% "spray-can"                   % "1.3.3",
  "org.scaldi"                     %% "scaldi"                      % "0.5.7"
)