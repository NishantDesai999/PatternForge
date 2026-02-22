# PatternForge

> A context-aware RAG (Retrieval-Augmented Generation) system that gives AI coding agents just the right coding patterns for every task — without blowing up the context window.

---

## The Problem PatternForge Solves

When you give an AI coding agent a giant `AGENTS.md` or `CLAUDE.md` with all your project rules, it still forgets them. Loading 30,000 tokens of coding standards into every conversation is expensive, slow, and still produces inconsistent results.

**PatternForge flips the model:** instead of dumping all rules upfront, an agent asks PatternForge _"I'm about to fix a NullPointerException in a Java service — what patterns should I follow?"_ and receives only the 5–7 most relevant patterns plus a step-by-step workflow tailored to that exact task.

**Results:**
- 80%+ reduction in context-window token usage
- 90%+ standards compliance (agents follow the patterns they receive)
- Self-improving: every task records a success/failure outcome, so pattern rankings improve over time

---

## How It Works — Big Picture

```
Your AI Agent (Claude Code, Cursor, Windsurf, etc.)
        │
        │  "Fix NullPointerException in getUserById"
        ▼
  ┌─────────────────────────────────┐
  │         PatternForge            │
  │                                 │
  │  1. Analyze task type           │
  │     (fix_bug, add_endpoint…)    │
  │                                 │
  │  2. Search pattern database     │
  │     (semantic + keyword)        │
  │                                 │
  │  3. Resolve workflow            │
  │     (project → global →        │
  │      auto-generated)            │
  │                                 │
  │  4. Return 5–7 patterns +       │
  │     step-by-step workflow       │
  └─────────────────────────────────┘
        │
        │  { patterns: [...], workflow: { steps: [...] } }
        ▼
  Agent applies patterns, records outcome
        │
        ▼
  PatternForge updates success rates,
  promotes high-performing patterns
```

Over time, patterns that work well get promoted from conversational → project-level → global standards automatically.

---

## Architecture Overview

```
┌─────────────────────────────────────────────────────────────┐
│                      PatternForge                           │
│                                                             │
│  ┌──────────────┐    ┌──────────────┐    ┌──────────────┐  │
│  │  REST API    │    │  MCP Server  │    │  Admin API   │  │
│  │  :15550      │    │  :8765       │    │  :15550      │  │
│  └──────┬───────┘    └──────┬───────┘    └──────┬───────┘  │
│         │                   │                   │           │
│         └───────────────────┼───────────────────┘           │
│                             │                               │
│                    ┌────────▼────────┐                      │
│                    │ PatternRetriever │                      │
│                    │ + TaskAnalyzer  │                      │
│                    └────────┬────────┘                      │
│                             │                               │
│          ┌──────────────────┼──────────────────┐            │
│          │                  │                  │            │
│  ┌───────▼──────┐  ┌───────▼──────┐  ┌───────▼──────┐     │
│  │ VectorSearch │  │ KeywordSearch│  │  Workflow    │     │
│  │ (pgvector)   │  │  (fallback)  │  │  Resolver   │     │
│  └───────┬──────┘  └──────────────┘  └──────────────┘     │
│          │                                                  │
│  ┌───────▼──────────────────────────────────────────────┐  │
│  │              PostgreSQL + pgvector                   │  │
│  │  patterns │ workflow_steps │ pattern_usage │ projects │  │
│  └──────────────────────────────────────────────────────┘  │
└─────────────────────────────────────────────────────────────┘
         │                              │
  ┌──────▼──────┐               ┌───────▼───────┐
  │   Ollama    │               │  Anthropic    │
  │ (embeddings)│               │  Claude API   │
  │  :11434     │               │ (extraction)  │
  └─────────────┘               └───────────────┘
```

---

## Technology Stack

| Layer | Technology |
|-------|-----------|
| Language | Java 23 |
| Framework | Spring Boot 3.4.1 |
| Build | Maven |
| Database | PostgreSQL 14+ with pgvector extension |
| SQL DSL | jOOQ 3.19.15 (type-safe, no raw SQL) |
| Embeddings | Ollama (`nomic-embed-text`, 768 dims) |
| LLM | Anthropic Claude (pattern extraction) |
| Protocol | Model Context Protocol (MCP, JSON-RPC 2.0) |
| Testing | JUnit 5 + Mockito + Testcontainers |

