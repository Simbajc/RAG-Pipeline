package helper

import ingestion.SourceStream.Chunk

import org.apache.flink.streaming.api.functions.source.SourceFunction

// Hadoop FS + Parquet APIs
import org.apache.hadoop.conf.Configuration
import org.apache.hadoop.fs.{FileSystem, Path}

import org.apache.parquet.hadoop.ParquetReader
import org.apache.parquet.example.data.Group
import org.apache.parquet.example.data.simple.convert.GroupRecordConverter
import org.apache.parquet.schema.MessageType
import org.apache.parquet.hadoop.api.{InitContext, ReadSupport}
import org.apache.parquet.io.api.RecordMaterializer

/**
 * Factory for a simple Flink SourceFunction[Chunk] that reads your
 * retrieval_index Parquet snapshot (both local and S3/HDFS via Hadoop FS).
 */
object IndexSource:

  /**
   * Single entry point.
   *
   * @param rootUri Path to the retrieval_index directory (can be file://, s3://, s3a://, hdfs://, etc).
   */
  def fromUri(rootUri: String): SourceFunction[Chunk] =
    new ParquetIndexSource(rootUri)

  /**
   * SourceFunction that:
   *  - Recursively walks all files under rootUri
   *  - For each *.parquet file, uses ParquetReader[Group]
   *  - Maps each row into a Chunk and emits it into the stream
   */
  private final class ParquetIndexSource(rootUri: String)
    extends SourceFunction[Chunk]:

    @volatile private var running: Boolean = true

    override def run(ctx: SourceFunction.SourceContext[Chunk]): Unit =
      val conf = new Configuration()
      val root = new Path(rootUri)
      val fs   = root.getFileSystem(conf)

      // Depth-first traversal of all files under the retrieval_index root.
      def traverse(path: Path): Unit =
        if !running then return

        val status = fs.getFileStatus(path)

        if status.isDirectory then
          val iter = fs.listStatusIterator(path)
          while running && iter.hasNext do
            traverse(iter.next().getPath)
        else if status.isFile && path.getName.endsWith(".parquet") then
          readParquetFile(path, conf, ctx)

      traverse(root)

    override def cancel(): Unit =
      running = false

    // ----------------- Helpers -----------------

    /**
     * Reads a single Parquet file and emits one Chunk per row.
     *
     * Expected schema (from your Spark retrieval_index writer):
     *   shard, docId, contentHash, title, language,
     *   chunkId, chunkIx, sectionPath, chunkText, embedding, embDim
     */
    private def readParquetFile(
                                 file: Path,
                                 conf: Configuration,
                                 ctx:  SourceFunction.SourceContext[Chunk]
                               ): Unit =

      // Minimal ReadSupport to get parquet.example.data.Group rows.
      final class GroupReadSupport extends ReadSupport[Group]:
        override def init(context: InitContext): ReadSupport.ReadContext =
          new ReadSupport.ReadContext(context.getFileSchema)

        override def prepareForRead(
         conf: Configuration,
         keyValueMeta: java.util.Map[String, String],
         fileSchema: MessageType,
         readContext:ReadSupport.ReadContext
        ): RecordMaterializer[Group] =
          new GroupRecordConverter(fileSchema)


