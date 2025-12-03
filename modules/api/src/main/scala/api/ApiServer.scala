package api

import akka.actor.ActorSystem
import akka.http.scaladsl.Http
import akka.http.scaladsl.model._
import akka.http.scaladsl.server.Directives._
import akka.stream.ActorMaterializer

import scala.concurrent.ExecutionContextExecutor
import scala.io.StdIn
import scala.collection.concurrent.TrieMap

import spray.json._
import JsonProtocol._
import config.AppConfig
import api.Neo4jReadClient._
import JsonProtocol.{QueryRequest, QueryResponse, toItem}

/**
 * Single Akka HTTP server exposing the HW3 REST API:
 *
 * - EvidenceService: GET  /v1/evidence/{chunkId}
 * - ExploreService:  GET  /v1/graph/concept/{conceptId}/neighbors
 * - QueryService:    POST /v1/query   (?mode=sync|async)
 * - JobsService:     GET  /v1/jobs/{jobId}
 *                    GET  /v1/jobs/{jobId}/result
 * - ExplainService:  GET  /v1/explain/trace/{requestId}
 * - Health:          GET  /v1/health
 */
object ApiServer {

  // In-memory store for “async” query results; in real deployment this would
  // be backed by Neo4j or a jobs table, and driven by Flink JobsService.
  private val asyncResults: TrieMap[String, QueryResponse] =
    TrieMap.empty[String, QueryResponse]

