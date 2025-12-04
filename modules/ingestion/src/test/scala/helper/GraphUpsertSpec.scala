package helper

import config.UpsertNode
import org.scalatest.funsuite.AnyFunSuite

class GraphUpsertSpec extends AnyFunSuite {
  test("GraphUpsert.mapper generates MERGE for Concept node") {
    val writes = GraphUpsert.mapper(UpsertNode("Concept", "c1", Map("lemma" -> "aspirin")))
    assert(writes.exists(_.contains("MERGE (c:Concept {conceptId: $id})")))
  }
}