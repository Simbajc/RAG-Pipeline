package llModels

import com.typesafe.config.ConfigFactory

import scala.concurrent.duration._
import config.AppConfig

import io.circe._
import io.circe.generic.semiauto._

import sttp.client3._
import sttp.client3.circe._
import sttp.client3.httpclient.HttpClientSyncBackend
import sttp.model.MediaType
import OllamaJson._

class Ollama(base: String = Ollama.detectBaseUrl()) {

  import OllamaJson._

  private val cfg = ConfigFactory.load().getConfig("app.models.ollama")
  private val modelName = AppConfig.ollamaModel.chatModel

  private val connectMs = Option(AppConfig.ollamaModel.connectMs).getOrElse(30000)
  private val readMs    = Option(AppConfig.ollamaModel.readMs).getOrElse(60000)
  private val attempts  = Option(AppConfig.ollamaModel.attempts).getOrElse(3)

  private val backend: SttpBackend[Identity, Any] =
    HttpClientSyncBackend(
      options = SttpBackendOptions.connectionTimeout(connectMs.millis)
    )

  private val baseUrl: String = base

  private val embedUrl     = uri"$base/api/embed"
  private val chatUrl      = uri"$base/api/chat"
  private val readinessUrl = uri"$base/api/tags"

  def chat(
            model: String,
            messages: Vector[ChatMessage],              // you can keep Vector here
            options: ChatOptions =
            ChatOptions(
              stop = None,
              num_ctx = Some(Option(AppConfig.ollamaModel.contextWindowSize).getOrElse(8000)),
              temperature = Some(Option(AppConfig.ollamaModel.temperature).getOrElse(0.2))
            ),
            attempts: Int = 3
          ): String = {
    val req = basicRequest
      .post(chatUrl)
      .readTimeout(readMs.millis)
      // ChatReq.messages is List[ChatMessage], so convert here:
      .body(ChatReq(model, messages.toList, stream = false, options = options, keep_alive = Some("10m")))
      .response(asJson[ChatResp])

    sendWithRetry[ChatResp](req, attempts).message.content
  }

  def awaitReady(timeout: FiniteDuration = 60.seconds, pollEvery: FiniteDuration = 2.seconds): Unit = {
    val deadline = timeout.fromNow
    var lastError: Throwable = null

    while (deadline.hasTimeLeft) {
      try {
        val resp = basicRequest
          .get(readinessUrl)
          .readTimeout(pollEvery)
          .response(asStringAlways)
          .send(backend)

        if (resp.code.isSuccess) return
      } catch { case t: Throwable => lastError = t }

      Thread.sleep(pollEvery.toMillis)
    }

    throw new RuntimeException(s"Ollama server at $baseUrl not ready", lastError)
  }

  private def sendWithRetry[A](
                                req: Request[Either[ResponseException[String, Error], A], Any],
                                attempts: Int
                              ): A = {
    var i = 0
    var last: Throwable = null

    while (i < attempts) {
      try {
        val resp = req.send(backend)
        return resp.body.fold(throw _, identity)
      } catch { case t: Throwable => last = t }

      Thread.sleep(5000L * (i + 1))
      i += 1
    }

    throw new RuntimeException(s"Ollama retry failed", last)
  }
}

object Ollama {
  def detectBaseUrl(): String =
    sys.env.get("OLLAMA_BASE_URL")
      .orElse(sys.env.get("OLLAMA_HOST"))
      .getOrElse("http://127.0.0.1:11434")

  def client(baseUrl: String): Ollama = new Ollama(baseUrl)
}

object OllamaJson {

  import io.circe.{Decoder, Encoder}
  import io.circe.generic.semiauto._

  // --------- model types ----------
  final case class EmbedReq(model: String, input: List[String])
  final case class EmbedResp(embeddings: List[List[Float]])

  final case class ChatMessage(role: String, content: String)

  final case class ChatOptions(
                                stop: Option[List[String]] = None,
                                num_ctx: Option[Int] = None,
                                temperature: Option[Double] = None
                              )


  final case class ChatReq(
                            model: String,
                            messages: List[ChatMessage],
                            stream: Boolean = false,
                            options: ChatOptions = ChatOptions(),
                            keep_alive: Option[String] = None
                          )

  final case class ChatMsg(
                            role: String,
                            content: String
                          )

  final case class ChatResp(
                             message: ChatMsg
                           )

  // --------- ONLY ONE Encoder/Decoder PER TYPE ----------
  implicit val encEmbedReq:   Encoder[EmbedReq]   = deriveEncoder
  implicit val decEmbedResp:  Decoder[EmbedResp]  = deriveDecoder

  implicit val encChatMessage: Encoder[ChatMessage] = deriveEncoder
  implicit val encChatOptions: Encoder[ChatOptions] = deriveEncoder
  implicit val encChatReq:     Encoder[ChatReq]     = deriveEncoder

  implicit val decChatMsg:     Decoder[ChatMsg]     = deriveDecoder
  implicit val decChatResp:    Decoder[ChatResp]    = deriveDecoder
}
