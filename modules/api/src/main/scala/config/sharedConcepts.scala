package config




/**
 * A canonical "concept" that we want to represent as a node in the graph.
 *
 * @param conceptId Stable ID for this concept (e.g., hash of lemma).
 * @param lemma     Normalized form (usually lowercase token).
 * @param surface   Original surface form as it appeared in the text.
 * @param origin    How/where this concept was produced (e.g., "heuristic", "llm").
 */
final case class Concept(
                          conceptId: String,
                          lemma:     String,
                          surface:   String,
                          origin:    String
                        )

/**
 * A mention of a Concept inside a particular chunk.
 *
 * @param chunkId ID of the chunk where this concept was found.
 * @param concept The Concept itself (shared across many mentions).
 */
final case class Mention(
                          chunkId: String,
                          concept: Concept
                        )

final case class RelationCandidate(a: Concept, b: Concept, evidence: String)

final case class ScoredRelation(
                                 a: Concept,
                                 predicate: String,
                                 b: Concept,
                                 confidence: Double,
                                 evidence: String
                               )

sealed trait GraphWrite
final case class UpsertNode(label: String, id: String, props: Map[String, Any]) extends GraphWrite
final case class UpsertEdge(fromLabel: String, fromId: String, rel: String, toLabel: String, toId: String, props: Map[String, Any]) extends GraphWrite

object sharedConcepts {

}
