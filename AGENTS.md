# PatternForge — Agent Guide

PatternForge is a **runtime knowledge system** for AI coding agents. Instead of loading 30K tokens
of rules, agents query PatternForge and receive only the 5–7 patterns relevant to their current
task, plus a step-by-step workflow. This keeps agents focused and dramatically reduces token usage.

This file is the single source of truth for any agent working on the PatternForge codebase
(Claude Code, OpenCode, Aider, Cursor, Gemini CLI, etc.).

---

## Before Every Task: Query PatternForge

```bash
curl -s -X POST http://localhost:15550/api/patterns/query \
  -H "Content-Type: application/json" \
  -d '{
    "task": "<describe your task here>",
    "language": "java",
    "projectPath": "/Users/nishantdesai/Work/Side-Projects/pattern-forge"
  }' | jq '{patterns: [.patterns[].title], workflow_source: .workflow.source, steps: [.workflow.steps[].action]}'
```

- **Apply every pattern returned**: read each pattern's `description` and `codeExamples`, then follow them when writing or editing code for this task.
- Follow `workflow.steps` in order. Do not skip steps.
- Block on any `qualityGates` with `blocking: true` before proceeding.
- **If PatternForge is unreachable**: skip the query and follow the Coding Standards section in this file directly.

---

## Start PatternForge

```bash
# Prerequisites: PostgreSQL running on localhost:5432, database 'patternforge'
export ANTHROPIC_API_KEY=sk-ant-...
mvn spring-boot:run

# Or run the JAR directly
mvn clean package -DskipTests
java -jar target/pattern-forge-1.0.0-SNAPSHOT.jar
```

- REST API: **port 15550**
- MCP server: **port 8765**

Verify it's up:
```bash
curl http://localhost:15550/actuator/health
# → {"status":"UP"}

curl http://localhost:15550/api/patterns | jq 'length'
# → 62 (or current count)
```

---

## API Reference

### REST API — port 15550

| Method | Endpoint | Description |
|--------|----------|-------------|
| GET | `/actuator/health` | Health check |
| GET | `/api/patterns` | List all patterns |
| POST | `/api/patterns/query` | Query patterns + workflow for a task |
| POST | `/api/patterns/extract` | LLM-extract patterns from a file and upsert |

### MCP Server — port 8765

| Method | Endpoint | Description |
|--------|----------|-------------|
| POST | `/mcp/query` | Same as `/api/patterns/query` (OpenCode integration) |

### Query request body

```json
{
  "task": "Fix NullPointerException in getUserById",
  "language": "java",
  "projectPath": "/full/path/to/your/project",
  "topK": 5,
  "conversationId": "optional-session-id"
}
```

| Field | Required | Default | Notes |
|-------|----------|---------|-------|
| `task` | yes | — | Plain-English description of what you are doing. Be specific — the more precise, the better the pattern match. |
| `language` | yes | — | `java`, `python`, `typescript`, `go`, etc. |
| `projectPath` | no | — | Absolute path to the project being worked on. Include this so PatternForge can find project-specific workflow files. |
| `topK` | no | 5 | Number of patterns to return. Use 3 for simple tasks, 5 for normal, 7–10 for complex ones. |
| `conversationId` | no | — | Opaque string (e.g. a UUID or session ID). Pass the same ID across multiple queries in one session so PatternForge can link them for future analytics. Omit if you don't need session tracking. |

### Query response shape

```json
{
  "patterns": [
    {
      "patternId": "uuid",
      "patternName": "java-null-check-objects-nonnull",
      "title": "Java Null Check Pattern",
      "description": "Use Objects.nonNull() for null checks",
      "category": "null_safety",
      "whenToUse": "When checking if a value is not null",
      "codeExamples": { "java": "if (Objects.nonNull(value)) { }" },
      "isGlobalStandard": true
    }
  ],
  "workflow": {
    "source": "project:/path/.opencode/workflows/fix-test-imports.md",
    "userDefined": true,
    "steps": [
      { "step": 1, "action": "Read the test file", "tool": "read", "validation": "File exists" }
    ],
    "qualityGates": [
      { "gateName": "code_review", "isBlocking": true, "description": "Code must be reviewed" }
    ]
  },
  "metadata": {
    "patterns_retrieved": 5,
    "task_type": "fix_test",
    "search_strategy": "keyword"
  }
}
```

---

## Seeding Patterns from a Rules File (LLM Extraction)

