package helper

import config.{Concept, Mention}
import org.scalatest.funsuite.AnyFunSuite
import helper.ConceptRelationshipMapping.CoOccur

class ConceptRelationshipMappingTest extends AnyFunSuite {

  private def hasPair(results: Seq[CoOccur], x: String, y: String): Boolean =
    results.exists(c => c.a.conceptId == x && c.b.conceptId == y)

  test("computeLocalCoOccurrence emits all co-occurring pairs within windowSize=3") {
    val cA = Concept("A", "a", "A", "test")
    val cB = Concept("B", "b", "B", "test")
    val cC = Concept("C", "c", "C", "test")

    val mentions = Seq(
      Mention("chunk-1", cA),
      Mention("chunk-1", cB),
      Mention("chunk-1", cC)
    )

    val results = ConceptRelationshipMapping.computeLocalCoOccurrence(
      mentions,
      windowSize = 3
    )

    assert(hasPair(results, "A", "B"))
    assert(hasPair(results, "A", "C") || hasPair(results, "C", "A"))
    assert(hasPair(results, "B", "C") || hasPair(results, "C", "B"))
    assert(!results.exists(c => c.a.conceptId == c.b.conceptId))
  }

  test("computeLocalCoOccurrence respects small windowSize=1") {
    val cA = Concept("A", "a", "A", "test")
    val cB = Concept("B", "b", "B", "test")
    val cC = Concept("C", "c", "C", "test")

    val mentions = Seq(
      Mention("chunk-2", cA), // pos 0
      Mention("chunk-2", cB), // pos 1
      Mention("chunk-2", cC)  // pos 2
    )

    val results = ConceptRelationshipMapping.computeLocalCoOccurrence(
      mentions,
      windowSize = 1
    )

    assert(hasPair(results, "A", "B") || hasPair(results, "B", "A"))
    assert(hasPair(results, "B", "C") || hasPair(results, "C", "B"))
    assert(!hasPair(results, "A", "C"))
    assert(!hasPair(results, "C", "A"))
  }


  test("computeLocalCoOccurrence on empty mentions yields no pairs") {
    val results = ConceptRelationshipMapping.computeLocalCoOccurrence(Seq.empty, windowSize = 3)
    assert(results.isEmpty)
  }

  test("computeLocalCoOccurrence does not emit pairs for a single mention") {
    val cA = Concept("A", "a", "A", "test")
    val mentions = Seq(Mention("chunk-1", cA))
    val results = ConceptRelationshipMapping.computeLocalCoOccurrence(mentions, windowSize = 3)
    assert(results.isEmpty)
  }
}
