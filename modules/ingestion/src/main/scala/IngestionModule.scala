package ingestion

import config.AppConfig
import helper.{GraphProjector, Neo4jConfig, Neo4jGraphSink}
import org.apache.flink.streaming.api.environment.StreamExecutionEnvironment

/** Entry point for the ingestion module. */
object IngestionModule:

  @main def mainEntry(args: String*): Unit =
    run()

  def main(args: Array[String]): Unit =
    run()

  private def run(): Unit =
    val env        = StreamExecutionEnvironment.getExecutionEnvironment
    val chunks     = SourceStream.build(env)          // will fix this to use config below
    val graphWrites = GraphProjector.project(chunks)

    println("Print Chunks")
    graphWrites.print()

    val neo4jCfg = Neo4jConfig(
      uri      = AppConfig.Neo4jConfig.uri,
      user     = AppConfig.Neo4jConfig.user,
      password = AppConfig.Neo4jConfig.password,
      database = AppConfig.Neo4jConfig.database
    )

    graphWrites.addSink(new Neo4jGraphSink(neo4jCfg))
    env.execute("graphrag-ingestion")