import sbt._
import sbt.Keys._

import sbtassembly.AssemblyPlugin
import sbtassembly.AssemblyPlugin.autoImport._
import sbtassembly.MergeStrategy

ThisBuild / version      := "0.1.0-SNAPSHOT"
ThisBuild / scalaVersion := "2.12.18"

val flinkVersion        = "1.20.0"
val parquetVersion      = "1.13.1"
lazy val neo4jDriverVersion = "5.25.0"

// ---------------- core ----------------
lazy val core = (project in file("modules/core"))
  .settings(
    name := "graphrag-core",
    libraryDependencies += "com.typesafe" % "config" % "1.4.3"
  )

// ---------------- llm ----------------
lazy val llm = (project in file("modules/llm"))
  .dependsOn(core)
  .settings(
    name := "graphrag-llm",
    libraryDependencies ++= Seq(
      // Config
      "com.typesafe" % "config" % "1.4.3",

      // Circe JSON
      "io.circe" %% "circe-core"    % "0.14.7",
      "io.circe" %% "circe-generic" % "0.14.7",
      "io.circe" %% "circe-parser"  % "0.14.7",

      // STTP HTTP client (ALL on 3.5.2)
      "com.softwaremill.sttp.client3" %% "core"               % "3.5.2",
      "com.softwaremill.sttp.client3" %% "circe"              % "3.5.2",
      "com.softwaremill.sttp.client3" %% "httpclient-backend" % "3.5.2"
    )
  )

// ---------------- neo4j helpers ----------------
lazy val neo4j = (project in file("modules/neo4j"))
  .dependsOn(core)
  .settings(
    name := "graphrag-neo4j",
    libraryDependencies ++= Seq(
      "org.apache.flink" % "flink-java"           % flinkVersion,
      "org.apache.flink" % "flink-streaming-java" % flinkVersion,
      "org.apache.flink" % "flink-clients"        % flinkVersion,
      // Neo4j Java driver
      "org.neo4j.driver"   % "neo4j-java-driver" % neo4jDriverVersion,
    )
  )

// ---------------- ingestion (Flink job + fat JAR) ----------------
lazy val ingestion = (project in file("modules/ingestion"))
  .enablePlugins(AssemblyPlugin)
  .dependsOn(core, llm, neo4j)
  .settings(
    name := "graphrag-ingestion",

    assembly / mainClass := Some("ingestion.IngestionModule"),
    assembly / test := {},

    assembly / assemblyMergeStrategy := {
      // discard module-info (Java 9+ modules)
      case p if p.endsWith("module-info.class") =>
        MergeStrategy.discard

      // VERY IMPORTANT: keep Hadoop FileSystem services so "file" works
      case "META-INF/services/org.apache.hadoop.fs.FileSystem" =>
        MergeStrategy.concat

      // you can still discard signatures etc.
      case p if p.startsWith("META-INF/") &&
        (p.endsWith(".SF") || p.endsWith(".DSA") || p.endsWith(".RSA") || p.contains("MANIFEST.MF")) =>
        MergeStrategy.discard

      // for everything else under META-INF, a safe default:
      case p if p.startsWith("META-INF/") =>
        MergeStrategy.first

      // default
      case _ =>
        MergeStrategy.first
    },

    libraryDependencies ++= Seq(
      // Flink Java DataStream stack
      "org.apache.flink" % "flink-core"           % flinkVersion,
      "org.apache.flink" % "flink-java"           % flinkVersion,
      "org.apache.flink" % "flink-streaming-java" % flinkVersion,
      "org.apache.flink" % "flink-clients"        % flinkVersion,
      "org.apache.flink" %% "flink-streaming-scala" % flinkVersion,
      "org.neo4j.driver" % "neo4j-java-driver" % "5.25.0",
//      "org.apache.flink" %% "flink-connector-neo4j" % "1.0.0-1.17",

      // Scala API (provided, because Flink cluster already has it)
//      "org.apache.flink" % "flink-streaming-scala_2.12" % flinkVersion % "provided",

      // Parquet + Hadoop
      "org.apache.parquet" % "parquet-hadoop" % parquetVersion,
      "org.apache.parquet" % "parquet-column" % parquetVersion,
      "org.apache.hadoop"  % "hadoop-client"  % "3.3.6",



      // Tests
      "org.scalatest" %% "scalatest" % "3.2.19" % Test
    )
  )



// ---------------- API ----------------------------------------------
lazy val api = (project in file("modules/api"))
  .settings(
    name := "graphrag-api",
    libraryDependencies ++= Seq(
      // Akka core + streams (Scala 2.12 artifacts)
      "com.typesafe.akka" %% "akka-actor"   % "2.6.21",
      "com.typesafe.akka" %% "akka-stream"  % "2.6.21",

      // Akka HTTP for REST endpoints
      "com.typesafe.akka" %% "akka-http"    % "10.2.10",

      // JSON
      "io.spray"          %% "spray-json"   % "1.3.6",
      "com.typesafe.akka" %% "akka-http-spray-json" % "10.2.10",

      // Neo4j Java driver (no Scala cross-versioning)
      "org.neo4j.driver"  %  "neo4j-java-driver" % "5.26.1"
    )
  )
  .dependsOn(neo4j, core)   // you already have this module; lets you reuse config types if needed

// ---------------- root aggregator ----------------
lazy val root = (project in file("."))
  .aggregate(core, ingestion, neo4j, llm, api)
  .dependsOn(core)
  .settings(
    name := "graphrag"
  )
