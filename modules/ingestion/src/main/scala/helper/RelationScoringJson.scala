package helper

import scala.util.matching.Regex

object RelationScoringJson {

  /** Extract the JSON line from a possibly decorated/raw string. */
  private def extractJsonLine(raw: String): String =
    raw.linesIterator
      .map(_.trim)
      .find(line => line.startsWith("{") && line.endsWith("}"))
      .getOrElse(raw.trim)

  /** Parse {"predicate": "...", "confidence": 0.9} into (predicate, confidence). */
  def parse(raw: String): (String, Double) = {
    val json = extractJsonLine(raw)

    val predicateRe: Regex  = """"predicate"\s*:\s*"([^"]*)"""".r
    val confidenceRe: Regex = """"confidence"\s*:\s*([0-9.]+)""".r

    val predicate: String =
      predicateRe
        .findFirstMatchIn(json)
        .map(_.group(1))
        .getOrElse("unknown")

    val confidence: Double =
      confidenceRe
        .findFirstMatchIn(json)
        .flatMap(m => m.group(1).toDoubleOption)
        .getOrElse(0.0)

    (predicate, confidence)
  }
}
