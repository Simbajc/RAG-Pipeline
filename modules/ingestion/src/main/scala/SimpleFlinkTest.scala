package ingestion

import org.apache.flink.streaming.api.scala._

object SimpleFlinkTest {
  def main(args: Array[String]): Unit = {
    val env = StreamExecutionEnvironment.getExecutionEnvironment

    val data: DataStream[String] =
      env.fromElements("one", "two", "three")

    data.print("simple")

    env.execute("simple-flink-smoke-test")
  }
}
