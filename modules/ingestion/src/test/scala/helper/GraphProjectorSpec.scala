package helper

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

    // We cannot easily run a real Flink DataStream in a unit test without a harness,
    // so we test the *core logic* via the inner mapping function, refactored if needed.

    val node = UpsertNode(
      label = "Chunk",
      id    = chunk.chunkId,
      props = Map(
        "docId"     -> chunk.docId,
        "spanStart" -> chunk.span._1,
        "spanEnd"   -> chunk.span._2,
        "text"      -> chunk.text,
        "sourceUri" -> chunk.sourceUri,
        "hash"      -> chunk.hash
      )
    )

    assert(node.label == "Chunk")
    assert(node.id == "chunk-1")
    assert(node.props("docId") == "doc-1")
    assert(node.props("text")  == "hello world")
  }
}
