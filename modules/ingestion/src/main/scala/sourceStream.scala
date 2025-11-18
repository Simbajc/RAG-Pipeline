package ingestion

import helper.IndexSource
import config.AppConfig

import org.apache.flink.streaming.api.environment.StreamExecutionEnvironment
import org.apache.flink.streaming.api.datastream.{DataStream, DataStreamSource}
import org.apache.flink.streaming.api.functions.source.SourceFunction

object SourceStream {

  // This must match what IndexSource emits
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
    // Path to retrieval_index root (shard=0, shard=1, ...)
    val indexPath = AppConfig.index.source

    // Your SourceFunction[Chunk] that reads Parquet
    val srcFn: SourceFunction[Chunk] =
      IndexSource.fromUri(indexPath)

    // Start from the raw source
    val src: DataStreamSource[Chunk] = env.addSource(srcFn)

    // Just return the stream – no .name/.uid to avoid API issues
    src
  }
}
