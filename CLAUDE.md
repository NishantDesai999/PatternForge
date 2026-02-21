# PatternForge — Claude Code Agent Guide

Read **AGENTS.md** first. It is the single source of truth for all agents.
This file contains Claude Code-specific additions only.

---

## Before Every Task

```bash
curl -s -X POST http://localhost:15550/api/patterns/query \
  -H "Content-Type: application/json" \
  -d '{
    "task": "<describe your task here>",
    "language": "java",
    "projectPath": "/Users/nishantdesai/Work/Side-Projects/pattern-forge"
  }' | jq '{patterns: [.patterns[].title], workflow_source: .workflow.source, steps: [.workflow.steps[].action]}'
```

## Claude Code Workflow Overrides

Place `.md` workflow files in `.claude/workflows/` to override PatternForge-generated
workflows for specific task types when working on this repo.

File name must match the task type (e.g., `fix_test.md`, `implement_feature.md`).
See AGENTS.md → "Workflow Override System" for the file format.

## Seed This File into PatternForge

```bash
curl -s -X POST http://localhost:15550/api/patterns/extract \
  -H "Content-Type: application/json" \
  -d '{
    "filePath": "/Users/nishantdesai/Work/Side-Projects/pattern-forge/CLAUDE.md",
    "projectPath": "/Users/nishantdesai/Work/Side-Projects/pattern-forge"
  }' | jq '{status, patterns_extracted}'
```
