package helper

import helper.ConceptMapping.getClass
import ingestion.SourceStream.Chunk
import org.apache.flink.streaming.api.functions.source.SourceFunction
import org.apache.parquet.hadoop.example.GroupReadSupport
import org.slf4j.LoggerFactory

import scala.collection.mutable.ListBuffer

// Hadoop FS + Parquet APIs
import org.apache.hadoop.conf.Configuration
import org.apache.hadoop.fs.{FileSystem, Path}
//import java.nio.file.Path => JPath

import org.apache.parquet.hadoop.ParquetReader
import org.apache.parquet.example.data.Group
import org.apache.parquet.example.data.simple.convert.GroupRecordConverter
import org.apache.parquet.schema.MessageType
import org.apache.parquet.hadoop.api.{InitContext, ReadSupport}
import org.apache.parquet.io.api.RecordMaterializer

// Due to kubernetese
import org.apache.hadoop.conf.Configuration
import org.apache.hadoop.fs.Path

/**
 * Factory for a simple Flink SourceFunction[Chunk] that reads your
 * retrieval_index Parquet snapshot (both local and S3/HDFS via Hadoop FS).
 */
object IndexSource {

  /**
   * Single entry point.
   *
   * @param rootUri Path to the retrieval_index directory (can be file://, s3://, s3a://, hdfs://, etc).
   */

  private val log = LoggerFactory.getLogger(getClass)
  def fromUri(rootUri: String): SourceFunction[Chunk] =
    new ParquetIndexSource(rootUri)

  /**
   * SourceFunction that:
   *  - Recursively walks all files under rootUri
   *  - For each *.parquet file, uses ParquetReader[Group]
   *  - (Later) maps each row into a Chunk and emits it into the stream
   */
  private final class ParquetIndexSource(rootUri: String)
    extends SourceFunction[Chunk] {

    @volatile private var running: Boolean = true

    override def run(ctx: SourceFunction.SourceContext[Chunk]): Unit = {
      val conf = new Configuration()

      // due to kubernetes
      // Tell Hadoop to use its own S3A implementation
      conf.set("fs.s3a.impl", "org.apache.hadoop.fs.s3a.S3AFileSystem")
      conf.set("fs.s3.impl",  "org.apache.hadoop.fs.s3a.S3AFileSystem")
      conf.set("fs.AbstractFileSystem.s3a.impl", "org.apache.hadoop.fs.s3a.S3A")

      val root = new Path(rootUri)
      val fs   = root.getFileSystem(conf)

      // Depth-first traversal of all files under the retrieval_index root.
      def traverse(path: Path): Unit = {
        if (!running) return

        val status = fs.getFileStatus(path)

        if (status.isDirectory) {
          val iter = fs.listStatusIterator(path)
          while (running && iter.hasNext) {
            traverse(iter.next().getPath)
          }
        } else if (status.isFile && path.getName.endsWith(".parquet")) {
          readParquetFile(path, conf, ctx)
        }
      }

      traverse(root)
    }

    override def cancel(): Unit = {
      running = false
    }

    // ----------------- Helpers -----------------

    /**
     * Reads a single Parquet file.
     *
     * Expected schema (from your Spark retrieval_index writer):
     *   shard, docId, contentHash, title, language,
     *   chunkId, chunkIx, sectionPath, chunkText, embedding, embDim
     *
     * For now this is wired just enough to compile; you can later
     * add the logic that converts each Group row into a Chunk and
     * calls ctx.collect(chunk).
     */
    private def readParquetFile(
                                 path: Path,
                                 conf: Configuration,
                                 ctx: SourceFunction.SourceContext[Chunk]
                               ): Unit = {
      log.info("start reading data from the indexes")
      val reader = ParquetReader
        .builder[Group](new GroupReadSupport, path)
        .withConf(conf)
        .build()

      try {
        var g: Group = reader.read()
        while (g != null) {

          def getOpt(field: String): Option[String] =
            if (g.getFieldRepetitionCount(field) > 0)
              Some(g.getBinary(field, 0).toStringUsingUTF8)
            else None

          val chunkId  = getOpt("chunkId").getOrElse("")
          val text     = getOpt("chunkText").getOrElse("")
          val hash     = getOpt("contentHash").getOrElse("")

          // Full span since there are no spanStart/spanEnd columns
          val spanFrom = 0
          val spanTo   = text.length

          // ----- NEW: derive docId from directory name -----
          // e.g. .../shard=0/docId=XXXX/part-00000-....parquet
          val docFolder = Option(path.getParent).map(_.getName).getOrElse("")
          val docId =
            if (docFolder.startsWith("docId=")) docFolder.substring("docId=".length)
            else docFolder

          val uri = s"doc:$docId"
          // -----------------------------------------------

          ctx.collect(
            Chunk(
              chunkId   = chunkId,
              docId     = docId,
              span      = (spanFrom, spanTo),
              text      = text,
              sourceUri = uri,
              hash      = hash
            )
          )

          g = reader.read()
        }
      } finally reader.close()
    }

  }
}
