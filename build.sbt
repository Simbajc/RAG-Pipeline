import sbt._
import sbt.Keys._

import sbtassembly.AssemblyPlugin
import sbtassembly.AssemblyPlugin.autoImport._
import sbtassembly.MergeStrategy

ThisBuild / version      := "0.1.0-SNAPSHOT"
ThisBuild / scalaVersion := "2.12.18"

val flinkVersion        = "1.20.0"
val neo4jDriverVersion  = "4.4.18"
val parquetVersion      = "1.13.1"

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
      "org.apache.flink" % "flink-clients"        % flinkVersion
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

      // Scala API (provided, because Flink cluster already has it)
//      "org.apache.flink" % "flink-streaming-scala_2.12" % flinkVersion % "provided",

      // Parquet + Hadoop
      "org.apache.parquet" % "parquet-hadoop" % parquetVersion,
      "org.apache.parquet" % "parquet-column" % parquetVersion,
      "org.apache.hadoop"  % "hadoop-client"  % "3.3.6",

      // Neo4j Java driver
      "org.neo4j.driver"   % "neo4j-java-driver" % neo4jDriverVersion,

      // Tests
      "org.scalatest" %% "scalatest" % "3.2.19" % Test
    )
  )

// ---------------- root aggregator ----------------
lazy val root = (project in file("."))
  .aggregate(core, ingestion, neo4j, llm)
  .dependsOn(core)
  .settings(
    name := "graphrag"
  )
