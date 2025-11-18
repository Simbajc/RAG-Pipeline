package helper

import org.apache.flink.configuration.Configuration

object Neo4jSmokeTest {

  def main(args: Array[String]): Unit = {
    val cfg = Neo4jConfig(
      uri      = "bolt://localhost:7687",  // adjust if needed
      user     = "neo4j",
      password = "password",
      database = "neo4j"
    )

    val sink = new Neo4jGraphSink(cfg)
    sink.open(new Configuration())

    // Test node
    val testNode =
      UpsertNode(
        label = "Chunk",
        id    = "test-chunk-1",
        props = Map(
          "docId" -> "doc-123",
          "text"  -> "Hello from Neo4jSmokeTest 2"
        )
      )

    sink.invoke(testNode, null)

    // Test edge
    val testEdge =
      UpsertEdge(
        fromLabel = "Chunk",
        fromId    = "test-chunk-1",
        rel       = "MENTIONS",
        toLabel   = "Concept",
        toId      = "concept-xyz",
        props     = Map("score" -> 0.95)
      )

    sink.invoke(testEdge, null)

    sink.close()
    println("Neo4jSmokeTest completed.")
  }
}