PatternForge can read any rules file (AGENTS.md, CLAUDE.md, MULTI_AGENT_WORKFLOW.md, etc.)
and use the Anthropic API to extract structured patterns, then upsert them into the database.

```bash
curl -s -X POST http://localhost:15550/api/patterns/extract \
  -H "Content-Type: application/json" \
  -d '{
    "filePath": "/path/to/your/CLAUDE.md",
    "projectPath": "/path/to/your/project"
  }' | jq '{status, patterns_extracted}'
```

Requires `ANTHROPIC_API_KEY` in the environment where PatternForge is running.

---

## Auto-Recording Patterns (Git Hook)

Patterns are automatically extracted after every `git commit`, regardless of which agent
(Claude Code, OpenCode, Aider, Cursor, etc.) made the change.

### Install the hook in any project

```bash
# From inside any git repo — replace the path with wherever PatternForge lives on your machine
PATTERNFORGE_DIR="/path/to/pattern-forge"

# Install in the current git repo only
bash "$PATTERNFORGE_DIR/scripts/install-hooks.sh"

# Install globally — every new git repo you create gets it automatically
bash "$PATTERNFORGE_DIR/scripts/install-hooks.sh" --global
```

The hook (`scripts/hooks/post-commit`) watches for changes to `.java`, `.kt`, `.py`, `.ts`,
`.go`, `.md`, `CLAUDE.md`, `AGENTS.md`, `MULTI_AGENT_WORKFLOW.md`, `.cursorrules`, etc.
It calls `/api/patterns/extract` in the background and never blocks commits.

Override the server URL:
```bash
export PATTERNFORGE_URL=http://my-server:15550
```

---

## Workflow Override System

PatternForge resolves workflows in priority order. First match wins:

1. **Project directories** (checked in order):
   - `{projectPath}/.opencode/workflows/{taskType}.md`
   - `{projectPath}/.claude/workflows/{taskType}.md`
   - `{projectPath}/.agent/workflows/{taskType}.md`

2. **Global directories** (checked in order):
   - `~/.config/opencode/workflows/{taskType}.md`
   - `~/.claude/workflows/{taskType}.md`

3. **PatternForge generated** — fallback, built from patterns.

### Known task types

The `TaskAnalyzer` classifies your `task` string into one of these types.
The resolved workflow file must be named `{taskType}.md`.

| Task type | Triggers on keywords |
|-----------|----------------------|
| `fix_test` | fix test, failing test, compilation error, import |
| `add_endpoint` | add endpoint, new route, REST, controller, API |
| `refactor` | refactor, rename, restructure, extract |
| `implement_feature` | add feature, implement, build, create |
| `fix_bug` | fix bug, bug, exception, error, NPE, NullPointer |
| `add_service` | service, business logic, use case |
| `database_change` | migration, schema, table, column |
| `generic` | fallback when no other type matches |

### Workflow file format

```markdown
---
workflow_name: fix-test-imports
task_types: [fix_test, test_compilation_error]
languages: [java]
priority: 100
---

## Step 1: Read Test File
**Action**: Read the failing test file
**Tool**: read
**Validation**: File exists and is readable

## Step 2: Add Missing Imports
**Action**: Insert imports in correct alphabetical order
**Tool**: edit
**Pattern**: test-import-order

## Quality Gates
- code_review: MANDATORY, blocking=true
- user_approval: MANDATORY, blocking=true
```

Place workflow files in any of the directories above. PatternForge reloads on every query.

### Workflow source values in the response

| `workflow.source` | Meaning |
|-------------------|---------|
| `project:/path/.opencode/workflows/fix-test.md` | Used a project-level file |
| `global:/home/user/.claude/workflows/fix-test.md` | Used a global file |
| `patternforge:generated` | No file found; workflow was generated from patterns |

---

## Project Coding Standards

These are embedded here so any agent has them at hand. PatternForge also stores them
as queryable patterns in the database.

### Null checks
Use `Objects.nonNull(x)` / `Objects.isNull(x)`. Never write `x != null` or `x == null`.

### Constructor injection
Use `@RequiredArgsConstructor` (Lombok) on every Spring component. Never use `@Autowired` on fields.

### Configuration properties
Use `@Component` + `@ConfigurationProperties(prefix = "...")` + `@Data` for structured config blocks.
Use `@Value` only for simple scalar properties that don't warrant a dedicated class.

### Logging
Use `@Slf4j` (Lombok). Log at `DEBUG` for normal flow, `INFO` for significant state changes,
`WARN` for recoverable problems, `ERROR` only for unexpected failures.

