//package helper
//
//import org.apache.flink.configuration.Configuration
//import org.apache.flink.streaming.api.functions.sink.RichSinkFunction
//import org.apache.flink.streaming.api.functions.sink.SinkFunction
//import org.neo4j.driver.Values
//import config.{GraphWrite, UpsertNode, UpsertEdge}
//import scala.jdk.CollectionConverters._
//
//
//import org.neo4j.driver.{
//  AuthTokens,
//  Driver,
//  GraphDatabase,
//  Session,
//  SessionConfig
//}
//
//import scala.jdk.CollectionConverters._
//
///**
// * Neo4j connection config.
// * Later you should populate this from application.conf.
// */
//final case class Neo4jConfig(
//                              uri:      String,
//                              user:     String,
//                              password: String,
//                              database: String
//                            )
//
///**
// * Flink sink that consumes GraphWrite events and performs
// * idempotent MERGE upserts into Neo4j.
// */
//final class Neo4jGraphSink(config: Neo4jConfig)
//  extends RichSinkFunction[GraphWrite] {
//
//  @transient private var driver: Driver = _
//  @transient private var session: Session = _
//
//  // ---------------------------------------------------------------------------
//  // Lifecycle
//  // ---------------------------------------------------------------------------
//
//  override def open(parameters: Configuration): Unit = {
//    super.open(parameters)
//    // Create Neo4j driver + session once per task
//    driver = GraphDatabase.driver(
//      config.uri,
//      AuthTokens.basic(config.user, config.password)
//    )
//    session = driver.session(
//      SessionConfig.builder().withDatabase(config.database).build()
//    )
//  }
//
//  override def close(): Unit = {
//    try if (session != null) session.close()
//    finally if (driver != null) driver.close()
//    super.close()
//  }
//
//  // ---------------------------------------------------------------------------
//  // SinkFunction: handle each GraphWrite
//  // ---------------------------------------------------------------------------
//
//  override def invoke(
//                       value: GraphWrite,
//                       context: SinkFunction.Context
//                     ): Unit = {
//    value match {
//      case UpsertNode(label, id, props) =>
//        upsertNode(label, id, props)
//
//      case UpsertEdge(fromLabel, fromId, rel, toLabel, toId, props) =>
//        upsertEdge(fromLabel, fromId, rel, toLabel, toId, props)
//    }
//  }
//
//  // ---------------------------------------------------------------------------
//  // Upsert helpers
//  // ---------------------------------------------------------------------------
//
//  /**
//   * Idempotent node upsert:
//   * MERGE (n:Label {id: $id}) SET n += $props
//   */
//  protected def upsertNode(
//                          label: String,
//                          id: String,
//                          props: Map[String, Any]
//                        ): Unit = {
//    val cypher =
//      s"""
//         |MERGE (n:`$label` {id: $$id})
//         |SET   n += $$props
//         |""".stripMargin
//
//    // Use Values.parameters to build the Java Map[String, Object]
//    val paramMap = Values.parameters(
//      "id", id,
//      "props", props.asJava
//    )
//
//    session.writeTransaction(tx => {
//      tx.run(cypher, paramMap).consume()
//      null
//    })
//  }
//
//  protected def upsertEdge(
//                          fromLabel: String,
//                          fromId: String,
//                          rel: String,
//                          toLabel: String,
//                          toId: String,
//                          props: Map[String, Any]
//                        ): Unit = {
//    val cypher =
//      s"""
//         |MERGE (from:`$fromLabel` {id: $$fromId})
//         |MERGE (to:`$toLabel`     {id: $$toId})
//         |MERGE (from)-[r:`$rel`]->(to)
//         |SET   r += $$props
//         |""".stripMargin
//
//    val paramMap = Values.parameters(
//      "fromId", fromId,
//      "toId", toId,
//      "props", props.asJava
//    )
//
//    session.writeTransaction(tx => {
//      tx.run(cypher, paramMap).consume()
//      null
//    })
//  }
//}