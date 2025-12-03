# Graph-Based Retrieval-Augmented Generation (GraphRAG)
### **CS 441 – Homework 3 Full Documentation**
**Author:** Simbarashe Chinomona

This document provides the **complete, unified architecture and documentation** for the CS441 HW3 GraphRAG system. It combines:

- README  
- Architecture description  
- Module-by-module breakdown  
- Interaction diagrams  
- Part 1 ingestion pipeline  
- Part 2 API service  
- Prerequisites  
- Deployment  
- Troubleshooting  
- Commands and workflow examples  

You may use this as a **single master documentation file** for your project.

---

# ============================================================
# 1. PROJECT OVERVIEW
# ============================================================

This project implements a **Graph-based Retrieval-Augmented Generation (GraphRAG)** system using:

- **Scala 2.12**
- **Apache Flink (DataStream API)**
- **Neo4j Graph Database**
- **Ollama LLM runtime**
- **Akka HTTP API (Part 2)**

**Part 1** builds a **streaming knowledge graph** from document chunks.  
**Part 2** exposes a full REST API for evidence lookup, graph exploration, and semantic query.

---

# ============================================================
# 2. PREREQUISITES & INSTALLATION
# ============================================================

## Required Software

| Component | Version | Notes |
|----------|---------|-------|
| Scala | **2.12.x** | Required for Flink API compatibility |
| sbt | **1.9+** | Build & test |
| Java | **11 or 17** | Works with Flink & Neo4j |
| Apache Flink | **1.17 – 1.20** | Local or cluster |
| Neo4j | **5.x** | APOC enabled |
| Ollama | Latest | Models pulled |
| Docker | Optional | For local Neo4j/Ollama |

---

## Required Ollama Models

```bash
ollama pull llama3.1
ollama pull mxbai-embed-large
```

---

## Environment Variables

```bash
export OLLAMA_BASE_URL="http://localhost:11434"
export NEO4J_URI="bolt://localhost:7687"
export NEO4J_USER="neo4j"
export NEO4J_PASSWORD="password"
```

---

## Build Project

```bash
sbt clean compile
```

---

## Run Flink Job (Local Mode)

```bash
./flink/bin/flink run \
  -c ingestion.IngestionModule \
  modules/ingestion/target/scala-2.12/graphrag-ingestion-assembly-0.1.0-SNAPSHOT.jar
```

---

## Run API Server (Part 2)

```bash
sbt "project api" run
```

Server starts at:

**http://localhost:8080/v1/**

---

# ============================================================
# 3. HIGH-LEVEL SYSTEM ARCHITECTURE
# ============================================================

The GraphRAG system consists of two major parts:

---

## PART 1 — **Flink Streaming Pipeline**
Builds a Neo4j concept graph:

1. **Chunk ingestion**
2. **Concept extraction**  
   • Heuristics  
   • LLM-assisted extraction  
3. **Co-occurrence analysis**
4. **Relation candidate generation**
5. **LLM scoring using Ollama**
6. **Graph projection**
7. **Idempotent upserts into Neo4j**

---

## PART 2 — **REST API Service**
Provides:

- Evidence lookup  
- Concept neighborhood expansion  
- Semantic query  
- Async job model  
- Explainability traces  

Powered by:

- **Akka HTTP**
- **Neo4jReadClient**

---

# ============================================================
# 4. ARCHITECTURE DIAGRAMS
# ============================================================

## 4.1 Full System Overview

```
                ┌──────────────────────────┐
                │      API Server (Part 2) │
                │  /v1/query, evidence...  │
                └──────────┬───────────────┘
                           │
                   ┌───────▼────────┐
                   │ Neo4jReadClient │
                   └───────┬────────┘
                           │ READS
                           ▼
                    ┌──────────────┐
                    │    Neo4j     │
                    │ Concept Graph│
                    └──────┬───────┘
                       WRITES ▲
                              │
              ┌───────────────┴────────────────┐
              │   Flink Ingestion Pipeline     │
              │  Concept → Relations → Graph   │
              └───────────────┬────────────────┘
                              │
                      ┌───────▼───────┐
                      │     LLM       │
                      │   (Ollama)    │
                      └───────────────┘
```

---

# ============================================================
# 5. MODULE-BY-MODULE BREAKDOWN
# ============================================================

## 5.1 `core/`
- Domain models  
- Config loader  
- Logging utilities  

## 5.2 `llm/`
- Ollama client  
- Prompt builders  
- JSON scorers  

## 5.3 `ingestion/`
- Flink job  
- Concept extraction  
- Co-occurrence  
- Relation scoring  
- Graph projection  

## 5.4 `neo4j-write/`
- Flink sink → Neo4j  
- Idempotent MERGE logic  

## 5.5 `neo4j-read/`
- Evidence queries  
- Neighborhood exploration  
- Semantic search  

## 5.6 `api/`
- Akka HTTP API  
- Endpoints: query, jobs, evidence, explain  

---

# ============================================================
# 6. FULL INGESTION PIPELINE
# ============================================================

```
Chunks → Concepts → Co-Occurs → RelationCandidates → LLM Scoring → Neo4j Upserts
```

**ScoredRelation example:**

```json
{ "predicate": "TREATS", "confidence": 0.92 }
```

---

# ============================================================
# 7. FULL API LAYER
# ============================================================

## Evidence
`GET /v1/evidence/{chunkId}`

## Neighborhood
`GET /v1/graph/concept/{id}/neighbors`

## Semantic Query
`POST /v1/query`

## Jobs
`POST /v1/query?mode=async`

---

# ============================================================
# 8. CONFIGURATION
# ============================================================

`application.conf`:

```
ollama { baseUrl = "http://localhost:11434" }
neo4j  { uri="bolt://localhost:7687" ... }
```

---

# ============================================================
# 9. TROUBLESHOOTING
# ============================================================

### Ollama Not Reachable  
Run:  
```bash
ollama serve
```

### Neo4j Login Failure  
Reset via browser.

### API Empty  
Run ingestion first.

---

# ============================================================
# 10. TESTING
# ============================================================

```bash
sbt test
```

Tests include:

- ConceptRelationshipMappingTest  
- GraphProjectorSpec  
- RelationScoringStageSpec  
- Neo4jGraphSinkIntegrationSpec  

---

# ============================================================
# 11. END-TO-END WORKFLOW
# ============================================================

### 1. Build JAR
```bash
sbt clean assembly
```

### 2. Run Flink Job
```bash
./flink/bin/flink run -c ingestion.IngestionModule graphrag-ingestion-assembly.jar
```

### 3. Run API
```bash
sbt "project api" run
```

---

# ============================================================
# 12. OPTIONAL — AWS EKS DEPLOYMENT
# ============================================================

```bash
kubectl apply -f deploy/neo4j.yaml
kubectl apply -f deploy/ollama-daemonset.yaml
helm install graphrag-flink deploy/flink-values.yaml
kubectl apply -f deploy/api.yaml
```

---

# END OF DOCUMENTATION
