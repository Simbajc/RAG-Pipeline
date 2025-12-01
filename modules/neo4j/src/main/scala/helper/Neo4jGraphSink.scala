package helper

import config.GraphWrite
import config.{UpsertEdge, UpsertNode}
import org.apache.flink.configuration.Configuration
import org.apache.flink.streaming.api.functions.sink.{RichSinkFunction, SinkFunction}
import org.neo4j.driver.exceptions.ServiceUnavailableException
import org.neo4j.driver.{AuthTokens, Driver, GraphDatabase, Session}

final class Neo4jGraphSink(uri: String, user: String, password: String)
  extends RichSinkFunction[GraphWrite] {

  @transient private var driver: Driver = _
  @transient private var session: Session = _

  override def open(parameters: Configuration): Unit = {
    driver = GraphDatabase.driver(uri, AuthTokens.basic(user, password))
    session = driver.session()
  }

  override def invoke(write: GraphWrite, context: SinkFunction.Context): Unit = {
    try {
      write match {
        case UpsertNode("Concept", id, props) =>
          val cypher =
            s"MERGE (c:Concept {conceptId: '$id'}) " +
              s"SET c += ${props.map { case (k, v) => s"$k: '${v.toString}'" }.mkString("{", ", ", "}")}"

          session.run(cypher)

        case UpsertNode("Chunk", id, props) =>
          val cypher =
            s"MERGE (ch:Chunk {chunkId: '$id'}) " +
              s"SET ch += ${props.map { case (k, v) => s"$k: '${v.toString}'" }.mkString("{", ", ", "}")}"

          session.run(cypher)

        case UpsertEdge("Chunk", from, "MENTIONS", "Concept", to, props) =>
          val cypher =
            s"""
               |MERGE (ch:Chunk {chunkId: '$from'})
               |MERGE (c:Concept {conceptId: '$to'})
               |MERGE (ch)-[r:MENTIONS]->(c)
               |SET r += ${props.map { case (k, v) => s"$k: '${v.toString}'" }.mkString("{", ", ", "}")}
               |""".stripMargin

          session.run(cypher)

        case UpsertEdge("Concept", a, rel, "Concept", b, props) =>
          // Normalize relation name to a valid Neo4j relationship type
          val safeRel =
            rel.toUpperCase
              .replaceAll("[^A-Z0-9_]", "_") // spaces etc. -> '_'

          val cypher =
            s"""
               |MERGE (a:Concept {conceptId: '$a'})
               |MERGE (b:Concept {conceptId: '$b'})
               |MERGE (a)-[r:$safeRel]->(b)
               |SET r += ${props.map { case (k, v) => s"$k: '${v.toString}'" }.mkString("{", ", ", "}")}
               |""".stripMargin

          session.run(cypher)
      }
    } catch {
        case e: ServiceUnavailableException =>
          println(s"[NEO4J-SINK] Neo4j unavailable: ${e.getMessage}")
        // do nothing else: job continues
    }
  }

  override def close(): Unit = {
    if (session != null) session.close()
    if (driver != null) driver.close()
  }
}
