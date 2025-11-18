ThisBuild / version      := "0.1.0-SNAPSHOT"
ThisBuild / scalaVersion := "3.3.7"

val flinkVersion       = "1.20.0"      // keep the HW version
val neo4jDriverVersion  = "4.4.18"
val parquetVersion     = "1.13.1"

// ---------------- core ----------------
lazy val core = (project in file("modules/core"))
  .settings(
    name := "graphrag-core",
    libraryDependencies ++= Seq(
      "com.typesafe" % "config" % "1.4.3"
    )
  )

// ---------------- ingestion (FIXED) ----------------
lazy val ingestion = (project in file("modules/ingestion"))
  .dependsOn(core)
  .settings(
    name := "graphrag-ingestion",
    libraryDependencies ++= Seq(
      // Flink Java DataStream stack – this is all you need
      "org.apache.flink" % "flink-java"           % flinkVersion,
      "org.apache.flink" % "flink-streaming-java" % flinkVersion,
      "org.apache.flink" % "flink-clients"        % flinkVersion,

      // Parquet + Hadoop
      "org.apache.parquet" % "parquet-hadoop" % parquetVersion,
      "org.apache.parquet" % "parquet-column" % parquetVersion,
      "org.apache.hadoop"  % "hadoop-client"  % "3.3.6",

      // Neo4j driver
      "org.neo4j.driver"   % "neo4j-java-driver" % neo4jDriverVersion,

      // ---- TEST DEPENDENCY ----
      "org.scalatest" %% "scalatest" % "3.2.19" % Test
    )
  )

// ---------------- other modules ----------------
lazy val neo4j = (project in file("modules/neo4j"))
  .settings(
    name := "graphrag-neo4j"
  )

lazy val llm = (project in file("modules/llm"))
  .settings(
    name := "graphrag-llm"
  )

lazy val root = (project in file("."))
  .aggregate(core, ingestion, neo4j, llm)
  .dependsOn(core)
  .settings(
    name := "graphrag"
  )
