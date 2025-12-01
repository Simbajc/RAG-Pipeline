package api

import akka.http.scaladsl.marshallers.sprayjson.SprayJsonSupport
import spray.json._

import api.Neo4jReadClient._

object JsonProtocol extends SprayJsonSupport with DefaultJsonProtocol {

  // ------------- Health ---------------------

  final case class HealthResponse(status: String = "ok")
  implicit val healthFormat = jsonFormat1(HealthResponse)

  // ------------- Evidence -------------------

  implicit val evidenceFormat = jsonFormat4(Evidence)

  // ------------- Explore / Neighbors --------

  final case class ExploreRequest(
                                   conceptId: String,
                                   direction: String = "both",
                                   depth: Int = 1,
                                   limit: Int = 100,
                                   edgeTypes: Option[Seq[String]] = None
                                 )

  implicit val neighborNodeFormat = jsonFormat3(NeighborNode)
  implicit val neighborEdgeFormat = jsonFormat3(NeighborEdge)
  implicit val neighborhoodFormat = jsonFormat3(Neighborhood)
  implicit val exploreRequestFormat = jsonFormat5(ExploreRequest)

  // ------------- Query ----------------------

  final case class QueryRequest(
                                 lemma: String,
                                 topK: Int = 10
                               )

  final case class QueryItem(
                              conceptId: String,
                              lemma: String,
                              surface: String,
                              chunkIds: Seq[String]
                            )

  final case class QueryResponse(
                                  query: QueryRequest,
                                  items: Seq[QueryItem],
                                  total: Int
                                )

  implicit val queryRequestFormat  = jsonFormat2(QueryRequest)
  implicit val queryItemFormat     = jsonFormat4(QueryItem)
  implicit val queryResponseFormat = jsonFormat3(QueryResponse)

  /** Helper to map from Neo4jReadClient.QueryHit to a JSON item. */
  def toItem(hit: QueryHit): QueryItem =
    QueryItem(
      conceptId = hit.conceptId,
      lemma     = hit.lemma,
      surface   = hit.surface,
      chunkIds  = hit.chunkIds
    )
}
