package ingestion
import ingestion.SourceStream.Chunk
import org.apache.flink.util.Collector
import config.AppConfig
import helper.ConceptRelationshipMapping.CoOccur
import helper.{Concept, ConceptMapping, ConceptRelationshipMapping, GraphProjector, Mention, Neo4jConfig, Neo4jGraphSink, Normalize}
import org.apache.flink.streaming.api.datastream.DataStream
import org.apache.flink.streaming.api.environment.StreamExecutionEnvironment


/** Entry point for the ingestion module. */
object IngestionModule:

  @main def mainEntry(args: String*): Unit =
    run()

  def main(args: Array[String]): Unit =
    run()

  private def run(): Unit =
    val env        = StreamExecutionEnvironment.getExecutionEnvironment
    val chunks: DataStream[SourceStream.Chunk] = SourceStream.build(env)

    // get the clean noramlized text chunks
    val normalized: DataStream[Chunk] =
      chunks
        .map[Chunk](Normalize.cleanAndTag _)   // make the type explicit
        .name("normalize")


    // group words together that have the same base llama (lamma: run | Words: run, ran, runs)
    val heuristicMentions: DataStream[Mention] =
      normalized
        .flatMap { (c: Chunk, out: Collector[Mention]) =>
          ConceptMapping
            .extractHeuristic(c)
            .foreach(out.collect)
        }
        .name("concept-heuristics")


    // Get the text chunk overall main concept
    val llmMentions: DataStream[Mention] =
      normalized
        .flatMap { (c: Chunk, out: Collector[Mention]) =>
          ConceptMapping
            .extractWithLLM("http://ollama:11434")(c)
            .foreach(out.collect)
        }
        .name("concept-llm")

    // Aim is to unionize the grouping of similar words and the overall concept of the chunks
    val mentions: DataStream[Mention] =
      heuristicMentions.union(llmMentions)



    // Make the relationships between concepts. (Edges of the Graph)
    val coOccurs: DataStream[CoOccur] =
      mentions
        .keyBy(_.chunkId)
        .process(ConceptRelationshipMapping.localCoOccurrence(windowSize = 3))
        .name("cooccur-local")


    val graphWrites = GraphProjector.project(normalized)

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