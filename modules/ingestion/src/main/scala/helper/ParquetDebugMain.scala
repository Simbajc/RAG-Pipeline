package helper

import org.apache.hadoop.conf.Configuration
import org.apache.hadoop.fs.Path

import org.apache.parquet.hadoop.ParquetReader
import org.apache.parquet.example.data.Group
import org.apache.parquet.example.data.simple.convert.GroupRecordConverter
import org.apache.parquet.schema.MessageType
import org.apache.parquet.hadoop.api.{InitContext, ReadSupport}
import org.apache.parquet.io.api.RecordMaterializer

object ParquetDebugMain {

  /** Simple ReadSupport that yields Group rows. */
  private final class GroupReadSupport extends ReadSupport[Group] {
    override def init(context: InitContext): ReadSupport.ReadContext =
      new ReadSupport.ReadContext(context.getFileSchema)

    override def prepareForRead(
                                 conf: Configuration,
                                 keyValueMeta: java.util.Map[String, String],
                                 fileSchema: MessageType,
                                 readContext: ReadSupport.ReadContext
                               ): RecordMaterializer[Group] =
      new GroupRecordConverter(fileSchema)
  }

  def main(args: Array[String]): Unit = {
    val indexPath: String =
      if (args.nonEmpty) args(0)
      else "input/retrieval_index"

    println(s"[debug] Reading retrieval_index parquet under: $indexPath")

    val conf = new Configuration()
    val root = new Path(indexPath)
    val fs   = root.getFileSystem(conf)

    // Recursively collect all .parquet files
    def listParquetFiles(p: Path): List[Path] = {
      val buff = scala.collection.mutable.ListBuffer.empty[Path]
      val it   = fs.listStatusIterator(p)
      while (it.hasNext) {
        val st = it.next()
        if (st.isDirectory) {
          buff ++= listParquetFiles(st.getPath)
        } else if (st.isFile && st.getPath.getName.endsWith(".parquet")) {
          buff += st.getPath
        }
      }
      buff.toList
    }

    val files = listParquetFiles(root)

    if (files.isEmpty) {
      println(s"[debug] No .parquet files found under $indexPath")
      return
    }

    println(s"[debug] Found ${files.size} parquet file(s). Dumping up to 20 rows...\n")

    var printed = 0
    val maxRows = 20

    val readSupport = new GroupReadSupport()

    files.foreach { file =>
      if (printed < maxRows) {
        println(s"--- File: ${file.toString} ---")

        val reader =
          ParquetReader
            .builder[Group](readSupport, file)
            .withConf(conf)
            .build()

        try {
          var row: Group = reader.read()
          while (row != null && printed < maxRows) {
            println(row.toString)
            printed += 1
            row = reader.read()
          }
        } finally {
          reader.close()
        }
      }
    }

    println(s"[debug] Done. Printed $printed row(s).")
  }
}
