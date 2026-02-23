# Multi-Agent Orchestration Workflow Pattern

**Category**: `multi_agent`  
**Type**: `global_config`  
**Priority**: `MANDATORY`  
**Applies To**: ALL implementation tasks, ALL projects, ALL languages

---

## Pattern: Multi-Agent Orchestration Workflow

### When to Use

For **ANY implementation task** involving code changes:
- Writing new files (1+ files)
- Modifying existing code
- Implementing features
- Fixing bugs
- Refactoring
- Adding tests

**This is the UNIVERSAL workflow - always use this approach.**

---

## The Workflow

### Step 1: Main Agent (Planning ONLY)

**Role**: Planning and coordination - NO implementation work

**Actions**:
1. Query PatternForge for relevant patterns
2. Create detailed task plan with subtasks
3. **Ask user for subagent model choice** (ONCE per session):
   - Available models: claude-opus-4.5, claude-sonnet-4.5, claude-haiku-4.5, gemini-3-pro-preview, kimi-k2-thinking, deepseek-v3.2, gpt-5, llama-3.3-70b, qwen-max, o1-preview
   - Recommend: claude-haiku-4.5 (fast + cheap)
4. Delegate to Work Orchestrator

**Cost Principle**: Main agent is EXPENSIVE (Protium/claude-sonnet-4.5) - minimize its use!

---

### Step 2: Work Orchestrator (Task Breakdown)

**Role**: Break task into subtasks, spawn subagents

**Strategy by file count**:
- **1-3 files**: Spawn individual subagents (one per file)
- **4-15 files**: Spawn parallel subagents (batches of 5)
- **16+ files**: Use orchestrator coordination with batching

**Subagent spawning**:
```
Spawn subagent with:
- Task description
- Relevant patterns from PatternForge
- Project standards from AGENTS.md
- Code style rules
- Build tool commands
- File context
```

---

### Step 3: Subagents (Implementation Work)

**Role**: Execute ALL implementation work

**Model**: Cheaper models (e.g., claude-haiku-4.5, deepseek-v3.2)

**Receives**:
- Detailed task description
- Relevant standards from AGENTS.md
- Patterns from PatternForge
- Code style rules
- Build commands

**Executes**:
- Writes new files
- Edits existing files
- Runs tests
- Fixes compilation errors

**Works in parallel** where tasks are independent.

---

### Step 4: Code Reviewer Agent (Quality Check)

**Role**: Review ALL subagent work

**Fetches**:
- ALL standards from knowledge base
- ALL mandatory patterns
- ALL relevant patterns for the task

**Checks**:
- Standards compliance (e.g., Objects.nonNull(), @RequiredArgsConstructor)
- Pattern usage (from PatternForge)
- Code quality
- Test coverage
- Null safety
- Exception handling

**Generates**:
- Structured review comments
- Severity levels (blocking / non-blocking)
- Specific fix recommendations

---

### Step 5: User Review Loop

**Role**: User approves or requests changes

**User sees**:
- Review comments from code reviewer
- Changes made by subagents
- Severity levels

**User actions**:
- ✅ Approve: Move to summary
- ❌ Request changes: Provide feedback → go to Step 6

---

### Step 6: Revision Loop (Fix Issues)

**Role**: Subagents fix review issues

**Process**:
1. Subagents receive review feedback
2. Apply fixes based on feedback
3. Re-submit to code reviewer
4. Repeat until approved

**Iterations**: Up to 3 revisions (configurable)

---

### Step 7: Final Summary

**Role**: Generate summary at user-chosen level

**Ask user for summary level**:
- **Low**: Brief summary (2-3 sentences)
- **Medium**: Moderate detail (1 paragraph per major change)
- **High**: Detailed explanation (all changes, rationale, patterns applied)

**Generate summary including**:
- What was implemented
- Which patterns were applied
- Which standards were followed
- Test results
- Any warnings or notes

---

## Code Example

```
User: "Add user CRUD endpoint"

↓

Main Agent (Planning):
- Query PatternForge: Get REST patterns
- Plan: (1) Controller, (2) Service, (3) Repository, (4) Tests
- Ask user: "Choose subagent model (recommend: claude-haiku-4.5)"
- Delegate to Work Orchestrator

↓

Work Orchestrator:
- Spawn 4 parallel subagents:
  - Subagent 1: UserController.java
  - Subagent 2: UserService.java
  - Subagent 3: UserRepository.java
  - Subagent 4: UserControllerTest.java

↓

Subagents (claude-haiku-4.5):
- Receive patterns: @RestController, @RequiredArgsConstructor, Objects.nonNull()
- Implement files in parallel
- Run tests

↓

Code Reviewer:
- Check: Are patterns applied?
- Check: Standards followed?
- Generate feedback: "Missing null check in UserService.findById()"

↓

User Review:
- Review comment: "Add null check"
- User: "Approved - please fix"

↓

Revision:
- Subagent 2 fixes UserService.java
- Re-review → Approved

↓

Summary:
- Ask: "Summary level? (Low/Medium/High)"
- User: "Medium"
- Generate medium-level summary
- Done!
```

---

## Rationale

### Cost Optimization
- **Main agent** (Protium/claude-sonnet-4.5): $$$$ - only for planning
- **Subagents** (claude-haiku-4.5): $ - do all implementation work
- **Savings**: 80-90% cost reduction

### Quality Assurance
- **Separate code reviewer**: Ensures patterns + standards compliance
- **Not biased**: Reviewer didn't write the code
- **Consistent**: Same standards applied to all work

### User Control
- **Approval loop**: User reviews before final submission
- **Feedback mechanism**: User can request changes
- **Summary levels**: User chooses detail level

### Parallelization
- **Faster execution**: Multiple subagents work simultaneously
- **Independent tasks**: No blocking
- **Scalable**: Works for 1 file or 100 files

---

## Important Rules

1. **Main agent NEVER implements**: Planning + coordination ONLY
2. **Always spawn subagents**: Even for 1 file (cost optimization)
3. **Ask model choice once**: At first subagent spawn, reuse for session
4. **Always invoke reviewer**: No exceptions
5. **Always ask summary level**: After task completion
6. **Context-rich prompts**: Subagents receive all relevant standards + patterns

---

## Success Criteria

✅ Main agent did NOT write any implementation code  
✅ Subagents used cheaper models  
✅ Code reviewer ran and provided feedback  
✅ User reviewed and approved/requested changes  
✅ Summary level was asked and provided  
✅ All patterns from PatternForge were applied  
✅ All standards from AGENTS.md were followed  

---

**This pattern is GLOBAL CONFIG - applies to EVERY project automatically via PatternForge.**