---

## Prerequisites

| Requirement | Notes |
|-------------|-------|
| Java 23 | `java -version` should show 23+ |
| Maven 3.9+ | `mvn -version` |
| Docker | For running PostgreSQL |
| Ollama (optional) | For semantic search. Keyword search works without it |
| Anthropic API key (optional) | Only needed for pattern extraction from files |

---

## Local Setup — Step by Step

### Step 1: Start PostgreSQL with pgvector

PatternForge needs PostgreSQL 14+ with the `pgvector` extension. The easiest way is Docker:

```bash
docker run -d \
  --name postgres17 \
  -e POSTGRES_PASSWORD=postgres \
  -p 5432:5432 \
  pgvector/pgvector:pg17
```

Verify it's running:
```bash
docker ps | grep postgres17
```

### Step 2: Create the Database Schema

```bash
cd /path/to/PatternForge
bash scripts/setup-database.sh
```

This script creates the `patternforge` database and applies the full schema (11 tables, indexes, views, and functions).

### Step 3: (Optional) Set Up Ollama for Semantic Search

Without Ollama, PatternForge falls back to keyword search — it still works fine for most cases.

```bash
# Install Ollama: https://ollama.com/download
bash scripts/setup-ollama.sh

# Or manually:
ollama pull nomic-embed-text
ollama serve   # Starts on port 11434
```

### Step 4: Set Environment Variables

```bash
# Required only if you want to extract patterns from files using Claude
export ANTHROPIC_API_KEY=sk-ant-...
```

### Step 5: Build and Run

```bash
# Generate jOOQ classes and build
mvn clean install -DskipTests

# Start the server
mvn spring-boot:run
```

Or build a JAR and run it:
```bash
mvn clean package -DskipTests
java -jar target/pattern-forge-1.0.0-SNAPSHOT.jar
```

### Step 6: Verify It's Running

```bash
# Health check
curl http://localhost:15550/actuator/health
# → {"status":"UP"}

# List patterns (should return an array)
curl http://localhost:15550/api/patterns | jq 'length'

# MCP server
curl http://localhost:8765/mcp
```

---

## Running with Docker Compose

If you prefer to run PatternForge itself in Docker (PostgreSQL must still be on your host or in another container):

```bash
# Start PatternForge app container
docker-compose up -d

# View logs
docker-compose logs -f
```

The `docker-compose.yml` expects PostgreSQL on `localhost:5432` and Ollama on `localhost:11434` on your host machine.

---

## REST API Reference

Base URL: `http://localhost:15550`

### Query patterns for a task

This is the main endpoint — agents call this before starting any task.

```bash
curl -s -X POST http://localhost:15550/api/patterns/query \
  -H "Content-Type: application/json" \
  -d '{
    "task": "Fix NullPointerException in getUserById",
    "language": "java",
    "projectPath": "/path/to/your/project",
    "topK": 5,
    "conversationId": "optional-session-uuid"
  }' | jq .
```

**Response:**
```json
{
  "patterns": [
    {
      "patternId": "uuid",
      "patternName": "java-null-check-objects-nonnull",
      "title": "Java Null Check Pattern",
      "description": "Use Objects.nonNull() for null checks",
      "category": "null_safety",
      "languages": ["java"],
      "whenToUse": "When checking if a value is not null",
      "codeExamples": { "java": "if (Objects.nonNull(value)) { ... }" },
      "successRate": 0.95,
      "usageCount": 127,
      "isGlobalStandard": true
    }
  ],
  "workflow": {
    "source": "project:/path/.claude/workflows/fix_bug.md",
    "userDefined": true,
    "steps": [
      { "step": 1, "action": "Identify where the null originates", "tool": "read" },
      { "step": 2, "action": "Apply null-check pattern", "tool": "edit" },
      { "step": 3, "action": "Run tests", "tool": "bash" }
    ],
    "qualityGates": [
      { "gateName": "test_check", "isBlocking": true }
    ]
  },
  "metadata": {
    "patterns_retrieved": 5,
    "task_type": "fix_bug",
    "search_strategy": "semantic"
  }
}
```

