package ingestion

//import model.{RelationCandidate, ScoredRelation}
import config.{RelationCandidate, ScoredRelation}
import helper.RelationScoringJson
import llModels.Ask
import llModels.Ollama
import org.apache.flink.configuration.Configuration
import org.apache.flink.streaming.api.functions.ProcessFunction
import org.apache.flink.util.Collector

/** LLM-based relation scoring stage: RelationCandidate -> ScoredRelation. */
object RelationScoringStage {

  def withOllama(
                  baseUrl: String,
                  model: String,
                  temperature: Double
                ): ProcessFunction[RelationCandidate, ScoredRelation] = {

    new ProcessFunction[RelationCandidate, ScoredRelation] {

      @transient private var client: Ollama = _

      override def open(parameters: Configuration): Unit = {
        // One client per task/slot – not per element
        client = Ollama.client(baseUrl)
      }

      override def processElement(
                                   value: RelationCandidate,
                                   ctx: ProcessFunction[RelationCandidate, ScoredRelation]#Context,
                                   out: Collector[ScoredRelation]
                                 ): Unit = {
        val raw = Ask.askRelationshipScoring(
          client      = client,        // <— your functions still get a client
          candidate   = value,
          model       = model,
          temperature = temperature
        )

        val (predicate, confidence) = RelationScoringJson.parse(raw)

        out.collect(
          ScoredRelation(
            a          = value.a,
            predicate  = predicate,
            b          = value.b,
            confidence = confidence,
            evidence   = value.evidence
          )
        )
      }
    }
  }

}
