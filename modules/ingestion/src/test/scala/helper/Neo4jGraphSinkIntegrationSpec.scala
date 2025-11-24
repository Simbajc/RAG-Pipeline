import org.apache.flink.configuration.Configuration
import org.neo4j.driver.exceptions.ServiceUnavailableException
import org.scalatest.funsuite.AnyFunSuite
import config.{AppConfig, GraphWrite, UpsertEdge, UpsertNode}
import helper.{Neo4jConfig, Neo4jGraphSink}

class Neo4jGraphSinkIntegrationSpec extends AnyFunSuite {

  test("Neo4jGraphSink upserts a node and an edge against a live DB") {
    val cfg = Neo4jConfig(
      uri      = AppConfig.Neo4jConfig.uri,
      user     = AppConfig.Neo4jConfig.user,
      password = AppConfig.Neo4jConfig.password,
      database = AppConfig.Neo4jConfig.database
    )

    val sink = new Neo4jGraphSink(cfg)

    try {
      // In Flink this is called by the runtime; in tests we must call it ourselves.
      sink.open(new Configuration())

      val node: GraphWrite =
        UpsertNode(
          label = "Concept",
          id    = "c1",
          props = Map("lemma" -> "aspirin")
        )

      val edge: GraphWrite =
        UpsertEdge(
          fromLabel = "Concept",
          fromId    = "c1",
          rel       = "RELATED_TO",
          toLabel   = "Concept",
          toId      = "c2",
          props     = Map("source" -> "test")
        )

      sink.invoke(node, null)
      sink.invoke(edge, null)

      // If we reached here, Neo4j was reachable and the sink executed without error.
      succeed
    } catch {
      case _: ServiceUnavailableException =>
        // When Neo4j is not running, don't fail the suite – just skip this integration test.
        cancel("Neo4j not running on localhost:7687; skipping integration test")
    } finally {
      sink.close()
    }
  }
}