  def main(args: Array[String]): Unit = {
    implicit val system: ActorSystem           = ActorSystem("graphrag-api")
    implicit val mat: ActorMaterializer        = ActorMaterializer()
    implicit val ec:  ExecutionContextExecutor = system.dispatcher

    val neoClient = new Neo4jReadClient(
      uri      = AppConfig.Neo4jConfig.uri,
      user     = AppConfig.Neo4jConfig.user,
      password = AppConfig.Neo4jConfig.password,
      database = AppConfig.Neo4jConfig.database
    )

    val route =
      pathPrefix("v1") {
        concat(
          // ------------------------------------------------------------
          // Health: GET /v1/health
          // ------------------------------------------------------------
          path("health") {
            get {
              complete(
                HttpResponse(
                  status = StatusCodes.OK,
                  entity = HttpEntity(
                    ContentTypes.`application/json`,
                    HealthResponse().toJson.compactPrint
                  )
                )
              )
            }
          },

          // ------------------------------------------------------------
          // EvidenceService: GET /v1/evidence/{chunkId}
          // ------------------------------------------------------------
          path("evidence" / Segment) { chunkId =>
            get {
              neoClient.getEvidence(chunkId) match {
                case Some(e) =>
                  val json = e.toJson.compactPrint
                  complete(
                    HttpResponse(
                      status = StatusCodes.OK,
                      entity = HttpEntity(ContentTypes.`application/json`, json)
                    )
                  )

                case None =>
                  complete(
                    HttpResponse(
                      status = StatusCodes.NotFound,
                      entity = HttpEntity(
                        ContentTypes.`application/json`,
                        s"""{"error":"Not Found","message":"No evidence for chunkId '$chunkId'"}"""
                      )
                    )
                  )
              }
            }
          },

          // ------------------------------------------------------------
          // ExploreService:
          //   GET /v1/graph/concept/{conceptId}/neighbors
          //   ?direction=in|out|both&depth=1..3&limit=100&edgeTypes=...
          // ------------------------------------------------------------
          path("graph" / "concept" / Segment / "neighbors") { conceptId =>
            parameters(
              "direction".?(default = "both"),
              "depth".as[Int].?(default = 1),
              "limit".as[Int].?(default = 100),
              "edgeTypes".?
            ) { (direction, depth, limit, edgeTypesOpt) =>
              get {
                val edgeTypes: Seq[String] =
                  edgeTypesOpt
                    .map(_.split(",").map(_.trim).filter(_.nonEmpty).toSeq)
                    .getOrElse(Seq("RELATES_TO", "CO_OCCURS", "MENTIONS"))

                neoClient.getConceptNeighborhood(
                  conceptId = conceptId,
                  direction = direction,
                  depth     = depth,
                  limit     = limit,
                  edgeTypes = edgeTypes
                ) match {
                  case Some(nb) =>
                    val json = nb.toJson.compactPrint
                    complete(
                      HttpResponse(
                        status = StatusCodes.OK,
                        entity = HttpEntity(ContentTypes.`application/json`, json)
                      )
                    )

                  case None =>
                    complete(
                      HttpResponse(
                        status = StatusCodes.NotFound,
                        entity = HttpEntity(
                          ContentTypes.`application/json`,
                          s"""{"error":"Not Found","message":"No neighborhood for conceptId '$conceptId'"}"""
                        )
                      )
                    )
                }
              }
            }
          },

          // ------------------------------------------------------------
          // QueryService:
          //   POST /v1/query
          //   Optional query param: mode=sync|async (default=sync)
          //
          //   - sync: 200 + QueryResponse JSON
          //   - async: 202 + jobId, and result available under /v1/jobs/{jobId}
          // ------------------------------------------------------------
          path("query") {
            post {
              parameter("mode".?) { modeOpt =>
                entity(as[QueryRequest]) { req =>
                  val hits  = neoClient.semanticQuery(req.lemma, req.topK)
                  val items = hits.map(toItem)
                  val resp  = QueryResponse(query = req, items = items, total = items.size)

                  modeOpt.map(_.toLowerCase) match {
                    case Some("async") =>
                      val jobId = java.util.UUID.randomUUID().toString
                      asyncResults.put(jobId, resp)

                      val body =
                        s"""{
                           |  "mode": "async",
                           |  "jobId": "$jobId",
                           |  "statusLink": "/v1/jobs/$jobId",
                           |  "pollAfterMs": 2000
                           |}""".stripMargin.replaceAll("\\s+", " ")

                      complete(
                        HttpResponse(
                          status = StatusCodes.Accepted,
                          entity = HttpEntity(ContentTypes.`application/json`, body)
                        )
                      )

                    case _ =>
                      val body = resp.toJson.compactPrint
                      complete(
                        HttpResponse(
                          status = StatusCodes.OK,
                          entity = HttpEntity(ContentTypes.`application/json`, body)
                        )
                      )
                  }
                }
              }
            }
          } ,

          // ------------------------------------------------------------
          // JobsService:
          //   GET /v1/jobs/{jobId}
          //   GET /v1/jobs/{jobId}/result
          //
          //   For HW3 we back this by the in-memory asyncResults map.
          // ------------------------------------------------------------
          pathPrefix("jobs") {
            concat(
              // GET /v1/jobs/{jobId}/result
              path(Segment / "result") { jobId =>
                get {
                  asyncResults.get(jobId) match {
                    case Some(resp) =>
                      val json = resp.toJson.compactPrint
                      complete(
                        HttpResponse(
                          status = StatusCodes.OK,
                          entity = HttpEntity(ContentTypes.`application/json`, json)
                        )
                      )

                    case None =>
                      complete(
                        HttpResponse(
                          status = StatusCodes.NotFound,
                          entity = HttpEntity(
                            ContentTypes.`application/json`,
                            s"""{"error":"Not Found","message":"Unknown jobId '$jobId'"}"""
                          )
                        )
                      )
                  }
                }
              },
                // GET /v1/jobs/{jobId}
                path(Segment) { jobId =>
                  get {
                    val (state, resultLinkOpt) =
                      if (asyncResults.contains(jobId))
                        ("SUCCEEDED", Some(s"/v1/jobs/$jobId/result"))
                      else
                        ("UNKNOWN", None)

                    if (state == "UNKNOWN") {
                      complete(
                        HttpResponse(
                          status = StatusCodes.NotFound,
                          entity = HttpEntity(
                            ContentTypes.`application/json`,
                            s"""{"error":"Not Found","message":"Unknown jobId '$jobId'"}"""
                          )
                        )
                      )
                    } else {
                      val started  = java.time.Instant.now().minusSeconds(5).toString
                      val finished = java.time.Instant.now().toString
                      val resultLinkJson =
                        resultLinkOpt.map(l => s""""resultLink":"$l",""").getOrElse("")

                      val body =
                        s"""{
                           |  "jobId": "$jobId",
                           |  "state": "$state",
                           |  "startedAt": "$started",
                           |  "finishedAt": "$finished",
                           |  $resultLinkJson
                           |  "statusLink": "/v1/jobs/$jobId"
                           |}""".stripMargin.replaceAll("\\s+", " ")

                      complete(
                        HttpResponse(
                          status = StatusCodes.OK,
                          entity = HttpEntity(ContentTypes.`application/json`, body)
                        )
                      )
                    }
                  }
                }
            )

          } ,

          // ------------------------------------------------------------
          // ExplainService:
          //   GET /v1/explain/trace/{requestId}
          //
          //   HW3 implementation: static trace skeleton keyed by requestId.
          //   In a full system this would be stored alongside query execution.
          // ------------------------------------------------------------
          path("explain" / "trace" / Segment) { requestId =>
            get {
              val body =
                s"""{
                   |  "requestId": "$requestId",
                   |  "plan": [
                   |    {"step":"matchTask","cypher":"MATCH (t:Task {name:'JIT Defect Prediction'}) ..."},
                   |    {"step":"filterTime","detail":"p.year >= 2018"},
                   |    {"step":"baselineEdge","detail":"IMPROVES_OVER metric=AUC baseline=Random Forest"}
                   |  ],
                   |  "promptVersions": {"relationScoring":"v3.2"},
                   |  "counters": {"nodesRead":1489,"relsRead":5503,"llmCalls":0,"cacheHits":1}
                   |}""".stripMargin.replaceAll("\\s+", " ")

              complete(
                HttpResponse(
                  status = StatusCodes.OK,
                  entity = HttpEntity(ContentTypes.`application/json`, body)
                )
              )
            }
          }
        )


      }

    val bindingF = Http().newServerAt("0.0.0.0", 8080).bind(route)
    println("GraphRAG API listening on http://localhost:8080 ...")
    println("Press ENTER to stop.")
    StdIn.readLine()

    bindingF
      .flatMap(_.unbind())
      .onComplete { _ =>
        neoClient.close()
        system.terminate()
      }
  }
}
