# Custom Workflows for Collateral Module

This directory contains project-specific workflow overrides for PatternForge.

## How It Works

When PatternForge receives a query, it checks for workflows in this order:
1. **Project workflows** (this directory) - HIGHEST PRIORITY
2. Global workflows (~/.config/opencode/workflows/)
3. PatternForge-generated workflows - FALLBACK

## Creating a Workflow

Create `{task-type}.md` file:

```yaml
---
workflow_name: my-workflow
task_types: [fix_test, add_endpoint, etc.]
languages: [java]
priority: 100
---

## Step 1: Action
**Action**: What to do
**Tool**: read/edit/bash/invoke_agent
```

## Available Task Types

- `fix_test` - Fix failing tests
- `add_endpoint` - Add REST API endpoint
- `refactor` - Code refactoring
- `fix_bug` - Bug fixes
- `add_feature` - New feature implementation

Create workflows for common tasks you do repeatedly!

## Example Workflows in This Directory

### fix-test-imports.md
Handles test compilation errors related to missing imports. Automatically:
- Identifies missing test utility imports
- Adds imports in correct order per checkstyle rules
- Runs Maven clean (required for Lombok/jOOQ)
- Invokes code review before applying changes

## Workflow Structure

Each workflow file contains:
- **Frontmatter**: Metadata (YAML)
  - `workflow_name`: Unique identifier
  - `task_types`: When to trigger this workflow
  - `languages`: Language constraints
  - `frameworks`: Framework constraints (optional)
  - `priority`: Higher = preferred (default: 50)

- **Steps**: Sequential actions
  - `Action`: Description
  - `Tool`: Which tool to use (read/edit/bash/invoke_agent)
  - `Validation`: Success criteria
  - `Rationale`: Why this step matters (optional)

- **Quality Gates**: Enforcement rules
  - `MANDATORY, blocking=true`: Must pass, blocks execution
  - `MANDATORY, blocking=false`: Must run, can continue if fails
  - `RECOMMENDED`: Should run, optional

- **Pattern References**: Links to PatternForge patterns used

## Adding Your Own Workflows

1. Identify repetitive tasks you do in this project
2. Create `{task-name}.md` in this directory
3. Define steps using the structure above
4. Set priority > 100 to override global workflows

Example task types to consider:
- `fix-checkstyle-errors`
- `add-rest-controller`
- `fix-lombok-issues`
- `update-jooq-queries`
- `add-integration-test`

PatternForge will automatically detect and use your workflows!
