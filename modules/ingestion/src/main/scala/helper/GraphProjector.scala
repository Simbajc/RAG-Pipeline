package helper

import config._
import helper.ConceptMapping.getClass
import helper.ConceptRelationshipMapping.CoOccur
import ingestion.SourceStream.Chunk
import org.apache.flink.api.common.functions.MapFunction
import org.apache.flink.streaming.api.scala.{DataStream, _}
import org.slf4j.LoggerFactory

object GraphProjector {

  /**
   * Full projector for HW3:
   *
   * 1) Chunk nodes
   * 2) Concept nodes
   * 3) Scored relations → edges
   */
  private val log = LoggerFactory.getLogger(getClass)
  def project(
               chunks: DataStream[Chunk],
               mentions: DataStream[Mention],
               coOccurs: DataStream[ConceptRelationshipMapping.CoOccur],
               scored: DataStream[ScoredRelation]
             ): DataStream[GraphWrite] = {
    log.info("Start projecting data into the graph, (chunk nodes, concepts, relational edges, mention edges")
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


    val mentionEdges: DataStream[GraphWrite] =
      mentions.map(new MapFunction[Mention, GraphWrite] {
        override def map(m: Mention): GraphWrite =
          UpsertEdge(
            fromLabel = "Chunk",
            fromId    = m.chunkId,
            rel       = "MENTIONS",
            toLabel   = "Concept",
            toId      = m.concept.conceptId,
            props     = Map()
          )
      })


    val coOccurEdges: DataStream[GraphWrite] =
      coOccurs
        .map(new MapFunction[CoOccur, GraphWrite] {
          override def map(c: CoOccur): GraphWrite =
            UpsertEdge(
              fromLabel = "Concept",
              fromId    = c.a.conceptId,
              rel       = "CO_OCCURS",
              toLabel   = "Concept",
              toId      = c.b.conceptId,
              props     = Map("freq" -> c.freq)
            )
        })
        .name("co-occurs-edges")


    log.info("start merging data to one graph")
    // Combine everything
    chunkNodes
      .union(conceptNodes)
      .union(mentionEdges)
      .union(relationEdges)
      .union(coOccurEdges)
  }
}
