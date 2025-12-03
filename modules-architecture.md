# Modules & Interaction Architecture

> **Project:** CS 441 – GraphRAG  
> **Part 1:** Streaming Graph Construction (Flink → Neo4j)  
> **Part 2:** Graph Query & API Layer (Akka HTTP → Neo4j)

This document describes **how the modules interact with each other**, the **direction of dependencies**, and how **Part 2 (API)** sits on top of the ingestion/graph layer.

---

## 1. Module Overview

Logical modules:

- `core` – Shared domain model, configuration, logging, and utilities.
- `llm` – Ollama / LLM integration and JSON parsing.
- `ingestion` – Flink DataStream job that builds the concept graph in Neo4j.
- `neo4j-write` – Flink sink for idempotent upserts into Neo4j.
- `api` – Akka HTTP microservice layer for querying the graph (Part 2).
- `neo4j-read` – Read-only client used by the API to query Neo4j.
- `deploy` – Kubernetes / infra configuration (Flink, Neo4j, Ollama, API).

Some of these are separate SBT modules in practice; others are “logical” submodules (e.g. `neo4j-write` and `neo4j-read`).

---

## 2. High-Level Dependency Structure

Conceptually, dependencies flow **downwards**:

```text
             ┌───────────────────────────┐
             │        deploy/infra       │
             │  (Flink, Neo4j, Ollama,   │
             │       API on EKS)         │
             └────────────┬──────────────┘
                          │
                ┌─────────▼─────────┐
                │       api         │   (Part 2)
                │   (ApiServer,     │
                │   JsonProtocol)   │
                └─────────┬─────────┘
                          │
                   ┌──────▼───────┐
                   │ neo4j-read   │
                   │ (Neo4j       │
                   │  read client)│
                   └──────┬───────┘
                          │
          ┌───────────────▼────────────────┐
          │         neo4j-write            │
          │  (Flink sink, Cypher MERGE)    │
          └───────────────┬────────────────┘
                          │
                ┌─────────▼─────────┐
                │     ingestion     │   (Part 1)
                │  (Flink pipeline) │
                └─────────┬─────────┘
                          │
                  ┌───────▼───────┐
                  │      llm      │
                  │ (Ollama,      │
                  │  JSON)        │
                  └───────┬───────┘
                          │
                      ┌───▼───┐
                      │ core  │
                      └───────┘
