# Graph-Based Retrieval-Augmented Generation (GraphRAG)

Unified documentation for the CS 441 Homework 3 GraphRAG system by Simbarashe Chinomona. This guide combines architecture, prerequisites, and end-to-end workflows for both ingestion and API layers.

## Table of Contents
- [Overview](#overview)
- [Prerequisites](#prerequisites)
- [Setup](#setup)
- [Flink Ingestion Pipeline](#flink-ingestion-pipeline)
- [API Service](#api-service)
- [Configuration](#configuration)
- [Troubleshooting](#troubleshooting)
- [Testing](#testing)
- [End-to-End Workflow](#end-to-end-workflow)
- [AWS EKS Deployment (Optional)](#aws-eks-deployment-optional)

## Overview
GraphRAG builds a streaming knowledge graph (Part 1) and exposes a REST API for graph exploration and semantic querying (Part 2). Core technologies include **Scala 2.12**, **Apache Flink**, **Neo4j**, **Ollama**, and **Akka HTTP**.

### Architecture Snapshot
- **Ingestion (Flink):** Chunk ingestion → concept extraction → co-occurrence → relation candidate generation → LLM scoring → graph projection → Neo4j upserts.
- **API Service (Akka HTTP):** Evidence lookup, concept neighborhood exploration, semantic queries, async jobs, and explainability traces backed by `Neo4jReadClient`.

### Module Guide
- `core/`: Domain models, config loader, logging utilities
- `llm/`: Ollama client, prompt builders, JSON scorers
- `ingestion/`: Flink job, concept extraction, co-occurrence, relation scoring, graph projection
- `neo4j-write/`: Flink sink with idempotent Neo4j MERGE logic
- `neo4j-read/`: Evidence queries, neighborhood exploration, semantic search helpers
- `api/`: Akka HTTP API (query, jobs, evidence, explain)

## Prerequisites

| Component | Version | Notes |
|-----------|---------|-------|
| Scala | **2.12.x** | Required for Flink API compatibility |
| sbt | **1.9+** | Build & test |
| Java | **11 or 17** | Works with Flink & Neo4j |
| Apache Flink | **1.17 – 1.20** | Local or cluster |
| Neo4j | **5.x** | APOC enabled |
| Ollama | Latest | Ensure required models are pulled |
| Docker | Optional | For local Neo4j/Ollama |

Required Ollama models:

```bash
ollama pull llama3.2:3b
ollama pull llama3:instruct
```

## Setup
Set baseline environment variables:

```bash
export OLLAMA_BASE_URL="http://localhost:11434"
export NEO4J_URI="bolt://localhost:7687"
export NEO4J_USER="neo4j"
export NEO4J_PASSWORD="password"
```

Build the project:

```bash
sbt clean compile
```

## Flink Ingestion Pipeline
Run the Flink job locally to construct the Neo4j concept graph:

```bash
./flink/bin/flink run \
  -c ingestion.IngestionModule \
  modules/ingestion/target/scala-2.12/graphrag-ingestion-assembly-0.1.0-SNAPSHOT.jar
```

## API Service
Start the REST API (Part 2):

```bash
sbt -java-home <java version directory path in computer (deferably java 21)> "api/runMain api.ApiServer"
```

The server listens at `http://localhost:8080/v1/` with key endpoints:

- `GET /v1/evidence/{chunkId}` – Evidence lookup
- `GET /v1/graph/concept/{id}/neighbors` – Concept neighborhood
- `POST /v1/query` – Semantic query (sync)
- `POST /v1/query?mode=async` – Async job submission

## Configuration
Primary settings live in `application.conf`:

```
ollama { baseUrl = "http://localhost:11434" }
neo4j  { uri = "bolt://localhost:7687" ... }
```

## Troubleshooting
- **Ollama not reachable:** start the service with `ollama serve`.
- **Neo4j login failure:** reset credentials via the Neo4j browser.
- **Empty API responses:** ensure the ingestion pipeline has populated the graph.

## Testing
Run the full test suite:

```bash
sbt test
```

Key specs: `ConceptRelationshipMappingTest`, `GraphProjectorSpec`, `RelationScoringStageSpec`, `Neo4jGraphSinkIntegrationSpec`.

## End-to-End Workflow
1) Build the shaded JAR:

```bash
sbt clean assembly
```

2) Launch the Flink job:

```bash
./flink/bin/flink run -c ingestion.IngestionModule graphrag-ingestion-assembly.jar
```

3) Start the API server:

```bash
sbt "project api" run
```

## AWS EKS Deployment
An **EKS cluster is required** for the provided deployment assets. The manifests below cover the core services, but you may need to supplement them with cluster-specific networking (ingress, load balancers) depending on your setup.

Apply manifests in order:

```bash
kubectl apply -f deploy/neo4j.yaml
kubectl apply -f deploy/ollama-daemonset.yaml
helm install graphrag-flink deploy/flink-values.yaml
kubectl apply -f deploy/api.yaml
```

