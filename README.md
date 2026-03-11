# Graph-Based Retrieval-Augmented Generation (GraphRAG)

Unified documentation for a GraphRAG system by Simbarashe Chinomona. This guide combines architecture, prerequisites, and end-to-end workflows for both ingestion and API layers.

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
- `neo4j/`: Evidence queries, neighborhood exploration, semantic search helpers, Flink sink with idempotent Neo4j MERGE logic
- `api/`: Akka HTTP API (query, jobs, evidence, explain)

### Walkthrough link
```text
https://youtu.be/Jv6bz1AcyqI
```

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

## Please Note that the input files and directory needed for this project could not be added to github please download from link and add it to project directory 
```text
https://drive.google.com/drive/folders/17X4YWFc2dLxwdcIrf1v2jpB6Waon3vj7?usp=drive_link
```

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

To run the ingestion pipeline locally with Flink, follow these steps.

### 1) Install / Download Flink

1. Go to the Apache Flink download page and download a **binary release** compatible with Scala 2.12 (e.g., Flink 1.20.x, Scala 2.12 build).
2. Extract it somewhere on your machine, for example:

   - On Linux / WSL: `/home/<user>/flink-1.20.0`
   - On Windows: `C:\tools\flink-1.20.0`

3. Optionally set `FLINK_HOME` and add it to your `PATH`:

   ```bash
   export FLINK_HOME=/home/<user>/flink-1.20.0
   export PATH="$FLINK_HOME/bin:$PATH"
### 2) — Install / Download Docker

1. If you do not already have Docker installed:

- **Windows / Mac:**  
  https://www.docker.com/products/docker-desktop/

- **Linux:**  
  ```bash
  sudo apt-get update
  sudo apt-get install docker.io -y
### 3) — Run Docker

1. Run this docker which hold neo4j locally:
   ```bash
   docker run -d \
    --name neo4j \
    -p 7474:7474 -p 7687:7687 \
    -e NEO4J_AUTH=neo4j/password \
    -e NEO4J_PLUGINS='["apoc"]' \
    -e NEO4J_dbms_security_procedures_unrestricted="apoc.*" \
    -e NEO4J_dbms_security_procedures_allowlist="apoc.*" \
    -v $HOME/neo4j/data:/data \
    -v $HOME/neo4j/plugins:/plugins \
    neo4j:5
   ```
 2. Verify its running
    Open Neo4j Browser:
      ```bash
        http://localhost:7474
      ```
    Login using:
      - Username: neo4j
      - Password: password (from the NEO4J_AUTH variable)
   
### 4) — Run Flink
  1. Change into the Flink directory:
     ```bash
     cd ~/flink-1.20.0
     ```
  2. Configure Flink File
     ```bash
      1) nano ~/flink-1.20.0/conf/flink-conf.yaml
      2) Configuration File Replacement
         # Where JobManager runs
          jobmanager.rpc.address: localhost
          
          # REST endpoint config
          rest.address: localhost
          rest.port: 8081
          rest.bind-address: 0.0.0.0
          
          # JobManager memory
          jobmanager.memory.process.size: 1024m   # 1 GB is fine locally
          
          # TaskManager memory – INCREASED
          taskmanager.memory.process.size: 4096m  # 4 GB; lower to 2048m if your machine complains
          
          taskmanager.numberOfTaskSlots: 4
          
          # JVM options – add module opens so Kryo/Chill can use reflection
          env.java.opts: --add-opens=java.base/java.util=ALL-UNNAMED --add-opens=java.base/java.lang=ALL-UNNAMED
     ```
       
  3. Restart the cluster to ensure a clean state:
     ```bash
        bin/stop-cluster.sh || true
        bin/start-cluster.sh
      ```
  4. Run assembled Jar file in the cluster. Run the Flink job locally to construct the Neo4j concept graph ( You must be in the flink directory in your computer)
     ```bash
         sbt "project ingestion" clean assembly
        ./bin/flink run -c ingestion.IngestionModule   /mnt/c/Users/Simba/IdeaProjects/CS441_HW3/modules/ingestion/target/scala-2.12/graphrag-ingestion-assembly-0.1.0-SNAPSHOT.jar
      ```

## API Service
Start the REST API (Part 2):

```bash
sbt -java-home <java version directory path in computer (deferably java 21)> "api/runMain api.ApiServer"
```

The server listens at `http://localhost:8080/v1/` with key endpoints:

- `GET /v1/evidence/{chunkId}` – Evidence lookup
- `GET /v1/graph/concept/{conceptId}/neighbors` – Concept neighborhood
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
sbt "project ingestion" clean assembly
```

2) Launch the Flink job:

```bash
    cd ~/flink-1.20.0
   ./bin/flink run -c ingestion.IngestionModule   /mnt/c/Users/Simba/IdeaProjects/CS441_HW3/modules/ingestion/target/scala-2.12/graphrag-ingestion-assembly-0.1.0-SNAPSHOT.jar
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

