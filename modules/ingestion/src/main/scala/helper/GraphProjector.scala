package helper

import config._
import ingestion.SourceStream.Chunk
import org.apache.flink.streaming.api.datastream.DataStream
import org.apache.flink.api.common.functions.MapFunction

object GraphProjector {

  /**
   * Full projector for HW3:
   *
   * 1) Chunk nodes
   * 2) Concept nodes
   * 3) Scored relations → edges
   */
  def project(
               chunks: DataStream[Chunk],
               mentions: DataStream[Mention],
               coOccurs: DataStream[ConceptRelationshipMapping.CoOccur],
               scored: DataStream[ScoredRelation]
             ): DataStream[GraphWrite] = {

    val chunkNodes: DataStream[GraphWrite] =
      chunks.map(new MapFunction[Chunk, GraphWrite] {
        override def map(c: Chunk): GraphWrite =
          UpsertNode(
            label = "Chunk",
            id    = c.chunkId,
            props = Map(
              "docId"     -> c.docId,
              "spanStart" -> c.span._1,
              "spanEnd"   -> c.span._2,
              "text"      -> c.text,
              "sourceUri" -> c.sourceUri,
              "hash"      -> c.hash
            )
          )
      })

    val conceptNodes: DataStream[GraphWrite] =
      mentions.map(new MapFunction[Mention, GraphWrite] {
        override def map(m: Mention): GraphWrite =
          UpsertNode(
            label = "Concept",
            id    = m.concept.conceptId,
            props = Map(
              "lemma"   -> m.concept.lemma,
              "surface" -> m.concept.surface,
              "origin"  -> m.concept.origin
            )
          )
      })

    val relationEdges: DataStream[GraphWrite] =
      scored.map(new MapFunction[ScoredRelation, GraphWrite] {
        override def map(s: ScoredRelation): GraphWrite =
          UpsertEdge(
            fromLabel = "Concept",
            fromId    = s.a.conceptId,
            rel       = s.predicate,
            toLabel   = "Concept",
            toId      = s.b.conceptId,
            props     = Map(
              "confidence" -> s.confidence,
              "evidence"   -> s.evidence
            )
          )
      })

    // Combine everything
    chunkNodes
      .union(conceptNodes)
      .union(relationEdges)
  }
}
