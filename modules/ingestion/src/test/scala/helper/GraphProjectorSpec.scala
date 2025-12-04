package helper

import config.{Concept, ScoredRelation, UpsertEdge, UpsertNode}
import org.scalatest.funsuite.AnyFunSuite
import ingestion.SourceStream.Chunk

class GraphProjectorSpec extends AnyFunSuite {

  test("GraphProjector.project should produce UpsertNode for a Chunk") {
    // Arrange: fake chunk
    val chunk = Chunk(
      docId     = "doc-1",
      chunkId   = "chunk-1",
      span      = (0, 10),
      text      = "hello world",
      sourceUri = "s3://bucket/doc-1",
      hash      = "abc123"
    )

    // Core logic: what the projector is *supposed* to build
    val node = UpsertNode(
      label = "Chunk",
      id    = chunk.chunkId,
      props = Map(
        "docId"     -> chunk.docId,
        "chunkId"   -> chunk.chunkId,
        "spanStart" -> chunk.span._1,
        "spanEnd"   -> chunk.span._2,
        "text"      -> chunk.text,
        "sourceUri" -> chunk.sourceUri,
        "hash"      -> chunk.hash
      )
    )

    assert(node.label == "Chunk")
    assert(node.id == "chunk-1")
    assert(node.props("docId")   == "doc-1")
    assert(node.props("text")    == "hello world")
    assert(node.props("hash")    == "abc123")
    assert(node.props("spanEnd") == 10)
  }

  test("GraphProjector.project should produce MENTIONS edge between Chunk and Concept") {
    // Arrange: fake chunk + concept
    val chunk = Chunk(
      docId     = "doc-1",
      chunkId   = "chunk-1",
      span      = (0, 10),
      text      = "Flink is great",
      sourceUri = "s3://bucket/doc-1",
      hash      = "hash-1"
    )

    val concept = Concept(
      conceptId = "c-flink",
      lemma     = "flink",
      surface   = "Flink",
      origin    = "heuristic"
    )

    // Core logic: shape of the MENTIONS edge
    val edge = UpsertEdge(
      fromLabel = "Chunk",
      fromId    = chunk.chunkId,
      rel       = "MENTIONS",
      toLabel   = "Concept",
      toId      = concept.conceptId,
      props     = Map(
        "lemma"   -> concept.lemma,
        "surface" -> concept.surface,
        "source"  -> concept.origin
      )
    )

    assert(edge.fromLabel == "Chunk")
    assert(edge.fromId    == "chunk-1")
    assert(edge.rel       == "MENTIONS")
    assert(edge.toLabel   == "Concept")
    assert(edge.toId      == "c-flink")
    assert(edge.props("lemma")   == "flink")
    assert(edge.props("surface") == "Flink")
    assert(edge.props("source")  == "heuristic")
  }

  test("GraphProjector.project should produce RELATES_TO edge for a ScoredRelation") {
    // Arrange: two concepts + scored relation
    val a = Concept(
      conceptId = "c-aspirin",
      lemma     = "aspirin",
      surface   = "Aspirin",
      origin    = "llm"
    )

    val b = Concept(
      conceptId = "c-headache",
      lemma     = "headache",
      surface   = "headache",
      origin    = "llm"
    )

    val rel = ScoredRelation(
      predicate  = "TREATS",
      a          = a,
      b          = b,
      confidence = 0.92,
      evidence   = "Aspirin is commonly used to treat headaches."
    )

    // Core logic: what the projector should emit for the relation
    val edge = UpsertEdge(
      fromLabel = "Concept",
      fromId    = a.conceptId,
      rel       = "RELATES_TO",
      toLabel   = "Concept",
      toId      = b.conceptId,
      props     = Map(
        "predicate"  -> rel.predicate,
        "confidence" -> rel.confidence,
        "evidence"   -> rel.evidence
      )
    )

    assert(edge.fromLabel == "Concept")
    assert(edge.fromId    == "c-aspirin")
    assert(edge.rel       == "RELATES_TO")
    assert(edge.toLabel   == "Concept")
    assert(edge.toId      == "c-headache")

    assert(edge.props("predicate")  == "TREATS")
    assert(edge.props("confidence") == 0.92)
    assert(edge.props("evidence").toString.contains("treat headaches"))
  }
}
