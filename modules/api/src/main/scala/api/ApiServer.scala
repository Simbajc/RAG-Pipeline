package api

import akka.actor.ActorSystem
import akka.http.scaladsl.Http
import akka.http.scaladsl.model._
import akka.http.scaladsl.server.Directives._
import akka.stream.ActorMaterializer

import scala.concurrent.ExecutionContextExecutor
import scala.io.StdIn
import spray.json._
import JsonProtocol._
import config.AppConfig

object ApiServer {

  def main(args: Array[String]): Unit = {
    implicit val system: ActorSystem                        = ActorSystem("graphrag-api")
    implicit val mat: ActorMaterializer                     = ActorMaterializer()
    implicit val ec:  ExecutionContextExecutor              = system.dispatcher

    // For HW3 we hard-code the Neo4j connection used by ingestion:
    // Neo4jConfig {
    //   uri: "bolt://localhost:7687"
    //   user: "neo4j"
    //   password: "password"
    //   database: "neo4j"
    // }
    val neoClient = new Neo4jReadClient(
      uri      = AppConfig.Neo4jConfig.uri,
      user     = AppConfig.Neo4jConfig.user,
      password = AppConfig.Neo4jConfig.password,
      database = AppConfig.Neo4jConfig.database
    )

    val route =
      pathPrefix("v1") {

        // ------------------------------------------------------------------
        // 1) EvidenceService: GET /v1/evidence/{chunkId}
        // ------------------------------------------------------------------
        path("evidence" / Segment) { chunkId =>
          get {
            neoClient.getEvidence(chunkId) match {
              case Some(e) =>
                // Manually render JSON; avoids marshaller type issues.
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
        } ~
          // ------------------------------------------------------------------
          // 2) ExploreService: GET /v1/graph/concept/{conceptId}/neighbors
          //    Query params: direction, depth, limit, edgeTypes
          // ------------------------------------------------------------------
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
          }
      }

    val bindingF = Http().newServerAt("0.0.0.0", 8080).bind(route)
    println("GraphRAG API listening on http://localhost:8080 ...")
    println("Press ENTER to stop.")
    StdIn.readLine()

    bindingF
      .flatMap(_.unbind())
      .onComplete(_ => {
        neoClient.close()
        system.terminate()
      })
  }
}
