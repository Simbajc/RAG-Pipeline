package ingestion

import org.scalatest.funsuite.AnyFunSuite
import org.apache.flink.streaming.api.functions.ProcessFunction
import org.apache.flink.util.Collector
import org.apache.flink.configuration.Configuration


import config.{AppConfig, Concept, RelationCandidate, ScoredRelation}

class RelationScoringStageSpec extends AnyFunSuite {

  test("print ScoredRelation produced by RelationScoringStage.withOllama") {
    val a = Concept("c1", "aspirin", "Aspirin", "test")
    val b = Concept("c2", "headache", "headache", "test")

    val candidate = RelationCandidate(
      a        = a,
      b        = b,
      evidence = "Aspirin is used to treat headaches."
    )

    // Use env override if present, otherwise config
    val ollamaEndpoint: String =
      sys.env.getOrElse("OLLAMA_BASE_URL", AppConfig.ollamaModel.baseUrl)

    val fn: ProcessFunction[RelationCandidate, ScoredRelation] =
      RelationScoringStage.withOllama(
        baseUrl     = ollamaEndpoint,
        model       = AppConfig.ollamaModel.chatModel,
        temperature = 0.0
      )

    // IMPORTANT: initialize Flink operator state (creates the Ollama client)
    fn.open(new Configuration())

    val outBuffer = scala.collection.mutable.ListBuffer.empty[ScoredRelation]

    val collector = new Collector[ScoredRelation] {
      override def collect(record: ScoredRelation): Unit =
        outBuffer += record
      override def close(): Unit = ()
    }

    fn.processElement(
      candidate,
      null,       // no Context needed for this test
      collector
    )

    assert(outBuffer.nonEmpty, "No ScoredRelation was emitted")
    val scored = outBuffer.head

    println("=========== SCORED RELATION ===========")
    println(s"predicate  = ${scored.predicate}")
    println(s"confidence = ${scored.confidence}")
    println(s"a          = ${scored.a}")
    println(s"b          = ${scored.b}")
    println(s"evidence   = ${scored.evidence}")
    println("=======================================")

    assert(scored.predicate.nonEmpty)
    assert(scored.confidence >= 0.0 && scored.confidence <= 1.0)
  }
}
