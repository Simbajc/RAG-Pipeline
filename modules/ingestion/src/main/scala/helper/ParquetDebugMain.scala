package ingestion

import org.apache.hadoop.conf.Configuration
import org.apache.hadoop.fs.{FileSystem, Path}

import org.apache.parquet.example.data.Group
import org.apache.parquet.example.data.simple.convert.GroupRecordConverter
import org.apache.parquet.hadoop.ParquetReader
import org.apache.parquet.hadoop.api.{InitContext, ReadSupport}
import org.apache.parquet.io.api.RecordMaterializer
import org.apache.parquet.schema.MessageType

import config.AppConfig  // uses the same app.conf you already moved to core

/**
 * Small local debug entry point.
 *
 * It bypasses Flink completely and just:
 *   1) Reads the retrieval_index Parquet files using Hadoop + Parquet.
 *   2) Prints the first few rows (docId, chunkId, chunkText, contentHash).
 */
object ParquetDebugMain:

  /** Minimal ReadSupport to get Parquet Group records. */
  private final class GroupReadSupport extends ReadSupport[Group]:
    override def init(context: InitContext): ReadSupport.ReadContext =
      // Use the file schema as-is
      new ReadSupport.ReadContext(context.getFileSchema)

    override def prepareForRead(
                                 conf:        Configuration,
                                 keyValueMd:  java.util.Map[String, String],
                                 fileSchema:  MessageType,
                                 readContext: ReadSupport.ReadContext
                               ): RecordMaterializer[Group] =
      new GroupRecordConverter(fileSchema)

  def main(args: Array[String]): Unit =
    // 1) Decide which path to use
    val indexPath: String =
      if args.nonEmpty then args(0)
      else AppConfig.index.source   // from app.conf, e.g. "file:///C:/.../out/retrieval_index"

    println(s"[debug] Reading retrieval_index parquet under: $indexPath")

    val conf   = new Configuration()
    val root   = new Path(indexPath)
    val fs     = root.getFileSystem(conf)

    // 2) Recursively collect all .parquet files under the index path
    def listParquetFiles(p: Path): List[Path] =
      val it   = fs.listStatusIterator(p)
      val buff = scala.collection.mutable.ListBuffer.empty[Path]
      while it.hasNext do
        val st = it.next()
        if st.isDirectory then
          buff ++= listParquetFiles(st.getPath)
        else if st.getPath.getName.endsWith(".parquet") then
          buff += st.getPath
      buff.toList

    val files = listParquetFiles(root)

    if files.isEmpty then
      println(s"[debug] No .parquet files found under $indexPath")
      return

    println(s"[debug] Found ${files.size} parquet file(s). Dumping up to 20 rows...\n")

    // 3) Read a few rows from each parquet file and print them
    var printed = 0
    val maxRows = 20

    files.foreach { file =>
      if printed < maxRows then
        println(s"--- File: ${file.toString} ---")

        val reader: ParquetReader[Group] =
          ParquetReader.builder[Group](new GroupReadSupport, file).build()

        // docId is a partition column: directory name "docId=<hash>"
        val docIdFromPath: String =
          val parentName = file.getParent.getName  // e.g. "docId=236ded44..."
          if parentName.startsWith("docId=") then parentName.substring("docId=".length)
          else parentName

        var keepReading = true
        while keepReading && printed < maxRows do
          val group = reader.read()
          if group == null then
            keepReading = false
          else
            printed += 1

            val chunkId     = group.getBinary("chunkId", 0).toStringUsingUTF8
            val chunkText   = group.getBinary("chunkText", 0).toStringUsingUTF8
            val contentHash = group.getBinary("contentHash", 0).toStringUsingUTF8

            println(s"[$printed] docId=$docIdFromPath")
            println(s"     chunkId=$chunkId")
            println(s"     hash=$contentHash")
            println(s"     textPreview=${chunkText.take(120).replaceAll("\\s+", " ")}")
            println()

        reader.close()
    }


    println(s"[debug] Done. Printed $printed row(s).")
