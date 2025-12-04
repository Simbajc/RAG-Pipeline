package config

import com.typesafe.config.{Config, ConfigFactory}
//import org.apache.kerby.config.Config

import java.nio.file.{Path, Paths}

/** Centralized, typed access to application.conf for HW1 / HW2. */
object AppConfig {

  // Load default app config; supports -Dconfig.resource=... overrides
  private val cfg: Config = ConfigFactory.load().getConfig("app")

  // ---- helpers ----
  private def path(key: String): Path = Paths.get(cfg.getString(key)).normalize()

  object index {
    val source: String = cfg.getString("index.source")
    val snapshotPath: String = cfg.getString("index.source")
  }

  object Neo4jConfig {
    val uri: String = cfg.getString("Neo4jConfig.uri")
    val user: String = cfg.getString("Neo4jConfig.user")
    val password: String = cfg.getString("Neo4jConfig.password")
    val database: String = cfg.getString("Neo4jConfig.database")
  }

  object ollamaModel {
    val baseUrl: String = cfg.getString("models.ollama.baseUrl")
    val connectMs: Int = cfg.getInt("models.ollama.connectMs")
    val attempts: Int = cfg.getInt("models.ollama.attempts")
    val readMs: Int = cfg.getInt("models.ollama.readMs")
    val scoringModel: String = cfg.getString("models.ollama.scoringModel")
    val chatModel: String = cfg.getString("models.ollama.chatModel")
    val contextWindowSize: Int = cfg.getInt("models.ollama.num_ctx")
    val temperature: Double = cfg.getDouble("models.ollama.temperature")
    val maxModelContextBlock: Int = cfg.getInt("models.ollama.maxModelContextBlock")

  }

}

