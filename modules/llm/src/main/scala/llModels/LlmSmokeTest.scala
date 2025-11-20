package llModels

object LlmSmokeTest {

  def main(args: Array[String]): Unit = {
    val client = new Ollama()      // uses Ollama.detectBaseUrl()
    client.awaitReady()            // will throw if Ollama is not running

    val context =
      """This is a tiny context.
        |2 + 2 = 4.
        |""".stripMargin

    val question = "According to the context, what is 2 + 2?"

    val answer = Ask.ask(client, question, context)

    println("=== Ollama response ===")
    println(answer)
  }
}
