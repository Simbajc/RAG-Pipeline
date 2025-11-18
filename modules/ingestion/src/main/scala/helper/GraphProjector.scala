package helper

import ingestion.SourceStream.Chunk
import org.apache.flink.streaming.api.datastream.DataStream
import org.apache.flink.api.common.functions.MapFunction

// If you already have these in core, remove this block and import them instead.
sealed trait GraphWrite
final case class UpsertNode(
                             label: String,
                             id:    String,
                             props: Map[String, Any]
                           ) extends GraphWrite

final case class UpsertEdge(
                             fromLabel: String,
                             fromId:    String,
                             rel:       String,
                             toLabel:   String,
                             toId:      String,
                             props:     Map[String, Any]
                           ) extends GraphWrite

object GraphProjector {

  /**
   * Very first, simple projector:
   * - One Chunk row → one Chunk node upsert.
   * - No Concept / relation logic yet.
   */
  def project(chunks: DataStream[Chunk]): DataStream[GraphWrite] = {
    chunks.map(
      new MapFunction[Chunk, GraphWrite] {
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
      }
    )
  }
}