### Optional over null returns
Repository and service methods that may return no result must return `Optional<T>`, not null.

### Exception handling in REST controllers
- `IllegalArgumentException` → HTTP 400
- Domain-specific exceptions (e.g., `AnthropicApiException`) → HTTP 500 with a clear message
- Generic `Exception` → HTTP 500 with "Internal server error: ..."

### No raw SQL except vector operations
Use jOOQ DSL for all queries. Raw `dsl.execute(...)` is acceptable only for pgvector operations
(e.g., `UPDATE patterns SET embedding = ?::vector WHERE pattern_id = ?`).

---

## Architecture

```
Agent (any CLI tool)
  │
  ├─ REST: POST http://localhost:15550/api/patterns/query
  └─ MCP:  POST http://localhost:8765/mcp/query
               │
               ▼
         PatternController
               │
         TaskAnalyzer          ← classifies task type (fix_test, add_endpoint, etc.)
               │
         PatternRetriever      ← hybrid: pgvector semantic search → ILIKE keyword fallback
               │
         WorkflowResolver      ← checks project dirs → global dirs → generates
               │
         Response: {patterns[], workflow{steps[], qualityGates[]}, metadata}
```

### Key source files

| File | Purpose |
|------|---------|
| `src/main/java/com/patternforge/api/rest/PatternController.java` | REST endpoints |
| `src/main/java/com/patternforge/retrieval/TaskAnalyzer.java` | Task classification |
| `src/main/java/com/patternforge/retrieval/PatternRetriever.java` | Hybrid search |
| `src/main/java/com/patternforge/workflow/WorkflowResolver.java` | 3-level hierarchy |
| `src/main/java/com/patternforge/workflow/WorkflowFileParser.java` | Parse .md workflows |
| `src/main/java/com/patternforge/workflow/WorkflowBuilder.java` | Generate workflows |
| `src/main/java/com/patternforge/mcp/PatternForgeMcpServer.java` | MCP server |
| `src/main/java/com/patternforge/llm/AnthropicClient.java` | Anthropic API client |
| `src/main/java/com/patternforge/llm/PatternExtractionService.java` | LLM extraction |
| `src/main/java/com/patternforge/config/WorkflowProperties.java` | Workflow dir config |
| `src/main/resources/application.yml` | All configuration |
| `src/main/resources/db/schema.sql` | 11-table DB schema |
| `scripts/hooks/post-commit` | Git hook template |
| `scripts/install-hooks.sh` | Hook installer |

### Database (PostgreSQL + pgvector)

Database: `patternforge` on `localhost:5432`. Extension: `pgvector` (768 dims for embeddings).

Tables: `patterns`, `workflow_steps`, `workflow_templates`, `rules`, `projects`,
`conversational_patterns`, `pattern_usage`, `pattern_promotions`, `quality_gates`,
`pattern_quality_gates`, `schema_version`.

---

## Current State (v1.1.0)

| Feature | Status |
|---------|--------|
| REST API on port 15550 | Running |
| MCP server on port 8765 | Running |
| Patterns in DB | ~62 (keyword search) |
| Semantic / vector search | Needs Ollama (falls back to keyword) |
| LLM extraction via Anthropic | Working (needs `ANTHROPIC_API_KEY`) |
| Workflow resolution (multi-dir) | Working |
| Git hook auto-recording | Available via `scripts/install-hooks.sh` |
| Pattern promotion (conversational → global) | Not yet implemented (Phase 3) |
| Success tracking analytics | Not yet implemented (Phase 3) |
| Ollama embeddings | Blocked (firewall) — keyword fallback active |

---

## Troubleshooting

**"Connection refused" on port 15550**
```bash
# Start PatternForge
mvn spring-boot:run
# or
java -jar target/pattern-forge-1.0.0-SNAPSHOT.jar
```

**"No patterns returned"**
```bash
# Check pattern count
curl http://localhost:15550/api/patterns | jq 'length'

# If 0, run migration
./scripts/migrate-opencode-patterns.sh
```

**LLM extraction fails**
```bash
# Verify ANTHROPIC_API_KEY is set in the environment PatternForge runs in
echo $ANTHROPIC_API_KEY
```

**"Ollama unavailable" warning**
Normal — keyword search fallback is fully functional. To enable semantic search,
install Ollama locally and pull `nomic-embed-text`.

**Patterns not updating after extraction**
The extract endpoint upserts immediately. Re-query to see new patterns — no restart needed.
