package helper

import ingestion.SourceStream.Chunk

object Normalize {

  /** Top-level function used in the Flink pipeline. */
  def cleanAndTag(c: Chunk): Chunk = {
    // 1) Normalize whitespace in the chunk text
    val cleanedText = normalizeWhitespace(c.text)

    // 2) Compute a stable hash on the cleaned text
    val newHash = stableHash(cleanedText)

    // 3) Return updated chunk (only text and hash change)
    c.copy(
      text = cleanedText,
      hash = newHash
    )
  }

  /** Collapse multiple spaces/newlines, trim ends, standardize line breaks. */
  private def normalizeWhitespace(s: String): String = {
    if (s == null) ""
    else s.replaceAll("\\s+", " ").trim
  }

  /** Stable SHA-256 hash as hex string. */
  private def stableHash(s: String): String = {
    import java.security.MessageDigest

    val md  = MessageDigest.getInstance("SHA-256")
    val dig = md.digest(s.getBytes("UTF-8"))
    dig.map("%02x".format(_)).mkString
  }
}
