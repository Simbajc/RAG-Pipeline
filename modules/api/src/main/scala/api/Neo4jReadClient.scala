package api

import org.neo4j.driver.{
  AuthTokens,
  Driver,
  GraphDatabase,
  Session,
  SessionConfig,
  Value,
  Values
}
import org.neo4j.driver.types.{Node, Relationship}

import scala.collection.JavaConverters._

/**
 * Thin read-only Neo4j client used by the REST API.
 */
final class Neo4jReadClient(
                             uri: String,
                             user: String,
                             password: String,
                             database: String
                           ) {

  import Neo4jReadClient._

  private val driver: Driver =
    GraphDatabase.driver(uri, AuthTokens.basic(user, password))

  private def session(): Session =
    driver.session(SessionConfig.forDatabase(database))

  def close(): Unit = driver.close()

  /** Simple health check. */
  def ping(): Boolean = {
    val s = session()
    try {
      s.run("RETURN 1 AS ok").single().get("ok").asInt() == 1
    } catch {
      case _: Throwable => false
    } finally {
      s.close()
    }
  }

  // ---------------------------------------------------------------------------
  // Evidence lookup: GET /v1/evidence/{chunkId}
  // ---------------------------------------------------------------------------

  def getEvidence(chunkId: String): Option[Evidence] = {
    val s = session()
    try {
      val cypher =
        """MATCH (ch:Chunk {chunkId: $id})
          |RETURN ch.chunkId   AS chunkId,
          |       ch.text      AS text,
          |       ch.docId     AS docId,
          |       ch.sourceUri AS sourceUri
          |""".stripMargin

      val result = s.run(cypher, Values.parameters("id", chunkId))

      if (!result.hasNext) None
      else {
        val r = result.next()
        Some(
          Evidence(
            chunkId = r.get("chunkId").asString(),
            text    = r.get("text").asString(),
            docId =
              if (r.get("docId").isNull) None else Some(r.get("docId").asString()),
            sourceUri =
              if (r.get("sourceUri").isNull) None else Some(r.get("sourceUri").asString())
          )
        )
      }
    } finally {
      s.close()
    }
  }

  // ---------------------------------------------------------------------------
  // Graph exploration: GET /v1/graph/concept/{conceptId}/neighbors
  // ---------------------------------------------------------------------------

  def getConceptNeighborhood(
                              conceptId: String,
                              direction: String,
                              depth: Int,
                              limit: Int,
                              edgeTypes: Seq[String]
                            ): Option[Neighborhood] = {

    // For now we ignore edgeTypes and direction in the pattern and just pull
    // all relationships up to a certain depth between concepts/chunks.
    val relFilter = "RELATES_TO|CO_OCCURS|MENTIONS"

    val cypher =
      s"""
         |MATCH (c:Concept {conceptId: $$id})
         |CALL apoc.path.subgraphNodes(c, {
         |  maxLevel: $$depth,
         |  relationshipFilter: '$relFilter'
         |})
         |YIELD node
         |WITH collect(DISTINCT node) AS nodes
         |MATCH (n)-[r]->(m)
         |WHERE n IN nodes AND m IN nodes
         |RETURN nodes AS nodes, collect(DISTINCT r) AS relationships
         |LIMIT $$limit
         |""".stripMargin

    val s = session()
    try {
      val res = s.run(
        cypher,
        Values.parameters(
          "id",    conceptId,
          "depth", Int.box(depth),
          "limit", Int.box(limit)
        )
      )

      if (!res.hasNext) None
      else {
        val rec = res.next()

        val nodes = rec
          .get("nodes")
          .asList((v: Value) => v.asNode())
          .asScala

        val rels = rec
          .get("relationships")
          .asList((v: Value) => v.asRelationship())
          .asScala

        // Build map of Neo4j node-id -> NeighborNode
        val nodesById: Map[String, NeighborNode] =
          nodes.map { n =>
            val id    = n.id().toString
            val label = n.labels().asScala.headOption.getOrElse("Unknown")

            val rawProps = n.asMap().asScala.toMap
            val props: Map[String, String] =
              rawProps.map { case (k, v) => k -> (if (v == null) "null" else v.toString) }

            id -> NeighborNode(id, label, props)
          }.toMap

        val edges: Seq[NeighborEdge] =
          rels.map { r: Relationship =>
            NeighborEdge(
              from    = r.startNodeId().toString,
              to      = r.endNodeId().toString,
              relType = r.`type`()
            )
          }.toSeq

        // Pick center: try node whose props.conceptId == conceptId, otherwise any node
        val center: NeighborNode =
          nodesById.values
            .find(_.props.get("conceptId").contains(conceptId))
            .orElse(nodesById.values.headOption)
            .getOrElse(NeighborNode(conceptId, "Concept", Map.empty))

        Some(
          Neighborhood(
            center = center,
            nodes  = nodesById.values.toSeq,
            edges  = edges
          )
        )
      }
    } finally {
      s.close()
    }
  }

  // ---------------------------------------------------------------------------
  // Simple semantic concept lookup: POST /v1/query
  // ---------------------------------------------------------------------------

  def semanticQuery(lemma: String, topK: Int): Seq[QueryHit] = {
    val s = session()
    try {
      val cypher =
        """MATCH (c:Concept)
          |WHERE toLower(c.lemma) CONTAINS toLower($lemma)
          |OPTIONAL MATCH (ch:Chunk)-[:MENTIONS]->(c)
          |WITH c, collect(DISTINCT ch.chunkId) AS chunkIds
          |RETURN c.conceptId AS conceptId,
          |       c.lemma     AS lemma,
          |       c.surface   AS surface,
          |       chunkIds
          |LIMIT $topK
          |""".stripMargin

      val res = s.run(
        cypher,
        Values.parameters(
          "lemma", lemma,
          "topK",  Int.box(topK)
        )
      )

      res.list().asScala.map { r =>
        QueryHit(
          conceptId = r.get("conceptId").asString(),
          lemma     = r.get("lemma").asString(),
          surface   = r.get("surface").asString(),
          chunkIds  = r
            .get("chunkIds")
            .asList((v: Value) => v.asString())
            .asScala
            .toSeq
        )
      }.toSeq
    } finally {
      s.close()
    }
  }
}

/**
 * Companion object holding all the data types that the JSON layer imports as
 * api.Neo4jReadClient.{Evidence, NeighborNode, ...}.
 */
object Neo4jReadClient {

  final case class Evidence(
                             chunkId: String,
                             text: String,
                             docId: Option[String],
                             sourceUri: Option[String]
                           )

  final case class NeighborNode(
                                 id: String,
                                 label: String,
                                 props: Map[String, String]
                               )

  final case class NeighborEdge(
                                 from: String,
                                 to: String,
                                 relType: String
                               )

  final case class Neighborhood(
                                 center: NeighborNode,
                                 nodes: Seq[NeighborNode],
                                 edges: Seq[NeighborEdge]
                               )

  final case class QueryHit(
                             conceptId: String,
                             lemma: String,
                             surface: String,
                             chunkIds: Seq[String]
                           )
}