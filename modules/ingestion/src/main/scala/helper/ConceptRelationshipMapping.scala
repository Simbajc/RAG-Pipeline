package helper

//import model.{Mention, Concept, CoOccur}  // adjust to your actual package

import config.{Concept, Mention, RelationCandidate}
import helper.ConceptRelationshipMapping.CoOccur
import ingestion.SourceStream.Chunk
import org.apache.flink.api.common.state.{ListState, ListStateDescriptor}
import org.apache.flink.api.common.typeinfo.TypeInformation
import org.apache.flink.configuration.Configuration
import org.apache.flink.streaming.api.scala.{DataStream, _}
import org.apache.flink.streaming.api.functions.KeyedProcessFunction
import org.apache.flink.util.Collector
import org.apache.flink.api.common.functions.MapFunction
import org.apache.flink.api.common.typeinfo.TypeInformation


//import scala.jdk.CollectionConverters.*
import scala.collection.JavaConverters._


//final case class RelationCandidate(a: Concept, b: Concept, evidence: String)
//
//final case class ScoredRelation(
//                                 a: Concept,
//                                 predicate: String,
//                                 b: Concept,
//                                 confidence: Double,
//                                 evidence: String
//                               )







object ConceptRelationshipMapping {

  implicit val relationCandidateTypeInfo: TypeInformation[RelationCandidate] =
    TypeInformation.of(classOf[RelationCandidate])

  final case class CoOccur(a: Concept, b: Concept, windowId: String, freq: Long)

  /**
   * Local co-occurrence over mentions for each chunk.
   *
   * - Assumes upstream: mentions.keyBy(_.chunkId)
   * - Maintains the last `windowSize` mentions for that chunk in state.
   * - For each new mention, it:
   *     1) appends it to the buffer,
   *     2) trims buffer to last `windowSize`,
   *     3) emits all unordered pairs from that window as CoOccur(a,b,windowId,freq=1).
   */
  def localCoOccurrence(windowSize: Int): KeyedProcessFunction[String, Mention, CoOccur] =
    new KeyedProcessFunction[String, Mention, CoOccur] {

      private var bufferState: ListState[Mention] = _

      override def open(parameters: Configuration): Unit = {
        val desc = new ListStateDescriptor[Mention](
          "mentions-buffer",
          TypeInformation.of(classOf[Mention])
        )
        bufferState = getRuntimeContext.getListState(desc)
      }

      override def processElement(
                                   value: Mention,
                                   ctx: KeyedProcessFunction[String, Mention, CoOccur]#Context,
                                   out: Collector[CoOccur]
                                 ): Unit = {
        // 1) Append new mention to buffer
        val current = bufferState.get().asScala.toList :+ value

//        val current: List[RelationCandidateMention] =
//          Option(bufferState.get()).getOrElse(List.empty[RelationCandidateMention])
//
//        val updated = current :+ value
//        bufferState.update(updated)

        // 2) Keep only last `windowSize` mentions
        val window = current.takeRight(windowSize)
        bufferState.update(window.asJava)

        // 3) Emit all unordered pairs in this window as co-occurrences
        window
          .combinations(2)        // size-2 subsets, no manual loops
          .foreach {
            case Seq(m1, m2) =>
              val windowId = ctx.getCurrentKey // using chunkId as windowId
              out.collect(
                CoOccur(
                  a        = m1.concept,
                  b        = m2.concept,
                  windowId = windowId,
                  freq     = 1L
                )
              )
            case _ => () // should never happen, but keeps the compiler happy
          }
      }
    }


  /** Pure version of the local co-occurrence logic for a single chunk.
   * `mentions` must be in their natural order within the chunk.
   */
  def computeLocalCoOccurrence(
                                mentions: Seq[Mention],
                                windowSize: Int
                              ): Seq[CoOccur] = {
    val buf = scala.collection.mutable.ListBuffer.empty[CoOccur]

    for {
      (m, i) <- mentions.zipWithIndex
      j <- (i + 1) until math.min(i + 1 + windowSize, mentions.length)
      other = mentions(j)
      if m.concept.conceptId != other.concept.conceptId
    } {
      buf += CoOccur(m.concept, other.concept, m.chunkId, windowSize)
    }

    buf.toList
  }


  /** Build cheap semantic-relation candidates from co-occurrence.
   *
   * For now we:
   *   - ignore `normalized` and `mentions`
   *   - create a lightweight `evidence` string from concept surfaces
   *
   * This is enough to feed the LLM scoring stage.
   */
  def makeCandidates(
                      normalized: org.apache.flink.streaming.api.datastream.DataStream[Chunk],
                      mentions: org.apache.flink.streaming.api.datastream.DataStream[Mention],
                      coOccurs: org.apache.flink.streaming.api.datastream.DataStream[CoOccur]
                    ): org.apache.flink.streaming.api.datastream.DataStream[RelationCandidate] = {

    coOccurs
      .map(
        (co: CoOccur) => {
          val ev = s"${co.a.surface} ... ${co.b.surface}" // simple evidence text

          RelationCandidate(
            a = co.a,
            b = co.b,
            evidence = ev
          )
        }
      )
      .returns(classOf[RelationCandidate])
      .name("relation-candidates")
  }






}
