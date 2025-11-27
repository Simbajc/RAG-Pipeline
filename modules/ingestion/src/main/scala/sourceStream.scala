package ingestion

import helper.IndexSource
import config.AppConfig

// USE SCALA API, not Java
import org.apache.flink.streaming.api.scala.{StreamExecutionEnvironment, DataStream, _}
import org.apache.flink.streaming.api.functions.source.SourceFunction
import ingestion.SourceStream.Chunk

object SourceStream {

  final case class Chunk(
                          chunkId:   String,
                          docId:     String,
                          span:      (Int, Int),
                          text:      String,
                          sourceUri: String,
                          hash:      String
                        )

  /** Build the stream of chunks from the retrieval_index parquet. */
  def build(env: StreamExecutionEnvironment): DataStream[Chunk] = {
    val indexPath = AppConfig.index.source

    val srcFn: SourceFunction[Chunk] =
      IndexSource.fromUri(indexPath)          // as you already wrote:contentReference[oaicite:1]{index=1}

    // `addSource` now returns a Scala `DataStream[Chunk]`
    env.addSource(srcFn)
  }
}
