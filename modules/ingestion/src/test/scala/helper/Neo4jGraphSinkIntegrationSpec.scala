package helper

import org.scalatest.funsuite.AnyFunSuite
import org.apache.flink.configuration.Configuration

final class Neo4jGraphSinkIntegrationSpec extends AnyFunSuite:

  test("Neo4jGraphSink upserts a node and an edge without error") {
    val cfg = Neo4jConfig(
      uri      = "bolt://localhost:7687",
      user     = "neo4j",
      password = "password",
      database = "neo4j"
    )

    val sink = new Neo4jGraphSink(cfg)
    sink.open(new Configuration())

    val node =
      UpsertNode(
        label = "Chunk",
        id    = "test-chunk-2",
        props = Map("docId" -> "doc-xyz", "text" -> "From ScalaTest")
      )

    val edge =
      UpsertEdge(
        fromLabel = "Chunk",
        fromId    = "test-chunk-2",
        rel       = "MENTIONS",
        toLabel   = "Concept",
        toId      = "concept-abc",
        props     = Map("score" -> 0.7)
      )

    // If any of these throw, the test fails.
    sink.invoke(node, null)
    sink.invoke(edge, null)

    sink.close()

    assert(true) // we just assert "no exception"
  }
