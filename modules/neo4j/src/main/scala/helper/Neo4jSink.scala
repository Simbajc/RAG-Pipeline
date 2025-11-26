//package helper
//
////import org.neo4j.driver._
//import org.neo4j.driver.exceptions.ServiceUnavailableException
//
//import org.apache.flink.streaming.api.functions.sink.{RichSinkFunction, SinkFunction}
//import org.apache.flink.configuration.Configuration
//
//import scala.collection.mutable.ArrayBuffer
//import scala.util.control.NonFatal
//
///**
// * A Neo4j sink for Flink that performs batched writes.
// *
// * It uses a user-supplied function (upsertMapper) that converts
// * your GraphWrite object into a runnable Cypher query + parameters.
// */
//object Neo4jSink {
//
//  // ----------------------------------------
//  //  Builder
//  // ----------------------------------------
//  final case class Builder[T](
//                               uri: String,
//                               user: String,
//                               pass: String,
//                               batchSize: Int = 200,
//                               maxRetries: Int = 8,
//                               upsertMapper: T => CypherCommand = (_: T) =>
//                                 CypherCommand("RETURN 1", Map.empty)
//                             ) {
//
//    def withBatchSize(size: Int): Builder[T] =
//      copy(batchSize = size)
//
//    def withMaxRetries(n: Int): Builder[T] =
//      copy(maxRetries = n)
//
//    def withUpsertMapper(f: T => CypherCommand): Builder[T] =
//      copy(upsertMapper = f)
//
//    def build(): SinkFunction[T] =
//      new Neo4jBatchSink[T](
//        uri        = uri,
//        user       = user,
//        pass       = pass,
//        batchSize  = batchSize,
//        maxRetries = maxRetries,
//        mapper     = upsertMapper
//      )
//  }
//
//  /** Public constructor */
//  def builder[T](uri: String, user: String, pass: String): Builder[T] =
//    Builder(uri, user, pass)
//
//  // ----------------------------------------
//  //  Data model
//  // ----------------------------------------
//
//  /** A compiled Cypher statement + parameter map */
//  final case class CypherCommand(
//                                  query: String,
//                                  params: Map[String, Any]
//                                )
//
//  // ----------------------------------------
//  //  Sink implementation
//  // ----------------------------------------
//  final class Neo4jBatchSink[T](
//                                 uri: String,
//                                 user: String,
//                                 pass: String,
//                                 batchSize: Int,
//                                 maxRetries: Int,
//                                 mapper: T => CypherCommand
//                               ) extends RichSinkFunction[T] {
//
//    @transient private var driver: Driver = _
//    private val buffer = ArrayBuffer.empty[CypherCommand]
//
//    override def open(parameters: Configuration): Unit = {
//      driver = GraphDatabase.driver(uri, AuthTokens.basic(user, pass))
//      println(s"[Neo4jSink] Connected → $uri as $user")
//    }
//
//    override def invoke(value: T, context: SinkFunction.Context): Unit = {
//      val cmd = mapper(value)
//      buffer.append(cmd)
//
//      if (buffer.size >= batchSize)
//        flushWithRetry()
//    }
//
//    override def close(): Unit = {
//      flushWithRetry()
//      if (driver != null) driver.close()
//      println("[Neo4jSink] Closed")
//    }
//
//    // ----------------------------------------
//    //  Flush Logic
//    // ----------------------------------------
//    private def flushWithRetry(): Unit = {
//      if (buffer.isEmpty) return
//
//      var attempt = 0
//      var done = false
//
//      while (!done && attempt < maxRetries) {
//        attempt += 1
//        try {
//          flushBatch()
//          done = true
//        } catch {
//          case _: ServiceUnavailableException =>
//            println(s"[Neo4jSink] Retry $attempt/$maxRetries — Neo4j unavailable")
//            Thread.sleep(200L * attempt)
//          case NonFatal(e) =>
//            println(s"[Neo4jSink] Unhandled exception: ${e.getMessage}")
//            throw e
//        }
//      }
//
//      buffer.clear()
//    }
//
//    private def flushBatch(): Unit = {
//      val session = driver.session(SessionConfig.forDatabase("neo4j"))
//      try {
//        val tx = session.beginTransaction()
//        buffer.foreach { cmd =>
//          tx.run(cmd.query, cmd.params.asJava)
//        }
//        tx.commit()
//      } finally {
//        session.close()
//      }
//
//      println(s"[Neo4jSink] Flushed ${buffer.size} records")
//    }
//  }
//}
