package helper

object GraphUpsert {
  import config.{GraphWrite, UpsertEdge, UpsertNode}

  def mapper: GraphWrite => Seq[String] = {
    case UpsertNode("Concept", id, props) =>
      Seq(
        "MERGE (c:Concept {conceptId: $id}) SET c += $props",
      )
    case UpsertNode("Chunk", id, props) =>
      Seq(
        "MERGE (ch:Chunk {chunkId: $id}) SET ch += $props",
      )
    case UpsertEdge("Chunk", from, "MENTIONS", "Concept", to, props) =>
      Seq(
        "MERGE (ch:Chunk {chunkId: $from})",
        "MERGE (c:Concept {conceptId: $to})",
        "MERGE (ch)-[r:MENTIONS]->(c) SET r += $props"
      )
    case UpsertEdge("Concept", a, "RELATES_TO", "Concept", b, props) =>
      Seq(
        "MERGE (a:Concept {conceptId: $a})",
        "MERGE (b:Concept {conceptId: $b})",
        "MERGE (a)-[r:RELATES_TO]->(b) SET r += $props"
      )
    case UpsertEdge("Concept", a, "CO_OCCURS", "Concept", b, props) =>
      Seq(
        "MERGE (a:Concept {conceptId: $a})",
        "MERGE (b:Concept {conceptId: $b})",
        "MERGE (a)-[r:CO_OCCURS]->(b) SET r.freq = coalesce(r.freq,0) + $inc"
      )
  }
}


