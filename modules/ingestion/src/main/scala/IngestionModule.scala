package ingestion

import org.apache.flink.api.common.typeinfo.TypeInformation
import org.apache.flink.streaming.api.scala._
import SourceStream.Chunk
import helper.Normalize
import org.apache.flink.api.common.functions.MapFunction


//import org.apache.flink.api.common.typeinfo.{TypeHint, TypeInformation}
//import org.apache.flink.streaming.api.scala.{StreamExecutionEnvironment, DataStream, _}
//import SourceStream.Chunk
import org.apache.flink.api.common.typeinfo.TypeInformation
//import org.apache.flink.streaming.api.scala._

//import org.apache.flink.streaming.api.functions.ProcessFunction
//import config.{AppConfig, Concept, GraphWrite, Mention, RelationCandidate, ScoredRelation}
//import helper.{ConceptMapping, ConceptRelationshipMapping, GraphProjector,  Normalize}
//Neo4jConfig, Neo4jGraphSink,
//import helper.ConceptRelationshipMapping.CoOccur
//import llModels.{Ask, Ollama}
//import org.apache.flink.api.common.functions.{FlatMapFunction, MapFunction}
//import org.apache.flink.util.Collector
//import helper.Quality
//import scala.collection.JavaConverters._




/** Entry point for the ingestion module. */
object IngestionModule {

