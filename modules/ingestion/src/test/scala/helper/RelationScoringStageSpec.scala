package ingestion

import org.scalatest.funsuite.AnyFunSuite
import org.apache.flink.streaming.api.functions.ProcessFunction
import org.apache.flink.util.Collector

import config.{AppConfig, Concept, RelationCandidate, ScoredRelation}
import llModels.Ollama

class RelationScoringStageSpec extends AnyFunSuite {

  test("print ScoredRelation produced by RelationScoringStage.withOllama") {
    val a = Concept("c1", "aspirin", "Aspirin", "test")
    val b = Concept("c2", "headache", "headache", "test")

    val candidate = RelationCandidate(
      a        = a,
      b        = b,
      evidence = "Aspirin is used to treat headaches."
    )

    val client = new Ollama(AppConfig.ollamaModel.baseUrl)

    val fn: ProcessFunction[RelationCandidate, ScoredRelation] =
      RelationScoringStage.withOllama(
        client      = client,
        model       = AppConfig.ollamaModel.chatModel,
        temperature = 0.0
      )

    val outBuffer = scala.collection.mutable.ListBuffer.empty[ScoredRelation]

    val collector = new Collector[ScoredRelation] {
      override def collect(record: ScoredRelation): Unit =
        outBuffer += record
      override def close(): Unit = ()
    }

    fn.processElement(
      candidate,
      null,       // we are not using Context in this stage
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

    // loose assertions so the test passes regardless of exact predicate
    assert(scored.predicate.nonEmpty)
    assert(scored.confidence >= 0.0 && scored.confidence <= 1.0)
  }
}
