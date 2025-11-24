package ingestion


import SourceStream.Chunk
import org.apache.flink.streaming.api.datastream.DataStream
import org.apache.flink.streaming.api.environment.StreamExecutionEnvironment
import org.apache.flink.streaming.api.functions.ProcessFunction
import org.apache.flink.util.Collector
import config.{AppConfig, Concept, GraphWrite, Mention, RelationCandidate, ScoredRelation}
import helper.{ConceptMapping, ConceptRelationshipMapping, GraphProjector, Neo4jConfig, Neo4jGraphSink, Normalize}
import helper.ConceptRelationshipMapping.CoOccur
import llModels.{Ask, Ollama}
import helper.Quality



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
        .returns(classOf[Mention])
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

    // Build cheap semantic-relation candidates from co-occurrence.
    val candidates: DataStream[RelationCandidate] =
      ConceptRelationshipMapping.makeCandidates(normalized, mentions, coOccurs)

    // create ONE client for the job
    val ollamaClient = new Ollama("http://ollama:11434")

//    val scored: DataStream[ScoredRelation] =
//      candidates
//        .process(
//          Ask.askRelationshipScoring(
//            client = ollamaClient,
//            model = "llama3:instruct")
//        )
//        .name("relation-scoring")


    val scored: DataStream[ScoredRelation] =
      candidates
        .process(
          RelationScoringStage.withOllama(
            client      = ollamaClient,
            model       = "llama3:instruct",
            temperature = 0.0
          )
        )
        .name("relation-scoring")

    val graphWrites: DataStream[GraphWrite] =
      GraphProjector.project(normalized, mentions, coOccurs, scored)

//    val graphWrites = GraphProjector.project(normalized)

    println("Print Chunks")
    graphWrites.print()

    val neo4jCfg = Neo4jConfig(
      uri      = AppConfig.Neo4jConfig.uri,
      user     = AppConfig.Neo4jConfig.user,
      password = AppConfig.Neo4jConfig.password,
      database = AppConfig.Neo4jConfig.database
    )

//    val neo4jSink = neo4jSink
//      .builder[GraphWrite]("bolt://neo4j:7687", "neo4j", sys.env("NEO4J_PASS"))
//      .withUpsertMapper(GraphUpsert.mapper)
//      .withBatchSize(200)
//      .withMaxRetries(8)
//      .build()

//    graphWrites.addSink(neo4jSink).name("neo4j-sink")

    val neo4jSink = new Neo4jGraphSink(neo4jCfg)

    val writesWithMetrics = Quality.attachMetrics(graphWrites)

    writesWithMetrics
      .addSink(neo4jSink)
      .name("neo4j-sink")



//    graphWrites
//      .addSink(neo4jSink)
//      .name("neo4j-sink")


    env.execute("graphrag-ingestion")

//    Quality.attachMetrics(env, writes