package helper
import config.{Concept, Mention}
import ingestion.IngestionModule.getClass
import llModels.{Ask, Ollama}
import ingestion.SourceStream.Chunk
import org.slf4j.LoggerFactory

import scala.util.Try

///**
// * A canonical "concept" that we want to represent as a node in the graph.
// *
// * @param conceptId Stable ID for this concept (e.g., hash of lemma).
// * @param lemma     Normalized form (usually lowercase token).
// * @param surface   Original surface form as it appeared in the text.
// * @param origin    How/where this concept was produced (e.g., "heuristic", "llm").
// */
//final case class Concept(
//                          conceptId: String,
//                          lemma:     String,
//                          surface:   String,
//                          origin:    String
//                        )
//
///**
// * A mention of a Concept inside a particular chunk.
// *
// * @param chunkId ID of the chunk where this concept was found.
// * @param concept The Concept itself (shared across many mentions).
// */
//final case class Mention(
//                          chunkId: String,
//                          concept: Concept
//                        )

/**
 * ConceptStage contains the logic for turning normalized chunks
 * into concept mentions.
 *
 * In the Flink job you will typically use it like:
 *
 *   val mentions: DataStream[Mention] =
 *     normalized
 *       .flatMap(ConceptStage.extractHeuristic)
 *       .name("concept-heuristics")
 */
object ConceptMapping {
  private val log = LoggerFactory.getLogger(getClass)

  /**
   * Heuristic extractor – VERY SIMPLE version for homework.
   *
   * For each chunk:
   *   - tokenizes the text on non-word characters,
   *   - lowercases each token to get a lemma,
   *   - builds a Concept for each unique lemma in the chunk,
   *   - wraps each in a Mention(chunkId, concept).
   *
   * Flink's Scala API will allow this to be passed directly to flatMap:
   *   flatMap(ConceptStage.extractHeuristic)
   */
  def extractHeuristic(c: Chunk): Iterable[Mention] = {
    log.info("start extracting Heuristic")
    val text = Option(c.text).getOrElse("")

    // Very naive tokenization: split on non-word characters
    val rawTokens =
      text.split("\\W+")
        .toSeq
        .filter(_.nonEmpty)

    // Group by lowercase lemma to avoid duplicate concepts per chunk
    val byLemma =
      rawTokens.groupBy(_.toLowerCase)

    // Turn each lemma into a single Concept + Mention
    byLemma.toSeq.map { case (lemma, surfaces) =>
      // Pick the first surface form we saw
      val surface = surfaces.head

      // Simple, deterministic conceptId (docId + lemma).
      // You could replace this with a SHA-256 hash if you prefer.
      val conceptId = s"${c.docId}::$lemma"

      val concept = Concept(
        conceptId = conceptId,
        lemma     = lemma,
        surface   = surface,
        origin    = "heuristic"
      )

      Mention(
        chunkId = c.chunkId,
        concept = concept
      )
    }
  }

  /**
   * LLM-based extractor.
   *
   * For each chunk:
   *   - calls the Ollama chat model with a "concept extraction" prompt,
   *   - expects ONE concept per line in the response,
   *   - turns each line into a Concept + Mention,
   *   - tags origin = "llm".
   *
   * This is designed to be best-effort: if the LLM call fails or returns
   * garbage, we simply return no mentions for that chunk.
   */
  def extractWithLLM(endpoint: String): Chunk => Iterable[Mention] = {
    log.info("Start extracting core concept of text")
    (c: Chunk) => {
      log.info(s"[LLM CALLED] chunkId=${c.chunkId}")

      val text = Option(c.text).getOrElse("").trim
      if (text.isEmpty) {
        log.info(s"[LLM SKIP] chunkId=${c.chunkId}: empty text")
        Iterable.empty[Mention]
      } else {
        // normal path: call Ollama, build mentions

        val client = new Ollama(endpoint)

        val question =
          """Extract the most important domain concepts (nouns or noun phrases)
            |from the context. Return your answer as PLAIN TEXT with ONE concept
            |per line. Do not include bullets, numbers or explanations.
            |""".stripMargin

        val maybeRaw: Option[String] =
          Try(Ask.ask(client, question, text)).fold(
            ex => {
              log.info(s"[LLM ERROR] chunkId=${c.chunkId}: ${ex.getMessage}")
              None
            },
            raw => {
              log.info(s"[LLM RAW] chunkId=${c.chunkId}: " + raw.take(200))
              Some(raw)
            }
          )

        val mentions: Seq[Mention] =
          maybeRaw
            .toSeq
            .flatMap { raw =>
              val lines =
                raw
                  .split("\n")
                  .toSeq
                  .map(_.trim)
                  .map(_.replaceAll("""^[-*\d\.\)\s]+""", "")) // strip bullets
                  .map(_.trim)
                  .filter(_.nonEmpty)

              if (lines.isEmpty) {
                log.info(s"[LLM PARSE] chunkId=${c.chunkId}: no non-empty lines")
              }

              lines.distinct.map { phrase =>
                val lemma = phrase.toLowerCase
                val conceptId = s"${c.docId}::llm::$lemma"

                val concept = Concept(
                  conceptId = conceptId,
                  lemma = lemma,
                  surface = phrase,
                  origin = "llm"
                )

                Mention(
                  chunkId = c.chunkId,
                  concept = concept
                )
              }
            }

        log.info(
          s"[LLM RESULT] chunkId=${c.chunkId}, size=${mentions.size}, sample=${mentions.take(3).mkString(", ")}"
        )

        mentions
      }
    }
  }

}