### All REST endpoints

| Method | Endpoint | Description |
|--------|----------|-------------|
| GET | `/actuator/health` | Health check |
| GET | `/api/patterns` | List all stored patterns |
| GET | `/api/standards` | Dynamic standards doc (replaces copy-pasting AGENTS.md) |
| POST | `/api/patterns/query` | **Main endpoint**: get relevant patterns + workflow |
| POST | `/api/patterns/extract` | Extract patterns from a file using Claude LLM |
| POST | `/api/patterns/capture` | Capture a pattern from the current agent session |
| POST | `/api/patterns/usage` | Record whether a pattern was used successfully |
| POST | `/api/patterns/admin/generate-embeddings` | Batch-generate embeddings for all patterns |
| POST | `/api/patterns/admin/promote-patterns` | Run promotion checks (conversational → project → global) |

---

## MCP Server Reference

The MCP server on port 8765 implements [Model Context Protocol](https://modelcontextprotocol.io) (JSON-RPC 2.0). Any MCP-compatible agent can use it directly.

| Tool | Description |
|------|-------------|
| `query_patterns` | Get relevant patterns + workflow for a task |
| `capture_pattern` | Save a new pattern learned during a session |
| `get_standards` | Get all standards as formatted markdown |
| `record_usage` | Record success or failure for a pattern |

### Connect Claude Code to PatternForge via MCP

Add this to your `~/.claude.json` (or workspace settings):

```json
{
  "mcpServers": {
    "patternforge": {
      "type": "http",
      "url": "http://localhost:8765/mcp"
    }
  }
}
```

---

## Workflow Override System

PatternForge resolves workflows using a priority hierarchy:

```
1. {projectPath}/.opencode/workflows/{taskType}.md   ← highest priority
2. {projectPath}/.claude/workflows/{taskType}.md
3. {projectPath}/.cursor/workflows/{taskType}.md
4. {projectPath}/.windsurf/workflows/{taskType}.md
5. ~/.patternforge/workflows/{taskType}.md           ← global defaults
6. Auto-generated from retrieved patterns            ← fallback
```

Task types: `fix_bug`, `fix_test`, `add_endpoint`, `add_feature`, `refactor`, `general`

### Creating a custom workflow

Create a file like `.claude/workflows/fix_bug.md`:

```markdown
---
name: Fix Bug
task_type: fix_bug
languages: [java]
---

## Steps

1. Read the failing test or error trace
2. Identify the root cause
3. Apply the relevant pattern from PatternForge
4. Write a regression test
5. Verify with `mvn test`

## Quality Gates

- test_check: blocking
- code_review: non-blocking
```

---

## Pattern Lifecycle

Patterns evolve automatically through three levels:

```
Conversational Pattern           ← captured during a session
      │
      │  (3+ reinforcements)
      ▼
  Project Standard               ← applies to this project
      │
      │  (used in 3+ projects)
      ▼
  Global Standard                ← applies everywhere
```

**Recording usage** is how patterns improve:

```bash
curl -X POST http://localhost:15550/api/patterns/usage \
  -H "Content-Type: application/json" \
  -d '{
    "patternId": "uuid-from-query-response",
    "projectPath": "/path/to/project",
    "taskType": "fix_bug",
    "success": true,
    "qualityScore": 0.9
  }'
```

---

## Git Hook Integration

PatternForge can auto-extract patterns every time you commit:

```bash
# Install hooks for the current repo
bash scripts/install-hooks.sh

# Install globally (all repos)
bash scripts/install-hooks.sh --global

# Install Claude Code PostToolUse hook
bash scripts/install-hooks.sh --claude-code
```

After installing, every `git commit` will call the `/api/patterns/extract` endpoint on the changed files and update the knowledge base automatically.

---

## Seeding Patterns from Existing Files

If you have an existing `CLAUDE.md`, `AGENTS.md`, or any standards document, you can extract patterns from it:

```bash
curl -s -X POST http://localhost:15550/api/patterns/extract \
  -H "Content-Type: application/json" \
  -d '{
    "filePath": "/path/to/AGENTS.md",
    "projectPath": "/path/to/your/project"
  }' | jq '{status, patterns_extracted}'
```

This uses Claude to read the file, identify coding patterns, and upsert them into the database.

---

## Project Structure

```
PatternForge/
├── src/main/java/com/patternforge/
│   ├── PatternForgeApplication.java    # Spring Boot entry point
│   ├── api/
│   │   ├── dto/                        # Request/response data classes (14 DTOs)
│   │   └── rest/                       # REST controllers
│   │       ├── PatternController.java  # Main API
│   │       ├── PatternCaptureController.java
│   │       └── StandardsController.java
│   ├── retrieval/
│   │   ├── TaskAnalyzer.java           # Infers task type from description
│   │   └── PatternRetriever.java       # Hybrid search (semantic + keyword)
│   ├── workflow/
│   │   ├── WorkflowResolver.java       # 3-level hierarchy resolution
│   │   ├── WorkflowFileParser.java     # Parses .md workflow files
│   │   └── WorkflowBuilder.java        # Generates workflow from patterns
│   ├── storage/
│   │   ├── VectorSearchService.java    # pgvector semantic search
│   │   ├── KeywordSearchService.java   # ILIKE fallback search
│   │   └── repository/
│   │       ├── PatternRepository.java  # Pattern CRUD via jOOQ
│   │       └── ConversationalPatternRepository.java
│   ├── extraction/
│   │   └── EmbeddingService.java       # Calls Ollama for 768-dim embeddings
│   ├── llm/
│   │   ├── AnthropicClient.java        # Anthropic API wrapper
│   │   └── PatternExtractionService.java # LLM-based pattern extraction
│   ├── promotion/
│   │   └── PatternPromotionService.java # Auto-promotes patterns by usage
│   ├── usage/
│   │   └── PatternUsageService.java    # Records outcomes, updates success rate
│   ├── mcp/
│   │   └── PatternForgeMcpServer.java  # MCP JSON-RPC 2.0 on port 8765
│   └── config/                         # Spring configuration
├── src/main/resources/
│   ├── application.yml                 # All config (ports, DB, Ollama)
│   └── db/schema.sql                   # Full database schema
├── src/test/java/                      # 67 test files (100% coverage enforced)
├── scripts/
│   ├── setup-database.sh               # Initialize PostgreSQL
│   ├── setup-ollama.sh                 # Install Ollama
│   ├── install-hooks.sh                # Git/Claude Code hooks
│   └── migrate-opencode-patterns.sh    # Migrate from OpenCode DB
├── .claude/workflows/                  # Claude Code workflow overrides
├── .opencode/workflows/                # OpenCode workflow overrides
├── requirements/                       # Architecture & design docs
├── Dockerfile                          # Alpine JRE 23 image
├── docker-compose.yml                  # Production compose
├── docker-compose-dev.yml              # Dev compose
├── AGENTS.md                           # Master guide for all AI agents
└── CLAUDE.md                           # Claude Code-specific additions
```

---

## Configuration Reference

All configuration lives in `src/main/resources/application.yml`.

```yaml
server:
  port: 15550                          # REST API port

spring:
  datasource:
    url: jdbc:postgresql://localhost:5432/patternforge
    username: postgres
    password: postgres

patternforge:
  ollama:
    url: http://localhost:11434        # Ollama embedding service
    model: nomic-embed-text            # 768-dim embedding model
    enabled: true                      # Set false to force keyword-only search
    timeout: 30000

  extraction:
    llm-model: claude-sonnet-4-6       # Claude model for pattern extraction
    temperature: 0.1
    max-tokens: 4000

  workflow:
    user-workflows-enabled: true
    reload-on-query: true              # Always reload workflow files (no restart needed)
    project-workflow-dirs:
      - .opencode/workflows
      - .claude/workflows
      - .cursor/workflows
      - .windsurf/workflows
```

You can override any value with environment variables using Spring Boot's convention:

```bash
# Override database URL
export SPRING_DATASOURCE_URL=jdbc:postgresql://myhost:5432/patternforge

# Override Ollama URL
export PATTERNFORGE_OLLAMA_URL=http://myollama:11434

# Override Anthropic key
export ANTHROPIC_API_KEY=sk-ant-...
```

---

## Database Schema Overview

11 tables in PostgreSQL:

| Table | Purpose |
|-------|---------|
| `patterns` | Core pattern storage with 768-dim embeddings |
| `workflow_steps` | Step definitions linked to patterns |
| `workflow_templates` | User-defined workflows loaded from `.md` files |
| `rules` | Coding rules/standards extracted from documents |
| `projects` | Project metadata and tech stack |
| `conversational_patterns` | Patterns captured during agent sessions |
| `pattern_usage` | Every usage recorded with success/failure |
| `pattern_promotions` | History of conversational → project → global promotions |
| `quality_gates` | Validation gates (tests, code review, build) |
| `pattern_quality_gates` | Junction: pattern ↔ quality gate |
| `schema_version` | Schema migration tracking |

Key database features:
- `vector(768)` column on `patterns` with IVFFlat index for fast cosine similarity search
- `calculate_pattern_similarity()` function for ranked vector queries
- `update_pattern_success_rate()` trigger auto-calculates success rate from `pattern_usage`
- `v_global_patterns` view: active global standards sorted by success rate
- `v_promotion_candidates` view: patterns ready to be promoted (3+ reinforcements)

---

## Troubleshooting

**Application won't start**
```
# Check PostgreSQL is running and accessible
docker ps | grep postgres
psql -h localhost -U postgres -d patternforge -c "SELECT version();"

# Check schema is applied
psql -h localhost -U postgres -d patternforge -c "SELECT * FROM schema_version;"
```

**Patterns not returning (or empty results)**
```bash
# Check if patterns exist
curl http://localhost:15550/api/patterns | jq 'length'

# Seed initial patterns from AGENTS.md
curl -X POST http://localhost:15550/api/patterns/extract \
  -H "Content-Type: application/json" \
  -d '{"filePath": "/path/to/PatternForge/AGENTS.md", "projectPath": "/path/to/PatternForge"}'
```

**Semantic search not working (Ollama)**
```bash
# Check Ollama is running
curl http://localhost:11434/api/tags

# If not running
ollama serve

# PatternForge automatically falls back to keyword search if Ollama is unavailable
```

**jOOQ compilation fails**
```bash
# jOOQ generates code from the live database — PostgreSQL must be running before building
docker start postgres17
mvn clean generate-sources
```

**Build fails with Lombok errors**
```bash
# Lombok edge-SNAPSHOT comes from projectlombok.org — ensure Maven can reach it
mvn clean install -U   # Force update snapshots
```

---

## Running Tests

```bash
# Run all tests (requires Docker for Testcontainers)
mvn test

# Run specific test class
mvn test -Dtest=PatternRetrieverTest

# Skip tests during build
mvn clean package -DskipTests
```

Tests use Testcontainers to spin up a real PostgreSQL instance automatically — no manual test DB setup needed.

Code coverage is enforced at 100% via JaCoCo. The build will fail if coverage drops below the threshold.

---

## Contributing

1. Fork the repo and create a feature branch
2. Query PatternForge before starting any task:
   ```bash
   curl -X POST http://localhost:15550/api/patterns/query \
     -H "Content-Type: application/json" \
     -d '{"task": "your task description", "language": "java", "projectPath": "."}'
   ```
3. Follow the workflow steps returned
4. Record your outcome after finishing:
   ```bash
   curl -X POST http://localhost:15550/api/patterns/usage \
     -H "Content-Type: application/json" \
     -d '{"patternId": "...", "projectPath": ".", "taskType": "...", "success": true}'
   ```
5. Run tests and ensure coverage stays at 100%
6. Open a PR

---

## Documentation

| File | Description |
|------|-------------|
| `AGENTS.md` | Master guide for all AI agents — read this if you're an agent |
| `CLAUDE.md` | Claude Code-specific workflow overrides |
| `requirements/patternforge-architecture.md` | Full system architecture (80KB) |
| `requirements/patternforge-addendum-multi-agent-workflow.md` | Multi-agent orchestration design |
| `requirements/patternforge-addendum-feedback-mechanisms.md` | Pattern learning & feedback design |

---

## License

See [LICENSE](LICENSE) file for details.
