ThisBuild / version := "0.1.0-SNAPSHOT"
ThisBuild / scalaVersion := "3.3.7"

lazy val core = (project in file("modules/core"))
  .settings(
    name := "graphrag-core"
  )

lazy val ingestion = (project in file("modules/ingestion"))
  .settings(
    name := "graphrag-ingestion"
  )

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
