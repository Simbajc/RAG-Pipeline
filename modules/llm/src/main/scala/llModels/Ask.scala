package llModels

import config.{AppConfig, RelationCandidate}
import llModels.OllamaJson.{ChatMessage, ChatOptions}

object Ask {

  // crude but effective size guard; adjust as needed
  private val MaxQuestionChars = 300
  private val MaxContextChars  = 1500

  // ---------- Generic QA prompt (used by LlmSmokeTest) ----------

  private def buildMessages(question: String, context: String): Vector[ChatMessage] = {
    val q = question.take(MaxQuestionChars)
    val c = context.take(MaxContextChars)

    val system = ChatMessage(
      role = "system",
      content =
        "You are a concise assistant that answers questions using only the provided context. " +
          "If the answer is not present, reply exactly: I can't find that in the context."
    )

    val user = ChatMessage(
      role = "user",
      content =
        s"""Question: $q

Context:
$c"""
    )

    Vector(system, user)
  }

  def ask(
           client: Ollama,
           question: String,
           context: String,
           model: String = AppConfig.ollamaModel.chatModel,
           temperature: Double = 0.0
         ): String = {
    val msgs = buildMessages(question, context)

    val opts = ChatOptions(
      temperature = Some(temperature)
    )

    client.chat(
      messages = msgs,
      model    = model,
      options  = opts
    )
  }

  // ---------- Relationship scoring prompt (for RelationCandidate) ----------

  private def contextFor(candidate: RelationCandidate): String =
    s"""Concept A: ${candidate.a.lemma} (surface: ${candidate.a.surface})
Concept B: ${candidate.b.lemma} (surface: ${candidate.b.surface})

Context:
${candidate.evidence}
"""

  private def buildMessagesRelationshipScoring(candidate: RelationCandidate): Vector[ChatMessage] = {
    val ctx = contextFor(candidate).take(MaxContextChars)

    val system = ChatMessage(
      role = "system",
      content =
        "You are a concise concept relationship scorer. " +
          "Given two concepts A and B and a context passage, infer the semantic relation between A and B. " +
          """Respond ONLY with a single JSON object of the form: {"predicate": "<label or none>", "confidence": <0.0-1.0>}.""" +
          "If no clear relation is expressed, use predicate \"none\" and confidence 0.0."
    )

    val user = ChatMessage(
      role = "user",
      content = ctx
    )

    Vector(system, user)
  }

  def askRelationshipScoring(
                              client: Ollama,
                              candidate: RelationCandidate,
                              model: String = AppConfig.ollamaModel.chatModel,
                              temperature: Double = 0.0
                            ): String = {
    val msgs = buildMessagesRelationshipScoring(candidate)

    val opts = ChatOptions(
      temperature = Some(temperature)
    )

    client.chat(
      messages = msgs,
      model    = model,
      options  = opts
    )
  }
}
