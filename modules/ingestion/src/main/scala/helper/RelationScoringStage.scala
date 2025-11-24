package ingestion

//import model.{RelationCandidate, ScoredRelation}
import config.{RelationCandidate, ScoredRelation}
import helper.RelationScoringJson
import llModels.Ask
import llModels.Ollama
import org.apache.flink.streaming.api.functions.ProcessFunction
import org.apache.flink.util.Collector

/** LLM-based relation scoring stage: RelationCandidate -> ScoredRelation. */
object RelationScoringStage {

  def withOllama(
                  client: Ollama,
                  model: String,
                  temperature: Double
                ): ProcessFunction[RelationCandidate, ScoredRelation] =
    new ProcessFunction[RelationCandidate, ScoredRelation] {

      override def processElement(
                                   value: RelationCandidate,
                                   ctx: ProcessFunction[RelationCandidate, ScoredRelation]#Context,
                                   out: Collector[ScoredRelation]
                                 ): Unit = {

        // Call LLM helper (Ollama) to score this candidate
        val raw = Ask.askRelationshipScoring(
          client      = client,
          candidate   = value,
          model       = model,
          temperature = temperature
        )

//        println("=============RAW scoring==============")
//        println(raw)
//        println("=======================================")


        // Parse LLM JSON: {"predicate": "...", "confidence": 0.9}
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