  def main(args: Array[String]): Unit = {
    // 1) Scala environment
    val env: StreamExecutionEnvironment =
      StreamExecutionEnvironment.getExecutionEnvironment

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

    // 4) Optional normalize stage (still produces Chunk)
    // 3) Use an explicit MapFunction – NO Scala lambda
    val normalized: DataStream[Chunk] =
      testChunks
        .map(new MapFunction[Chunk, Chunk] {
          override def map(value: Chunk): Chunk =
            Normalize.cleanAndTag(value)
        })
        .name("normalize+ner")

    // 5) At least one sink
    normalized.print("normalized")

    // 6) Exactly one execute, at the very end
    env.execute("graphrag-ingestion")

//    // 3) Tell map exactly what R is
//    val normalized: DataStream[Chunk] =
//      testChunks
//        .map[Chunk]((c: Chunk) => Normalize.cleanAndTag(c))
//        .name("normalize+ner")

//    normalized.print("normalized")

//    env.execute("graphrag-ingestion")


    // ACTIVATE AT LEAST ONE SINK
//    testChunks.print("testChunks")

//    val testChunks: DataStream[Chunk] =
//      env.fromElements(
//        Chunk("chunk1", "doc1", (0, 38), "This is a test about Flink and Scala.", "file://local-test/doc1", "h1"),
//        Chunk("chunk2", "doc2", (0, 26), "Another small test chunk.", "file://local-test/doc2", "h2")
//      )
//    println("Passed testChunks no asJava")

//    val sampleChunks = new java.util.ArrayList[Chunk]()
//    sampleChunks.add(
//      Chunk("chunk1", "doc1", (0, 38), "This is a test about Flink and Scala.", "file://local-test/doc1", "h1")
//    )
//    sampleChunks.add(
//      Chunk("chunk2", "doc2", (0, 26), "Another small test chunk.", "file://local-test/doc2", "h2")
//    )
//
//    val testChunks: DataStream[Chunk] =
//      env.fromCollection(sampleChunks)

    // ADD THIS: at least one sink
//    testChunks.print("testChunks")

    println("Print Chunks")

    // MOVE execute down here and make sure only one execute exists
//    env.execute("graphrag-ingestion")

    // 1) normalize
//    val normalized: DataStream[Chunk] =
////        chunks
//        testChunks
//        .map((value: Chunk) => Normalize.cleanAndTag(value))
//        .returns(chunkTypeInfo) // <- important
//        .name("normalize+ner")
//    normalized.print("normalized")

//    env.execute("graphrag-ingestion")

//    val normalized: DataStream[Chunk] =
////      chunks
//      testChunks
//        .map(new MapFunction[Chunk, Chunk] {
//          override def map(value: Chunk): Chunk =
//            Normalize.cleanAndTag(value)
//        })
//        .returns(chunkTypeInfo)
//        .name("normalize+ner")

    // 2) heuristic mentions
//    val heuristicMentions: DataStream[Mention] =
//      normalized
//        .flatMap(new FlatMapFunction[Chunk, Mention] {
//          override def flatMap(c: Chunk, out: Collector[Mention]): Unit = {
//            ConceptMapping
//              .extractHeuristic(c)
//              .foreach(out.collect)
//          }
//        })
//        .returns(mentionTypeInfo) // <- important
//        .name("concept-heuristics")
//
//    heuristicMentions.print("heuristic")

    // group words together that have the same base llama (lamma: run | Words: run, ran, runs)
//    val heuristicMentions: DataStream[Mention] =
//      normalized
//        .flatMap((c: Chunk, out: Collector[Mention]) => {
//          ConceptMapping
//            .extractHeuristic(c)
//            .foreach(out.collect)
//        })
//        .name("concept-heuristics")

//    implicit val relationCandidateTypeInfo: TypeInformation[RelationCandidate] =
//      TypeInformation.of(classOf[RelationCandidate])
    //    // Get the text chunk overall main concept

//        val ollamaEndpoint: String = "http://127.0.0.1:11434" // or "http://localhost:11434"
//        val llmMentions: DataStream[Mention] =
//          normalized
//            .flatMap { (c: Chunk, out: Collector[Mention]) =>
//              ConceptMapping
//                .extractWithLLM(ollamaEndpoint)(c)
//                .foreach(out.collect)
//            }
//            .returns(mentionTypeInfo)
//            .name("concept-llm")

    // LLM-powered concept extraction as explicit UDF
//    final class LlmmMentionFlatMap(ollamaEndpoint: String)
//      extends FlatMapFunction[Chunk, Mention]
//        with Serializable {
//
//      override def flatMap(c: Chunk, out: Collector[Mention]): Unit = {
//        ConceptMapping
//          .extractWithLLM(ollamaEndpoint)(c)
//          .foreach(out.collect)
//      }
//    }
//
//    val ollamaEndpoint: String = "http://127.0.0.1:11434"
//
//    val llmMentions: DataStream[Mention] =
//      normalized
//        .flatMap(new LlmmMentionFlatMap(ollamaEndpoint))
//        .returns(mentionTypeInfo)
//        .name("concept-llm")


    //        llmMentions
//          .print("llm-mentions") // label will appear in the logs
//          .name("llm-mentions-print")

//    val heuristicMentions: DataStream[Mention] =
//      normalized
//        .flatMap(
//          new FlatMapFunction[Chunk, Mention] {
//            override def flatMap(c: Chunk, out: Collector[Mention]): Unit = {
//              ConceptMapping
//                .extractHeuristic(c)
//                .foreach(out.collect)
//            }
//          }
//        )
//        .returns(classOf[Mention])
//        .name("concept-heuristics")
//
//    val llmMentions: DataStream[Mention] =
//      normalized
//        .flatMap(
//          new FlatMapFunction[Chunk, Mention] {
//            override def flatMap(c: Chunk, out: Collector[Mention]): Unit = {
//              ConceptMapping
//                .extractWithLLM("http://ollama:11434")(c)
//                .foreach(out.collect)
//            }
//          }
//        )
//        .returns(classOf[Mention])
//        .name("concept-llm")
//
//    // Aim is to unionize the grouping of similar words and the overall concept of the chunks
//    val mentions: DataStream[Mention] =
//      heuristicMentions
//        .union(llmMentions)
//    mentions.getTransformation.setName("mentions-all")


//
//
//    // Make the relationships between concepts. (Edges of the Graph)
//    val coOccurs: DataStream[CoOccur] =
//      mentions
//        .keyBy(_.chunkId)
//        .process(ConceptRelationshipMapping.localCoOccurrence(windowSize = 3))
//        .name("cooccur-local")
//
//    // Build cheap semantic-relation candidates from co-occurrence.
//    val candidates: DataStream[RelationCandidate] =
//      ConceptRelationshipMapping.makeCandidates(normalized, mentions, coOccurs)
//
//    // create ONE client for the job
//    val ollamaClient = new Ollama("http://ollama:11434")
//
//
//
//    val scored: DataStream[ScoredRelation] =
//      candidates
//        .process(
//          RelationScoringStage.withOllama(
//            baseUrl = "http://ollama:11434",
//            model = "llama3:instruct",
//            temperature = 0.0
//          )
//        )
//        .name("relation-scoring")

//    val graphWrites: DataStream[GraphWrite] =
//      GraphProjector.project(normalized, mentions, coOccurs, scored)

//      val graphWrites = GraphProjector.project(normalized)

    println("Print Chunks")
//    graphWrites.print()
//
//    val neo4jCfg = Neo4jConfig(
//      uri = AppConfig.Neo4jConfig.uri,
//      user = AppConfig.Neo4jConfig.user,
//      password = AppConfig.Neo4jConfig.password,
//      database = AppConfig.Neo4jConfig.database
//    )

    //    val neo4jSink = neo4jSink
    //      .builder[GraphWrite]("bolt://neo4j:7687", "neo4j", sys.env("NEO4J_PASS"))
    //      .withUpsertMapper(GraphUpsert.mapper)
    //      .withBatchSize(200)
    //      .withMaxRetries(8)
    //      .build()

    //    graphWrites.addSink(neo4jSink).name("neo4j-sink")

//    val neo4jSink = new Neo4jGraphSink(neo4jCfg)
//
//    val writesWithMetrics = Quality.attachMetrics(graphWrites)
//
//    writesWithMetrics
//      .addSink(neo4jSink)
//      .name("neo4j-sink")



    //    graphWrites
    //      .addSink(neo4jSink)
    //      .name("neo4j-sink")


//    env.execute("graphrag-ingestion")

    //    Quality.attachMetrics(env, writes



  }


}