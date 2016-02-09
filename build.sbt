organization := "eu.inn"
name := "hyperbus-facade"

scalaVersion := "2.11.7"
version := "0.1.SNAPSHOT"

scalacOptions ++= Seq(
  "-language:postfixOps",
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

packSettings

packMain := Map("start" -> "'$@' eu.inn.facade.MainApp #")

packJarNameConvention := "full"

buildInfoSettings

sourceGenerators in Compile <+= buildInfo

buildInfoKeys := Seq[BuildInfoKey](BuildInfoKey.action("name")("inn-hyperbus-facade"), version, BuildInfoKey.action("buildTime")(System.currentTimeMillis))

buildInfoPackage := "eu.inn.forgame.api"

fork in Test := true

resolvers ++= Seq(
  "Innova libs repo" at "http://repproxy.srv.inn.ru/artifactory/libs-release-local",
  "Innova ext repo" at "http://repproxy.srv.inn.ru/artifactory/ext-release-local",
  Resolver.sonatypeRepo("public"),
  Resolver.mavenLocal
)

libraryDependencies ++= Seq(
  "io.dropwizard.metrics"          %  "metrics-core"                 % "3.1.2",
  "io.dropwizard.metrics"          %  "metrics-graphite"             % "3.1.2",
  "ch.qos.logback"                 %  "logback-classic"              % "1.1.2",
  "ch.qos.logback"                 %  "logback-core"                 % "1.1.2",
  "com.fasterxml.jackson.core"     %  "jackson-annotations"          % "2.4.4",
  "com.fasterxml.jackson.core"     %  "jackson-core"                 % "2.4.4",
  "com.fasterxml.jackson.core"     %  "jackson-databind"             % "2.4.4",
  "com.fasterxml.jackson.datatype" %  "jackson-datatype-joda"        % "2.4.3",
  "com.fasterxml.jackson.module"   %%  "jackson-module-scala"        % "2.4.3",
  "com.typesafe.akka"              %% "akka-actor"                   % "2.3.11",
  "com.typesafe.akka"              %% "akka-cluster"                 % "2.3.11",
  "com.typesafe.akka"              %% "akka-testkit"                 % "2.3.11"           % "test",
  "com.wandoulabs.akka"            %% "spray-websocket"              % "0.1.4",
  "eu.inn"                         %% "hyperbus"                     % "0.1.53",
  "eu.inn"                         %% "hyperbus-model"               % "0.1.53",
  "eu.inn"                         %% "hyperbus-standard-model"      % "0.1.53",
  "eu.inn"                         %% "hyperbus-transport"           % "0.1.53",
  "eu.inn"                         %% "hyperbus-t-kafka"             % "0.1.53",
  "eu.inn"                         %% "hyperbus-t-distributed-akka"  % "0.1.53",
  "jline"                          %  "jline"                        % "2.12.1",
  "io.spray"                       %% "spray-can"                    % "1.3.3",
  "io.spray"                       %% "spray-routing-shapeless2"     % "1.3.3",
  "io.spray"                       %% "spray-testkit"                % "1.3.3"            % "test",
  "eu.inn"                         % "java-raml1-parser"             % "0.0.1-SNAPSHOT",
  "eu.inn"                         % "javascript-module-holders"     % "0.0.1-SNAPSHOT",
  "org.scaldi"                     %% "scaldi"                       % "0.5.7",
  "org.scalatest"                  %% "scalatest"                    % "2.2.1"            % "test"
)

publishTo := Some("Innova libs repo" at "http://repproxy.srv.inn.ru/artifactory/libs-release-local")

credentials += Credentials(Path.userHome / ".ivy2" / ".innova_credentials")

addCompilerPlugin("org.scalamacros" % "paradise" % "2.1.0-M5" cross CrossVersion.full)
