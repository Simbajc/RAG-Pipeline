package ingestion

import org.apache.flink.api.common.typeinfo.TypeInformation
import org.apache.flink.streaming.api.scala._
import SourceStream.Chunk
import config.AppConfig.ollamaModel.baseUrl
import config.{AppConfig, GraphWrite, Mention, RelationCandidate, ScoredRelation}
import helper.ConceptRelationshipMapping.CoOccur
import helper.{ConceptMapping, ConceptRelationshipMapping, GraphProjector, GraphUpsert, Neo4jGraphSink, Normalize}
import org.apache.flink.api.common.functions.{FlatMapFunction, MapFunction}
import org.apache.flink.util.Collector
import org.apache.flink.api.java.functions.KeySelector
import org.slf4j.LoggerFactory





/** Entry point for the ingestion module.
 *
 * Entry Point to the entire application
 * */
object IngestionModule {

  private val log = LoggerFactory.getLogger(getClass)

  def main(args: Array[String]): Unit = {
    // 1) Scala environment
    val env: StreamExecutionEnvironment =
      StreamExecutionEnvironment.getExecutionEnvironment

    env.setParallelism(1)
    println("Start Program")


    println("Start Chunking")
    val chunks: DataStream[Chunk] =
      SourceStream
        .build(env) // <-- whatever helper you wrote
        .name("chunks-from-parquet")

    chunks.print("chunks-from-parquet")
    println("Finish Chunking")
    log.info("Finish Chunking")


    // 2) TypeInformation for Chunk (Scala 2.12)
    implicit val chunkTypeInfo: TypeInformation[Chunk] =
      createTypeInformation[Chunk]

    // 3) Simple in-memory source
    val testChunks: DataStream[Chunk] =
      env.fromElements(
        Chunk("chunk1", "doc1", (0, 38),
          "This is a test about Flink and Scala.",
          "file://local-test/doc1", "h1"),
        Chunk("chunk2", "doc2", (0, 26),
          "Another small test chunk.",
          "file://local-test/doc2", "h2")
      )

    println("Start Normalization")
    // 4) Optional normalize stage (still produces Chunk)
    // 3) Use an explicit MapFunction – NO Scala lambda
    val normalized: DataStream[Chunk] = {
      chunks
//      testChunks
        .map(new MapFunction[Chunk, Chunk] {
          override def map(value: Chunk): Chunk =
            Normalize.cleanAndTag(value)
        })
        .name("normalize+ner")
    }

    // 5) At least one sink
    normalized.print("normalized")
    println("Finish Normalization")
    log.info("Finish Normalization")

    // 6) Exactly one execute, at the very end



    println("Start heuristic")
    // 2) heuristic mentions
    val heuristicMentions: DataStream[Mention] =
      normalized
        .flatMap(new FlatMapFunction[Chunk, Mention] {
          override def flatMap(c: Chunk, out: Collector[Mention]): Unit = {
            ConceptMapping
              .extractHeuristic(c)
              .foreach(out.collect)
          }
        })
        .name("concept-heuristics")

    heuristicMentions.print("heuristic")
    println("Finish heuristic")

    log.info("Finish Heuristic")


//    val ollamaEndpoint: String = "http://127.0.0.1:11434" // or "http://localhost:11434"

    val ollamaEndpoint: String =
      sys.env.getOrElse("OLLAMA_BASE_URL", "http://127.0.0.1:11434")



    val llmMentions: DataStream[Mention] =
      normalized
        .flatMap(new FlatMapFunction[Chunk, Mention] {
          override def flatMap(c: Chunk, out: Collector[Mention]): Unit = {
            ConceptMapping
              .extractWithLLM(ollamaEndpoint)(c)
              .foreach(out.collect)
          }
        })
        .name("concept-llm")

    llmMentions.print("llm-mentions")

    log.info("Finish llm-mentions")



    // Aim is to unionize the grouping of similar words and the overall concept of the chunks
    val mentions: DataStream[Mention] =
      heuristicMentions
        .union(llmMentions)

    mentions
      .print("all-mentions")
      .name("all-mentions-sink")

    log.info("Finish all-mentions-sink")




    // 2) Optional: co-occurrence edges between concepts
    implicit val coOccurTypeInfo: TypeInformation[CoOccur] =
      TypeInformation.of(classOf[CoOccur])

    val coOccurs: DataStream[CoOccur] =
      mentions
        .keyBy(new KeySelector[Mention, String] {
          override def getKey(value: Mention): String =
            value.chunkId
        })
        .process(ConceptRelationshipMapping.localCoOccurrence(windowSize = 3))
        .name("cooccur-local")

    coOccurs.print("co-occurs")

    log.info("Finish CoOccurs")

//    println("Cocurence is Passing")
//    env.execute("graphrag-ingestion")



    // Build cheap semantic-relation candidates from co-occurrence.
    val candidates: DataStream[RelationCandidate] =
      ConceptRelationshipMapping.makeCandidates(normalized, mentions, coOccurs)


    candidates.print("relation-candidates")

    log.info("Finish relationsal-canidates")

//    println("Cocurence is Passing")


//    // create ONE client for the job
//    val ollamaClient = new Ollama("http://ollama:11434")



    val scored: DataStream[ScoredRelation] =
      candidates
        .process(
          RelationScoringStage.withOllama(
            baseUrl = ollamaEndpoint,
            model = AppConfig.ollamaModel.scoringModel,
            temperature = 0.0
          )
        )
        .name("relation-scoring")
    scored.print("relation-scoring")

    log.info("Finish Relation-Scoring")



    val neo4jSink = new Neo4jGraphSink(
      AppConfig.Neo4jConfig.uri,
      AppConfig.Neo4jConfig.user,
      AppConfig.Neo4jConfig.password
    )

    val graphWrites: DataStream[GraphWrite] =
      GraphProjector.project(normalized, mentions, coOccurs, scored)
    graphWrites.print()

    graphWrites
      .addSink(neo4jSink)
      .name("neo4j-sink")

    log.info("Finish neo4j-sink")




    println("Finish Program")
    env.execute("graphrag-ingestion")

    log.info("Finish Program")





  }


}