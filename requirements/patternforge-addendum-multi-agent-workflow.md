# PatternForge Architecture - Addendum: Multi-Agent Workflow Orchestration

## Advanced Multi-Agent System with Code Review Loops

---

## Problem Statement

The current architecture assumes a simple linear workflow: task → generate → review → done.

**Real-world requirements:**
1. **Complex tasks need parallel work** - Multiple sub-agents working simultaneously
2. **Code review should be thorough** - Separate reviewer agent with full knowledge base access
3. **User feedback loop** - User reviews, approves/denies changes
4. **Multiple iterations** - Sub-agents respond to review comments
5. **Final orchestration** - Work orchestrator collects and summarizes
6. **Variable summary levels** - User chooses detail level (low/medium/detailed)
7. **Workflow itself is a pattern** - This process should be stored in knowledge base
8. **Universal application** - Same workflow across all projects unless overridden

---

## Architecture: Multi-Agent Orchestration System

```
┌─────────────────────────────────────────────────────────────────┐
│                    User Task Request                             │
└────────────────────────────┬────────────────────────────────────┘
                             │
                             ▼
┌─────────────────────────────────────────────────────────────────┐
│  Main Agent (Planning & Coordination)                           │
│  - Understands task                                              │
│  - Retrieves patterns (mandatory + semantic + session)          │
│  - Delegates to Work Orchestrator                               │
└────────────────────────────┬────────────────────────────────────┘
                             │
                             ▼
┌─────────────────────────────────────────────────────────────────┐
│  Work Orchestrator Agent                                         │
│  - Breaks task into subtasks                                     │
│  - Spawns parallel sub-agents                                    │
│  - Manages dependencies between subtasks                         │
│  - Collects completed work                                       │
└────────────────────────────┬────────────────────────────────────┘
                             │
                ┌────────────┴────────────┐
                │                         │
                ▼                         ▼
┌───────────────────────┐   ┌───────────────────────┐
│  Sub-Agent 1          │   │  Sub-Agent 2          │   ...
│  Task: API Layer      │   │  Task: Data Layer     │
│  Model: Haiku (cheap) │   │  Model: Haiku (cheap) │
└──────────┬────────────┘   └──────────┬────────────┘
           │                           │
           └────────────┬──────────────┘
                        │
                        ▼
┌─────────────────────────────────────────────────────────────────┐
│  Code Reviewer Agent                                             │
│  - Fetches ALL standards from knowledge base                     │
│  - Fetches ALL patterns (mandatory + relevant)                   │
│  - Reviews EACH sub-agent's work                                 │
│  - Generates structured review comments                          │
│  - Checks for: standards compliance, pattern usage, quality      │
└────────────────────────────┬────────────────────────────────────┘
                             │
                             ▼
┌─────────────────────────────────────────────────────────────────┐
│  User Review Interface                                           │
│  - Shows review comments                                         │
│  - User approves/denies each comment                            │
│  - User can add additional feedback                              │
└────────────────────────────┬────────────────────────────────────┘
                             │
            ┌────────────────┴────────────────┐
            │                                 │
            ▼ (if changes needed)             ▼ (if approved)
┌─────────────────────────┐      ┌─────────────────────────────┐
│  Sub-Agents Revision    │      │  Work Orchestrator          │
│  - Fix review comments  │      │  - Final review             │
│  - Apply user feedback  │      │  - Generate summary         │
│  - Re-submit            │      │  - Submit to user           │
└─────────┬───────────────┘      └─────────────────────────────┘
          │                                    │
          └─────────► Loop until approved ◄───┘
                             │
                             ▼
┌─────────────────────────────────────────────────────────────────┐
│  Knowledge Base Update                                           │
│  - Record workflow execution                                     │
│  - Update pattern success rates                                  │
│  - Store user feedback patterns                                  │
│  - Learn from review comments                                    │
└─────────────────────────────────────────────────────────────────┘
```

---

## Component Specifications

### Component 11: Main Agent (Task Planner)

**Purpose**: Understand task, retrieve patterns, delegate to orchestrator.

```python
# core/main_agent.py

from typing import Dict, List
from dataclasses import dataclass
import anthropic

@dataclass
class TaskPlan:
    """High-level task plan from Main Agent."""
    
    task_description: str
    task_context: 'TaskContext'
    
    # Patterns to follow
    mandatory_patterns: List['RetrievedPattern']
    relevant_patterns: List['RetrievedPattern']
    session_patterns: List['ConversationalPattern']
    
    # Orchestration directive
    requires_parallel_work: bool
    subtask_count_estimate: int
    complexity: str  # simple, medium, complex
    
    # Review requirements
    review_strictness: str  # lenient, standard, strict
    critical_concerns: List[str]  # security, performance, etc.

class MainAgent:
    """
    Main planning agent - understands task and coordinates execution.
    """
    
    def __init__(
        self,
        anthropic_api_key: str,
        pattern_retriever: 'EnhancedPatternRetriever',
        task_analyzer: 'TaskAnalyzer'
    ):
        self.client = anthropic.Anthropic(api_key=anthropic_api_key)
        self.pattern_retriever = pattern_retriever
        self.task_analyzer = task_analyzer
    
    def plan_task(
        self,
        task_description: str,
        project_context: Dict,
        user_preferences: Dict
    ) -> TaskPlan:
        """
        Understand task and create execution plan.
        """
        
        # 1. Analyze task
        task_context = self.task_analyzer.analyze_task(
            task_description,
            project_context
        )
        
        # 2. Retrieve complete pattern set
        pattern_set = self.pattern_retriever.retrieve_complete_pattern_set(
            task_context,
            project_context
        )
        
        # 3. Assess complexity and parallel work needs
        complexity_assessment = self._assess_task_complexity(
            task_description,
            task_context
        )
        
        # 4. Create plan
        plan = TaskPlan(
            task_description=task_description,
            task_context=task_context,
            mandatory_patterns=pattern_set.mandatory_patterns,
            relevant_patterns=pattern_set.retrieved_patterns,
            session_patterns=pattern_set.session_patterns,
            requires_parallel_work=complexity_assessment['parallel_work_needed'],
            subtask_count_estimate=complexity_assessment['subtask_count'],
            complexity=complexity_assessment['complexity'],
            review_strictness=user_preferences.get('review_strictness', 'standard'),
            critical_concerns=task_context.concerns
        )
        
        return plan
    
    def _assess_task_complexity(
        self,
        task_description: str,
        task_context: 'TaskContext'
    ) -> Dict:
        """
        Assess whether task needs parallel work and how many subtasks.
        """
        
        prompt = f"""Assess this software development task's complexity.

**Task**: {task_description}

**Context**:
- Type: {task_context.task_type}
- Components: {', '.join(task_context.components)}
- Concerns: {', '.join(task_context.concerns)}

**Your Task**: Determine:
1. Can this be done by a single agent or needs parallel work?
2. If parallel, how many logical subtasks?
3. What's the overall complexity?

Return ONLY JSON:
```json
{{
  "parallel_work_needed": true/false,
  "subtask_count": 1-10,
  "complexity": "simple|medium|complex",
  "rationale": "Why this assessment",
  "suggested_subtasks": ["subtask 1", "subtask 2", ...]
}}
```"""
        
        response = self.client.messages.create(
            model="claude-sonnet-4-20250514",
            max_tokens=800,
            messages=[{"role": "user", "content": prompt}]
        )
        
        response_text = response.content[0].text
        if "```json" in response_text:
            json_text = response_text.split("```json")[1].split("```")[0].strip()
        else:
            json_text = response_text.strip()
        
        import json
        return json.loads(json_text)
```

---

### Component 12: Work Orchestrator Agent

**Purpose**: Break task into subtasks, spawn parallel sub-agents, manage dependencies.

```python
# core/work_orchestrator.py

from typing import Dict, List, Optional
from dataclasses import dataclass
from enum import Enum
import asyncio
import anthropic

class SubtaskStatus(Enum):
    PENDING = "pending"
    IN_PROGRESS = "in_progress"
    COMPLETED = "completed"
    NEEDS_REVISION = "needs_revision"
    FAILED = "failed"

@dataclass
class Subtask:
    """Individual subtask for sub-agent."""
    
    subtask_id: str
    description: str
    component: str  # controller, service, repository, etc.
    
    # Dependencies
    depends_on: List[str]  # Other subtask IDs
    
    # Patterns to follow
    applicable_patterns: List['RetrievedPattern']
    
    # Status
    status: SubtaskStatus
    assigned_agent_id: Optional[str] = None
    
    # Results
    generated_code: Optional[str] = None
    file_path: Optional[str] = None
    
    # Review
    review_comments: List['ReviewComment'] = None
    revision_count: int = 0

@dataclass
class SubAgentResult:
    """Result from a sub-agent."""
    
    subtask_id: str
    success: bool
    generated_code: str
    file_path: str
    model_used: str
    iterations: int
    error: Optional[str] = None

class WorkOrchestrator:
    """
    Orchestrates parallel sub-agents and manages dependencies.
    """
    
    def __init__(
        self,
        anthropic_api_key: str,
        db_connection
    ):
        self.client = anthropic.Anthropic(api_key=anthropic_api_key)
        self.db = db_connection
    
    async def orchestrate_task(
        self,
        task_plan: TaskPlan,
        project_context: Dict
    ) -> Dict:
        """
        Orchestrate task execution with parallel sub-agents.
        """
        
        # 1. Break down into subtasks
        subtasks = await self._create_subtasks(task_plan, project_context)
        
        # 2. Execute subtasks in parallel (respecting dependencies)
        results = await self._execute_subtasks_parallel(subtasks, task_plan)
        
        return {
            'subtasks': subtasks,
            'results': results,
            'success': all(r.success for r in results)
        }
    
    async def _create_subtasks(
        self,
        task_plan: TaskPlan,
        project_context: Dict
    ) -> List[Subtask]:
        """
        Break task into subtasks with dependencies.
        """
        
        # Format patterns for prompt
        patterns_text = self._format_patterns_for_prompt(
            task_plan.mandatory_patterns,
            task_plan.relevant_patterns
        )
        
        prompt = f"""Break this task into subtasks for parallel execution.

**Task**: {task_plan.task_description}

**Components Involved**: {', '.join(task_plan.task_context.components)}

**Patterns to Follow**:
{patterns_text}

**Your Task**: Create subtasks that can be executed in parallel.

Return ONLY JSON:
```json
{{
  "subtasks": [
    {{
      "subtask_id": "task_1",
      "description": "Create repository interface",
      "component": "repository",
      "depends_on": [],
      "applicable_pattern_ids": ["PAT-00042", "PAT-00055"]
    }},
    {{
      "subtask_id": "task_2",
      "description": "Implement service layer",
      "component": "service",
      "depends_on": ["task_1"],
      "applicable_pattern_ids": ["PAT-00055", "PAT-00078"]
    }}
  ]
}}
```

**Rules**:
- Each subtask should be independently executable (after dependencies)
- Minimize dependencies to maximize parallelism
- Assign specific patterns to each subtask
"""
        
        response = self.client.messages.create(
            model="claude-sonnet-4-20250514",
            max_tokens=2000,
            messages=[{"role": "user", "content": prompt}]
        )
        
        response_text = response.content[0].text
        if "```json" in response_text:
            json_text = response_text.split("```json")[1].split("```")[0].strip()
        else:
            json_text = response_text.strip()
        
        import json
        subtask_data = json.loads(json_text)
        
        # Convert to Subtask objects
        all_patterns = {
            p.pattern_id: p 
            for p in (task_plan.mandatory_patterns + task_plan.relevant_patterns)
        }
        
        subtasks = []
        for st in subtask_data['subtasks']:
            # Get applicable patterns
            applicable_patterns = [
                all_patterns[pid] 
                for pid in st['applicable_pattern_ids']
                if pid in all_patterns
            ]
            
            subtask = Subtask(
                subtask_id=st['subtask_id'],
                description=st['description'],
                component=st['component'],
                depends_on=st['depends_on'],
                applicable_patterns=applicable_patterns,
                status=SubtaskStatus.PENDING
            )
            subtasks.append(subtask)
        
        return subtasks
    
    async def _execute_subtasks_parallel(
        self,
        subtasks: List[Subtask],
        task_plan: TaskPlan
    ) -> List[SubAgentResult]:
        """
        Execute subtasks in parallel, respecting dependencies.
        """
        
        results = []
        completed_subtask_ids = set()
        
        # Execute in waves (respecting dependencies)
        while len(completed_subtask_ids) < len(subtasks):
            # Find ready tasks (dependencies met)
            ready_tasks = [
                st for st in subtasks
                if st.status == SubtaskStatus.PENDING
                and all(dep in completed_subtask_ids for dep in st.depends_on)
            ]
            
            if not ready_tasks:
                print("⚠ No ready tasks - possible circular dependency!")
                break
            
            print(f"\n🚀 Executing {len(ready_tasks)} subtasks in parallel...")
            
            # Execute ready tasks in parallel
            tasks = [
                self._execute_subtask(st, task_plan)
                for st in ready_tasks
            ]
            
            wave_results = await asyncio.gather(*tasks)
            
            for result in wave_results:
                results.append(result)
                if result.success:
                    completed_subtask_ids.add(result.subtask_id)
                    
                    # Update subtask status
                    for st in subtasks:
                        if st.subtask_id == result.subtask_id:
                            st.status = SubtaskStatus.COMPLETED
                            st.generated_code = result.generated_code
                            st.file_path = result.file_path
                            break
        
        return results
    
    async def _execute_subtask(
        self,
        subtask: Subtask,
        task_plan: TaskPlan
    ) -> SubAgentResult:
        """
        Execute a single subtask using a sub-agent.
        """
        
        subtask.status = SubtaskStatus.IN_PROGRESS
        
        # Create focused prompt for sub-agent
        prompt = self._build_subtask_prompt(subtask, task_plan)
        
        try:
            # Use cheap model for execution (Haiku)
            response = self.client.messages.create(
                model="claude-haiku-4-20250514",
                max_tokens=3000,
                messages=[{"role": "user", "content": prompt}]
            )
            
            generated_code = response.content[0].text
            
            # Extract code from markdown if present
            if "```" in generated_code:
                parts = generated_code.split("```")
                for i, part in enumerate(parts):
                    if i % 2 == 1:
                        lines = part.strip().split('\n')
                        if lines[0].strip() in ['python', 'java', 'csharp', 'javascript']:
                            generated_code = '\n'.join(lines[1:])
                        else:
                            generated_code = part.strip()
                        break
            
            # Determine file path
            file_path = self._determine_file_path(subtask, task_plan)
            
            return SubAgentResult(
                subtask_id=subtask.subtask_id,
                success=True,
                generated_code=generated_code,
                file_path=file_path,
                model_used="haiku",
                iterations=1
            )
            
        except Exception as e:
            return SubAgentResult(
                subtask_id=subtask.subtask_id,
                success=False,
                generated_code="",
                file_path="",
                model_used="haiku",
                iterations=1,
                error=str(e)
            )
    
    def _build_subtask_prompt(
        self,
        subtask: Subtask,
        task_plan: TaskPlan
    ) -> str:
        """
        Build focused prompt for sub-agent.
        """
        
        # Include only applicable patterns for this subtask
        patterns_text = ""
        for pattern in subtask.applicable_patterns:
            patterns_text += f"""
Pattern: {pattern.title}
- {pattern.description}
- Code template:
{pattern.pattern_data.get('code_examples', {}).get(task_plan.task_context.language, 'N/A')}

"""
        
        prompt = f"""Execute this subtask following the provided patterns.

**Subtask**: {subtask.description}

**Component**: {subtask.component}

**Patterns to Follow** (MANDATORY):
{patterns_text}

**Critical Requirements**:
- Follow ALL patterns exactly
- Write production-ready code
- Include proper error handling
- Add type hints/annotations
- Include docstrings/comments

Generate ONLY the code, no explanation.
"""
        
        return prompt
    
    def _determine_file_path(
        self,
        subtask: Subtask,
        task_plan: TaskPlan
    ) -> str:
        """
        Determine appropriate file path for generated code.
        """
        
        # Simple heuristic - in production, this would be more sophisticated
        component_paths = {
            'controller': 'controllers',
            'service': 'services',
            'repository': 'repositories',
            'model': 'models',
            'dto': 'dtos',
            'middleware': 'middleware'
        }
        
        base_path = component_paths.get(subtask.component, 'generated')
        filename = subtask.subtask_id.replace('_', '-') + '.py'  # Assume Python
        
        return f"{base_path}/{filename}"
    
    def _format_patterns_for_prompt(
        self,
        mandatory: List,
        relevant: List
    ) -> str:
        """Format patterns for prompt."""
        
        text = "**Mandatory Patterns**:\n"
        for p in mandatory:
            text += f"- {p.title}\n"
        
        text += "\n**Relevant Patterns**:\n"
        for p in relevant[:3]:  # Top 3 only
            text += f"- {p.title}\n"
        
        return text
    
    async def finalize_and_summarize(
        self,
        subtasks: List[Subtask],
        results: List[SubAgentResult],
        summary_level: str  # low, medium, detailed
    ) -> Dict:
        """
        Perform final review and create summary.
        """
        
        # Collect all generated code
        all_code = {
            result.file_path: result.generated_code
            for result in results
            if result.success
        }
        
        # Create summary based on level
        summary = await self._create_summary(
            subtasks,
            results,
            all_code,
            summary_level
        )
        
        return {
            'files': all_code,
            'summary': summary,
            'subtasks_completed': len([r for r in results if r.success]),
            'subtasks_total': len(subtasks)
        }
    
    async def _create_summary(
        self,
        subtasks: List[Subtask],
        results: List[SubAgentResult],
        all_code: Dict[str, str],
        summary_level: str
    ) -> str:
        """
        Create summary at requested detail level.
        """
        
        if summary_level == "low":
            return self._create_low_summary(subtasks, results)
        elif summary_level == "medium":
            return self._create_medium_summary(subtasks, results, all_code)
        else:  # detailed
            return await self._create_detailed_summary(subtasks, results, all_code)
    
    def _create_low_summary(
        self,
        subtasks: List[Subtask],
        results: List[SubAgentResult]
    ) -> str:
        """Low detail summary - just status."""
        
        completed = len([r for r in results if r.success])
        total = len(subtasks)
        
        return f"""Task completed: {completed}/{total} subtasks successful

Files created:
{chr(10).join(f'- {r.file_path}' for r in results if r.success)}
"""
    
    def _create_medium_summary(
        self,
        subtasks: List[Subtask],
        results: List[SubAgentResult],
        all_code: Dict[str, str]
    ) -> str:
        """Medium detail summary - status + what was done."""
        
        summary = f"Task completed: {len([r for r in results if r.success])}/{len(subtasks)} subtasks\n\n"
        
        for subtask, result in zip(subtasks, results):
            if result.success:
                summary += f"✅ {subtask.description}\n"
                summary += f"   File: {result.file_path}\n"
                summary += f"   Lines of code: {len(result.generated_code.split(chr(10)))}\n\n"
            else:
                summary += f"❌ {subtask.description}\n"
                summary += f"   Error: {result.error}\n\n"
        
        return summary
    
    async def _create_detailed_summary(
        self,
        subtasks: List[Subtask],
        results: List[SubAgentResult],
        all_code: Dict[str, str]
    ) -> str:
        """Detailed summary - full explanation with LLM."""
        
        # Use LLM to create comprehensive summary
        code_snippets = "\n\n".join([
            f"File: {path}\n```\n{code[:500]}...\n```"
            for path, code in all_code.items()
        ])
        
        prompt = f"""Create a detailed summary of the work completed.

**Subtasks**:
{chr(10).join(f'{i+1}. {st.description} - {st.status.value}' for i, st in enumerate(subtasks))}

**Generated Code** (snippets):
{code_snippets}

**Your Task**: Write a comprehensive summary including:
1. Overview of what was accomplished
2. Key implementation details
3. Patterns and standards followed
4. Any notable decisions or trade-offs
5. Files created and their purpose

Be technical and specific.
"""
        
        response = self.client.messages.create(
            model="claude-sonnet-4-20250514",
            max_tokens=2000,
            messages=[{"role": "user", "content": prompt}]
        )
        
        return response.content[0].text
```

---

### Component 13: Code Reviewer Agent

**Purpose**: Comprehensive review against ALL standards and patterns.

```python
# core/code_reviewer.py

from typing import Dict, List
from dataclasses import dataclass
from enum import Enum

class ReviewSeverity(Enum):
    CRITICAL = "critical"  # Must fix
    IMPORTANT = "important"  # Should fix
    SUGGESTION = "suggestion"  # Nice to have

@dataclass
class ReviewComment:
    """Single review comment."""
    
    comment_id: str
    subtask_id: str
    file_path: str
    
    severity: ReviewSeverity
    category: str  # pattern_violation, security, quality, etc.
    
    issue: str
    location: str  # line number or code snippet
    suggestion: str
    rationale: str
    
    # Pattern reference (if applicable)
    violated_pattern_id: Optional[str] = None
    violated_pattern_title: Optional[str] = None
    
    # User feedback
    user_decision: Optional[str] = None  # approved, denied, modified

@dataclass
class CodeReview:
    """Complete code review."""
    
    review_id: str
    comments: List[ReviewComment]
    
    overall_assessment: str
    compliance_score: float  # 0-1
    needs_revision: bool
    
    timestamp: str

class CodeReviewerAgent:
    """
    Thorough code reviewer with full knowledge base access.
    """
    
    def __init__(
        self,
        anthropic_api_key: str,
        db_connection,
        pattern_retriever: 'EnhancedPatternRetriever'
    ):
        self.client = anthropic.Anthropic(api_key=anthropic_api_key)
        self.db = db_connection
        self.pattern_retriever = pattern_retriever
    
    async def review_code(
        self,
        subtasks: List[Subtask],
        results: List[SubAgentResult],
        task_plan: TaskPlan,
        project_context: Dict
    ) -> CodeReview:
        """
        Perform comprehensive code review.
        """
        
        # 1. Fetch ALL relevant standards and patterns
        all_standards = await self._fetch_all_standards(task_plan, project_context)
        
        # 2. Review each subtask's code
        all_comments = []
        
        for subtask, result in zip(subtasks, results):
            if result.success:
                comments = await self._review_subtask_code(
                    subtask,
                    result,
                    all_standards,
                    task_plan
                )
                all_comments.extend(comments)
        
        # 3. Calculate overall assessment
        critical_count = len([c for c in all_comments if c.severity == ReviewSeverity.CRITICAL])
        important_count = len([c for c in all_comments if c.severity == ReviewSeverity.IMPORTANT])
        
        needs_revision = critical_count > 0 or important_count > 0
        
        # Compliance score: 1.0 - (critical*0.3 + important*0.1)
        penalty = (critical_count * 0.3) + (important_count * 0.1)
        compliance_score = max(0.0, 1.0 - penalty)
        
        overall = self._generate_overall_assessment(all_comments, compliance_score)
        
        review = CodeReview(
            review_id=self._generate_review_id(),
            comments=all_comments,
            overall_assessment=overall,
            compliance_score=compliance_score,
            needs_revision=needs_revision,
            timestamp=self._timestamp()
        )
        
        return review
    
    async def _fetch_all_standards(
        self,
        task_plan: TaskPlan,
        project_context: Dict
    ) -> Dict:
        """
        Fetch ALL applicable standards and patterns.
        """
        
        cursor = self.db.cursor()
        
        # Get all mandatory patterns (project + global)
        cursor.execute("""
            SELECT 
                pattern_id, title, description, category,
                when_to_use, when_not_to_use,
                code_examples, anti_patterns,
                applies_to
            FROM patterns
            WHERE (is_global_standard = TRUE 
                   OR (is_project_standard = TRUE AND project_id = %s))
        """, (project_context.get('project_id'),))
        
        mandatory_patterns = []
        for row in cursor.fetchall():
            mandatory_patterns.append({
                'pattern_id': row[0],
                'title': row[1],
                'description': row[2],
                'category': row[3],
                'when_to_use': row[4],
                'when_not_to_use': row[5],
                'code_examples': row[6],
                'anti_patterns': row[7],
                'applies_to': row[8]
            })
        
        # Get all rules
        cursor.execute("""
            SELECT 
                rule_id, rule_text, category, severity,
                rationale, examples, validation_method
            FROM rules
            WHERE category = ANY(%s)
        """, (task_plan.critical_concerns,))
        
        rules = []
        for row in cursor.fetchall():
            rules.append({
                'rule_id': row[0],
                'rule_text': row[1],
                'category': row[2],
                'severity': row[3],
                'rationale': row[4],
                'examples': row[5],
                'validation_method': row[6]
            })
        
        return {
            'mandatory_patterns': mandatory_patterns,
            'rules': rules
        }
    
    async def _review_subtask_code(
        self,
        subtask: Subtask,
        result: SubAgentResult,
        all_standards: Dict,
        task_plan: TaskPlan
    ) -> List[ReviewComment]:
        """
        Review a single subtask's code.
        """
        
        # Format standards for prompt
        standards_text = self._format_standards_for_review(
            all_standards,
            subtask.applicable_patterns
        )
        
        prompt = f"""Review this code against ALL standards and patterns.

**File**: {result.file_path}

**Code**:
```
{result.generated_code}
```

**Patterns That Should Be Followed**:
{standards_text}

**Critical Concerns**: {', '.join(task_plan.critical_concerns)}

**Your Task**: Perform comprehensive code review.

Check for:
1. **Pattern Compliance**: Are ALL mandatory patterns followed?
2. **Anti-Patterns**: Are any anti-patterns present?
3. **Security**: Any security issues?
4. **Quality**: Code quality, readability, maintainability
5. **Completeness**: Missing error handling, logging, validation?

Return ONLY JSON:
```json
{{
  "comments": [
    {{
      "severity": "critical|important|suggestion",
      "category": "pattern_violation|security|quality|completeness",
      "issue": "Description of the issue",
      "location": "line 15-20" or "function authenticate()",
      "suggestion": "Specific fix to apply",
      "rationale": "Why this is an issue",
      "violated_pattern_id": "PAT-00042" (if applicable),
      "violated_pattern_title": "Repository Pattern" (if applicable)
    }}
  ]
}}
```

Be thorough but fair. Only flag real issues, not stylistic preferences.
"""
        
        response = self.client.messages.create(
            model="claude-sonnet-4-20250514",  # Use expensive model for review
            max_tokens=3000,
            messages=[{"role": "user", "content": prompt}]
        )
        
        response_text = response.content[0].text
        if "```json" in response_text:
            json_text = response_text.split("```json")[1].split("```")[0].strip()
        else:
            json_text = response_text.strip()
        
        import json
        review_data = json.loads(json_text)
        
        # Convert to ReviewComment objects
        comments = []
        for i, comment_data in enumerate(review_data['comments']):
            comment = ReviewComment(
                comment_id=f"{result.subtask_id}_comment_{i+1}",
                subtask_id=result.subtask_id,
                file_path=result.file_path,
                severity=ReviewSeverity(comment_data['severity']),
                category=comment_data['category'],
                issue=comment_data['issue'],
                location=comment_data['location'],
                suggestion=comment_data['suggestion'],
                rationale=comment_data['rationale'],
                violated_pattern_id=comment_data.get('violated_pattern_id'),
                violated_pattern_title=comment_data.get('violated_pattern_title')
            )
            comments.append(comment)
        
        return comments
    
    def _format_standards_for_review(
        self,
        all_standards: Dict,
        applicable_patterns: List
    ) -> str:
        """Format standards for review prompt."""
        
        text = "**Mandatory Patterns**:\n"
        for pattern in all_standards['mandatory_patterns']:
            text += f"\nPattern: {pattern['title']}\n"
            text += f"- Description: {pattern['description']}\n"
            text += f"- When to use: {pattern['when_to_use']}\n"
            
            if pattern['anti_patterns']:
                text += f"- Anti-patterns to avoid:\n"
                for ap in pattern['anti_patterns']:
                    text += f"  × {ap}\n"
        
        text += "\n**Rules**:\n"
        for rule in all_standards['rules']:
            text += f"\n- {rule['rule_text']} (Severity: {rule['severity']})\n"
            text += f"  Rationale: {rule['rationale']}\n"
        
        return text
    
    def _generate_overall_assessment(
        self,
        comments: List[ReviewComment],
        compliance_score: float
    ) -> str:
        """Generate overall assessment text."""
        
        critical = len([c for c in comments if c.severity == ReviewSeverity.CRITICAL])
        important = len([c for c in comments if c.severity == ReviewSeverity.IMPORTANT])
        suggestions = len([c for c in comments if c.severity == ReviewSeverity.SUGGESTION])
        
        assessment = f"Compliance Score: {compliance_score:.1%}\n\n"
        assessment += f"Issues Found:\n"
        assessment += f"- Critical: {critical}\n"
        assessment += f"- Important: {important}\n"
        assessment += f"- Suggestions: {suggestions}\n\n"
        
        if critical > 0:
            assessment += "Status: ❌ Critical issues must be fixed before approval.\n"
        elif important > 0:
            assessment += "Status: ⚠️  Important issues should be addressed.\n"
        elif suggestions > 0:
            assessment += "Status: ✅ Code is acceptable with minor suggestions.\n"
        else:
            assessment += "Status: ✅ Code meets all standards!\n"
        
        return assessment
    
    def _generate_review_id(self) -> str:
        import uuid
        return f"review_{uuid.uuid4().hex[:8]}"
    
    def _timestamp(self) -> str:
        from datetime import datetime
        return datetime.utcnow().isoformat()
```

---

### Component 14: User Review Interface & Feedback Loop

**Purpose**: Present review to user, collect feedback, iterate.

```python
# core/user_review_interface.py

from typing import Dict, List, Optional
from dataclasses import dataclass

@dataclass
class UserFeedback:
    """User's feedback on review comments."""
    
    comment_id: str
    decision: str  # approved, denied, modified
    user_note: Optional[str] = None
    modified_suggestion: Optional[str] = None

class UserReviewInterface:
    """
    Interface for user review and feedback.
    """
    
    def __init__(self, db_connection):
        self.db = db_connection
    
    def present_review_to_user(
        self,
        review: CodeReview
    ) -> Dict:
        """
        Format review for user presentation.
        
        In a real UI, this would be a web interface.
        For CLI/API, returns structured data.
        """
        
        presentation = {
            'review_id': review.review_id,
            'overall_assessment': review.overall_assessment,
            'compliance_score': review.compliance_score,
            'needs_revision': review.needs_revision,
            'comments_by_severity': {
                'critical': [],
                'important': [],
                'suggestion': []
            }
        }
        
        # Group comments by severity
        for comment in review.comments:
            comment_dict = {
                'comment_id': comment.comment_id,
                'file': comment.file_path,
                'issue': comment.issue,
                'location': comment.location,
                'suggestion': comment.suggestion,
                'rationale': comment.rationale,
                'category': comment.category
            }
            
            if comment.violated_pattern_id:
                comment_dict['violated_pattern'] = comment.violated_pattern_title
            
            presentation['comments_by_severity'][comment.severity.value].append(comment_dict)
        
        return presentation
    
    def collect_user_feedback(
        self,
        review: CodeReview,
        user_decisions: List[Dict]
    ) -> List[UserFeedback]:
        """
        Collect and validate user feedback on review comments.
        
        user_decisions format:
        [
            {
                "comment_id": "task_1_comment_1",
                "decision": "approved",  # or "denied" or "modified"
                "user_note": "Good catch!",
                "modified_suggestion": "Use this approach instead..."
            }
        ]
        """
        
        feedback_list = []
        
        for decision_data in user_decisions:
            # Find the comment
            comment = next(
                (c for c in review.comments if c.comment_id == decision_data['comment_id']),
                None
            )
            
            if not comment:
                print(f"⚠ Comment {decision_data['comment_id']} not found")
                continue
            
            feedback = UserFeedback(
                comment_id=decision_data['comment_id'],
                decision=decision_data['decision'],
                user_note=decision_data.get('user_note'),
                modified_suggestion=decision_data.get('modified_suggestion')
            )
            
            # Update comment with user decision
            comment.user_decision = decision_data['decision']
            
            feedback_list.append(feedback)
            
            # Store in database
            self._store_user_feedback(feedback, comment)
        
        return feedback_list
    
    def _store_user_feedback(
        self,
        feedback: UserFeedback,
        comment: ReviewComment
    ):
        """Store user feedback for learning."""
        
        cursor = self.db.cursor()
        
        cursor.execute("""
            INSERT INTO user_review_feedback
            (comment_id, file_path, category, severity, issue,
             original_suggestion, user_decision, user_note, modified_suggestion)
            VALUES (%s, %s, %s, %s, %s, %s, %s, %s, %s)
        """, (
            feedback.comment_id,
            comment.file_path,
            comment.category,
            comment.severity.value,
            comment.issue,
            comment.suggestion,
            feedback.decision,
            feedback.user_note,
            feedback.modified_suggestion
        ))
        
        self.db.commit()
    
    def get_changes_to_apply(
        self,
        review: CodeReview,
        user_feedback: List[UserFeedback]
    ) -> Dict[str, List[ReviewComment]]:
        """
        Get changes that need to be applied by sub-agents.
        
        Returns changes grouped by subtask_id.
        """
        
        changes_by_subtask = {}
        
        for comment in review.comments:
            # Find user feedback for this comment
            feedback = next(
                (f for f in user_feedback if f.comment_id == comment.comment_id),
                None
            )
            
            # Only apply approved or modified changes
            if feedback and feedback.decision in ['approved', 'modified']:
                if comment.subtask_id not in changes_by_subtask:
                    changes_by_subtask[comment.subtask_id] = []
                
                # Use modified suggestion if provided
                if feedback.modified_suggestion:
                    comment.suggestion = feedback.modified_suggestion
                
                changes_by_subtask[comment.subtask_id].append(comment)
        
        return changes_by_subtask
```

---

### Component 15: Revision Workflow (Sub-Agents Fix Issues)

**Purpose**: Sub-agents apply review feedback and revise code.

```python
# core/revision_workflow.py

from typing import Dict, List
import anthropic

class RevisionWorkflow:
    """
    Manages revision iterations based on user feedback.
    """
    
    def __init__(
        self,
        anthropic_api_key: str,
        work_orchestrator: WorkOrchestrator,
        code_reviewer: CodeReviewerAgent,
        user_interface: UserReviewInterface
    ):
        self.client = anthropic.Anthropic(api_key=anthropic_api_key)
        self.orchestrator = work_orchestrator
        self.reviewer = code_reviewer
        self.user_interface = user_interface
    
    async def execute_revision_loop(
        self,
        subtasks: List[Subtask],
        results: List[SubAgentResult],
        task_plan: TaskPlan,
        project_context: Dict,
        max_iterations: int = 3
    ) -> Dict:
        """
        Execute review → user feedback → revision loop.
        """
        
        current_results = results
        iteration = 0
        
        while iteration < max_iterations:
            iteration += 1
            print(f"\n📋 Review Iteration {iteration}")
            
            # 1. Code review
            review = await self.reviewer.review_code(
                subtasks,
                current_results,
                task_plan,
                project_context
            )
            
            # 2. Present to user
            presentation = self.user_interface.present_review_to_user(review)
            
            print(f"\n{review.overall_assessment}")
            
            if not review.needs_revision:
                print("✅ Code approved!")
                return {
                    'status': 'approved',
                    'final_results': current_results,
                    'iterations': iteration
                }
            
            # 3. Collect user feedback
            print("\n⏳ Waiting for user feedback...")
            user_decisions = await self._get_user_decisions(presentation)
            
            user_feedback = self.user_interface.collect_user_feedback(
                review,
                user_decisions
            )
            
            # 4. Get changes to apply
            changes_by_subtask = self.user_interface.get_changes_to_apply(
                review,
                user_feedback
            )
            
            if not changes_by_subtask:
                print("ℹ️  No changes approved by user")
                return {
                    'status': 'no_changes_needed',
                    'final_results': current_results,
                    'iterations': iteration
                }
            
            # 5. Apply revisions
            print(f"\n🔧 Applying revisions to {len(changes_by_subtask)} subtasks...")
            
            revised_results = await self._apply_revisions(
                subtasks,
                current_results,
                changes_by_subtask,
                task_plan
            )
            
            current_results = revised_results
        
        print(f"\n⚠️  Max iterations ({max_iterations}) reached")
        return {
            'status': 'max_iterations',
            'final_results': current_results,
            'iterations': iteration
        }
    
    async def _get_user_decisions(
        self,
        presentation: Dict
    ) -> List[Dict]:
        """
        Get user decisions on review comments.
        
        In a real implementation, this would be an async wait for UI input.
        For now, simulating approval of all critical/important issues.
        """
        
        # TODO: Replace with actual user input mechanism
        # This could be:
        # - Web UI with approval buttons
        # - CLI prompts
        # - API endpoint that waits for callback
        
        decisions = []
        
        for severity in ['critical', 'important']:
            for comment in presentation['comments_by_severity'][severity]:
                decisions.append({
                    'comment_id': comment['comment_id'],
                    'decision': 'approved',  # Simulate auto-approval for demo
                    'user_note': f'Fix this {severity} issue'
                })
        
        return decisions
    
    async def _apply_revisions(
        self,
        subtasks: List[Subtask],
        current_results: List[SubAgentResult],
        changes_by_subtask: Dict[str, List[ReviewComment]],
        task_plan: TaskPlan
    ) -> List[SubAgentResult]:
        """
        Have sub-agents apply revisions based on feedback.
        """
        
        revised_results = []
        
        for result in current_results:
            if result.subtask_id in changes_by_subtask:
                # This subtask needs revision
                changes = changes_by_subtask[result.subtask_id]
                
                # Find corresponding subtask
                subtask = next(
                    (st for st in subtasks if st.subtask_id == result.subtask_id),
                    None
                )
                
                if not subtask:
                    revised_results.append(result)
                    continue
                
                # Apply revisions
                revised_result = await self._revise_code(
                    subtask,
                    result,
                    changes,
                    task_plan
                )
                
                revised_results.append(revised_result)
            else:
                # No changes needed for this subtask
                revised_results.append(result)
        
        return revised_results
    
    async def _revise_code(
        self,
        subtask: Subtask,
        original_result: SubAgentResult,
        review_comments: List[ReviewComment],
        task_plan: TaskPlan
    ) -> SubAgentResult:
        """
        Revise code based on review comments.
        """
        
        # Format review comments for prompt
        comments_text = ""
        for i, comment in enumerate(review_comments, 1):
            comments_text += f"""
{i}. **{comment.severity.value.upper()}** - {comment.issue}
   Location: {comment.location}
   Fix: {comment.suggestion}
   Rationale: {comment.rationale}

"""
        
        prompt = f"""Revise this code based on review feedback.

**Original Code**:
```
{original_result.generated_code}
```

**Review Comments to Address**:
{comments_text}

**Your Task**: 
1. Fix ALL issues mentioned in review comments
2. Preserve existing functionality
3. Return ONLY the revised code, no explanation

Generate the corrected code:
"""
        
        try:
            response = self.client.messages.create(
                model="claude-haiku-4-20250514",  # Cheap model for revisions
                max_tokens=3000,
                messages=[{"role": "user", "content": prompt}]
            )
            
            revised_code = response.content[0].text
            
            # Extract code from markdown
            if "```" in revised_code:
                parts = revised_code.split("```")
                for i, part in enumerate(parts):
                    if i % 2 == 1:
                        lines = part.strip().split('\n')
                        if lines[0].strip() in ['python', 'java', 'csharp']:
                            revised_code = '\n'.join(lines[1:])
                        else:
                            revised_code = part.strip()
                        break
            
            # Update subtask
            subtask.revision_count += 1
            subtask.generated_code = revised_code
            
            return SubAgentResult(
                subtask_id=original_result.subtask_id,
                success=True,
                generated_code=revised_code,
                file_path=original_result.file_path,
                model_used="haiku",
                iterations=original_result.iterations + 1
            )
            
        except Exception as e:
            print(f"❌ Revision failed for {subtask.subtask_id}: {e}")
            return original_result
```

---

### Component 16: Workflow Storage in Knowledge Base

**Purpose**: Store the multi-agent workflow itself as a pattern.

```python
# core/workflow_pattern_storage.py

from typing import Dict
from dataclasses import dataclass

@dataclass
class WorkflowPattern:
    """The multi-agent workflow as a stored pattern."""
    
    workflow_id: str
    name: str
    description: str
    
    # Workflow definition
    stages: List[Dict]  # [planning, orchestration, execution, review, iteration]
    agent_types: List[str]  # [main, orchestrator, sub_agent, reviewer]
    
    # Configuration
    parallel_execution: bool
    max_review_iterations: int
    review_strictness: str
    summary_levels: List[str]
    
    # Applicability
    applies_to_project_types: List[str]
    required_for: List[str]  # e.g., ["production_code", "critical_systems"]
    
    # Version
    version: str
    is_active: bool

class WorkflowPatternStorage:
    """
    Stores the multi-agent workflow as a reusable pattern.
    """
    
    def __init__(self, db_connection):
        self.db = db_connection
    
    def store_workflow_pattern(self) -> str:
        """
        Store the default multi-agent workflow pattern.
        """
        
        workflow = WorkflowPattern(
            workflow_id="WORKFLOW-001",
            name="Multi-Agent Code Generation with Review Loop",
            description="Standard workflow for generating code using parallel sub-agents with comprehensive code review and user feedback loops",
            stages=[
                {
                    "stage": "planning",
                    "agent": "main_agent",
                    "actions": [
                        "Analyze task",
                        "Retrieve patterns",
                        "Assess complexity",
                        "Create task plan"
                    ]
                },
                {
                    "stage": "orchestration",
                    "agent": "work_orchestrator",
                    "actions": [
                        "Break into subtasks",
                        "Identify dependencies",
                        "Spawn sub-agents"
                    ]
                },
                {
                    "stage": "execution",
                    "agent": "sub_agents",
                    "actions": [
                        "Execute subtasks in parallel",
                        "Follow applicable patterns",
                        "Generate code"
                    ],
                    "parallel": True
                },
                {
                    "stage": "code_review",
                    "agent": "reviewer_agent",
                    "actions": [
                        "Fetch all standards",
                        "Review each subtask",
                        "Generate review comments",
                        "Calculate compliance score"
                    ]
                },
                {
                    "stage": "user_feedback",
                    "agent": "user_interface",
                    "actions": [
                        "Present review to user",
                        "Collect decisions",
                        "Store feedback"
                    ]
                },
                {
                    "stage": "revision",
                    "agent": "sub_agents",
                    "actions": [
                        "Apply user-approved changes",
                        "Revise code",
                        "Re-submit for review"
                    ],
                    "max_iterations": 3
                },
                {
                    "stage": "finalization",
                    "agent": "work_orchestrator",
                    "actions": [
                        "Collect all code",
                        "Generate summary",
                        "Submit to user"
                    ]
                }
            ],
            agent_types=[
                "main_agent",
                "work_orchestrator",
                "sub_agent",
                "code_reviewer",
                "user_interface"
            ],
            parallel_execution=True,
            max_review_iterations=3,
            review_strictness="standard",
            summary_levels=["low", "medium", "detailed"],
            applies_to_project_types=["all"],
            required_for=["production_code"],
            version="1.0.0",
            is_active=True
        )
        
        # Store in database
        cursor = self.db.cursor()
        
        cursor.execute("""
            INSERT INTO workflow_patterns
            (workflow_id, name, description, stages, agent_types,
             parallel_execution, max_review_iterations, review_strictness,
             summary_levels, applies_to_project_types, required_for,
             version, is_active)
            VALUES (%s, %s, %s, %s, %s, %s, %s, %s, %s, %s, %s, %s, %s)
            ON CONFLICT (workflow_id) DO UPDATE
            SET stages = EXCLUDED.stages,
                version = EXCLUDED.version,
                updated_at = NOW()
        """, (
            workflow.workflow_id,
            workflow.name,
            workflow.description,
            json.dumps(workflow.stages),
            workflow.agent_types,
            workflow.parallel_execution,
            workflow.max_review_iterations,
            workflow.review_strictness,
            workflow.summary_levels,
            workflow.applies_to_project_types,
            workflow.required_for,
            workflow.version,
            workflow.is_active
        ))
        
        self.db.commit()
        
        print(f"✅ Workflow pattern stored: {workflow.workflow_id}")
        return workflow.workflow_id
    
    def get_workflow_for_project(
        self,
        project_id: str,
        project_type: str
    ) -> WorkflowPattern:
        """
        Get appropriate workflow for a project.
        
        Checks for project-specific overrides first,
        falls back to default workflow.
        """
        
        cursor = self.db.cursor()
        
        # Check for project-specific workflow
        cursor.execute("""
            SELECT workflow_id, name, description, stages, agent_types,
                   parallel_execution, max_review_iterations, review_strictness,
                   summary_levels, version
            FROM workflow_patterns
            WHERE %s = ANY(applies_to_project_types)
              AND is_active = TRUE
            ORDER BY 
              CASE WHEN %s = ANY(applies_to_project_types) THEN 0 ELSE 1 END,
              created_at DESC
            LIMIT 1
        """, (project_id, project_type))
        
        row = cursor.fetchone()
        
        if row:
            import json
            return WorkflowPattern(
                workflow_id=row[0],
                name=row[1],
                description=row[2],
                stages=json.loads(row[3]),
                agent_types=row[4],
                parallel_execution=row[5],
                max_review_iterations=row[6],
                review_strictness=row[7],
                summary_levels=row[8],
                applies_to_project_types=[project_id],
                required_for=[],
                version=row[9],
                is_active=True
            )
        
        # Fallback to default
        return self._get_default_workflow()
    
    def update_workflow_parameter(
        self,
        workflow_id: str,
        parameter: str,
        new_value
    ):
        """
        Update a workflow parameter globally.
        
        Example: Change max_review_iterations from 3 to 5
        """
        
        cursor = self.db.cursor()
        
        valid_parameters = [
            'parallel_execution',
            'max_review_iterations',
            'review_strictness',
            'summary_levels'
        ]
        
        if parameter not in valid_parameters:
            raise ValueError(f"Invalid parameter: {parameter}")
        
        cursor.execute(f"""
            UPDATE workflow_patterns
            SET {parameter} = %s,
                updated_at = NOW()
            WHERE workflow_id = %s
        """, (new_value, workflow_id))
        
        self.db.commit()
        
        print(f"✅ Workflow {workflow_id} updated: {parameter} = {new_value}")
        print(f"   This change applies to ALL projects using this workflow")
```

---

## Database Schema Additions

```sql
-- Add to schema.sql

-- Workflow patterns table
CREATE TABLE workflow_patterns (
    workflow_id VARCHAR(50) PRIMARY KEY,
    name VARCHAR(200) NOT NULL,
    description TEXT,
    
    -- Workflow definition (JSON)
    stages JSONB NOT NULL,
    agent_types TEXT[],
    
    -- Configuration
    parallel_execution BOOLEAN DEFAULT TRUE,
    max_review_iterations INTEGER DEFAULT 3,
    review_strictness VARCHAR(50) DEFAULT 'standard',
    summary_levels TEXT[] DEFAULT ARRAY['low', 'medium', 'detailed'],
    
    -- Applicability
    applies_to_project_types TEXT[],
    required_for TEXT[],
    
    -- Versioning
    version VARCHAR(20) DEFAULT '1.0.0',
    is_active BOOLEAN DEFAULT TRUE,
    
    created_at TIMESTAMP DEFAULT NOW(),
    updated_at TIMESTAMP DEFAULT NOW()
);

CREATE INDEX idx_workflow_active ON workflow_patterns(is_active) WHERE is_active = TRUE;
CREATE INDEX idx_workflow_project_types ON workflow_patterns USING GIN(applies_to_project_types);

-- Review comments table
CREATE TABLE review_comments (
    comment_id VARCHAR(100) PRIMARY KEY,
    review_id VARCHAR(50),
    subtask_id VARCHAR(100),
    file_path VARCHAR(500),
    
    severity VARCHAR(50),
    category VARCHAR(100),
    
    issue TEXT,
    location VARCHAR(500),
    suggestion TEXT,
    rationale TEXT,
    
    violated_pattern_id VARCHAR(50),
    violated_pattern_title VARCHAR(200),
    
    user_decision VARCHAR(50),  -- approved, denied, modified
    
    created_at TIMESTAMP DEFAULT NOW()
);

CREATE INDEX idx_review_comments_review ON review_comments(review_id);
CREATE INDEX idx_review_comments_severity ON review_comments(severity);

-- User review feedback table
CREATE TABLE user_review_feedback (
    feedback_id SERIAL PRIMARY KEY,
    comment_id VARCHAR(100),
    
    file_path VARCHAR(500),
    category VARCHAR(100),
    severity VARCHAR(50),
    
    issue TEXT,
    original_suggestion TEXT,
    
    user_decision VARCHAR(50),
    user_note TEXT,
    modified_suggestion TEXT,
    
    timestamp TIMESTAMP DEFAULT NOW()
);

CREATE INDEX idx_user_feedback_decision ON user_review_feedback(user_decision);
CREATE INDEX idx_user_feedback_timestamp ON user_review_feedback(timestamp);

-- Workflow executions log
CREATE TABLE workflow_executions (
    execution_id VARCHAR(100) PRIMARY KEY,
    workflow_id VARCHAR(50) REFERENCES workflow_patterns(workflow_id),
    project_id VARCHAR(100),
    
    task_description TEXT,
    
    subtasks_count INTEGER,
    review_iterations INTEGER,
    final_status VARCHAR(50),
    
    compliance_score FLOAT,
    
    started_at TIMESTAMP,
    completed_at TIMESTAMP
);

CREATE INDEX idx_workflow_exec_project ON workflow_executions(project_id);
CREATE INDEX idx_workflow_exec_workflow ON workflow_executions(workflow_id);
CREATE INDEX idx_workflow_exec_timestamp ON workflow_executions(started_at);
```

---

## Complete Integration Example

```python
# main.py - Complete workflow example

async def execute_complete_workflow(
    task_description: str,
    project_context: Dict,
    user_preferences: Dict
):
    """
    Execute complete multi-agent workflow.
    """
    
    # Initialize all components
    main_agent = MainAgent(...)
    work_orchestrator = WorkOrchestrator(...)
    code_reviewer = CodeReviewerAgent(...)
    user_interface = UserReviewInterface(...)
    revision_workflow = RevisionWorkflow(...)
    workflow_storage = WorkflowPatternStorage(...)
    
    # 1. Get workflow pattern for this project
    workflow = workflow_storage.get_workflow_for_project(
        project_context['project_id'],
        project_context.get('project_type', 'standard')
    )
    
    print(f"📋 Using workflow: {workflow.name} (v{workflow.version})")
    
    # 2. Main Agent: Plan task
    print("\n🧠 Planning task...")
    task_plan = main_agent.plan_task(
        task_description,
        project_context,
        user_preferences
    )
    
    print(f"   Complexity: {task_plan.complexity}")
    print(f"   Estimated subtasks: {task_plan.subtask_count_estimate}")
    print(f"   Parallel work: {task_plan.requires_parallel_work}")
    
    # 3. Work Orchestrator: Execute with parallel sub-agents
    print("\n⚙️  Orchestrating work...")
    orchestration_result = await work_orchestrator.orchestrate_task(
        task_plan,
        project_context
    )
    
    subtasks = orchestration_result['subtasks']
    results = orchestration_result['results']
    
    print(f"   ✅ {len([r for r in results if r.success])}/{len(results)} subtasks completed")
    
    # 4. Revision Loop: Review → User Feedback → Revise
    print("\n📝 Starting review loop...")
    revision_result = await revision_workflow.execute_revision_loop(
        subtasks,
        results,
        task_plan,
        project_context,
        max_iterations=workflow.max_review_iterations
    )
    
    final_results = revision_result['final_results']
    
    print(f"   Status: {revision_result['status']}")
    print(f"   Iterations: {revision_result['iterations']}")
    
    # 5. Final Summary
    print("\n📊 Generating summary...")
    summary_level = user_preferences.get('summary_level', 'medium')
    
    final_summary = await work_orchestrator.finalize_and_summarize(
        subtasks,
        final_results,
        summary_level
    )
    
    print(f"\n{'='*60}")
    print(f"SUMMARY ({summary_level})")
    print(f"{'='*60}")
    print(final_summary['summary'])
    print(f"{'='*60}")
    
    # 6. Store execution log
    workflow_storage.log_execution(
        workflow.workflow_id,
        project_context['project_id'],
        task_description,
        revision_result
    )
    
    return {
        'files': final_summary['files'],
        'summary': final_summary['summary'],
        'workflow_used': workflow.workflow_id,
        'iterations': revision_result['iterations'],
        'status': revision_result['status']
    }


# Usage
if __name__ == "__main__":
    result = await execute_complete_workflow(
        task_description="Add user authentication with JWT tokens",
        project_context={
            'project_id': 'project-123',
            'project_type': 'api',
            'language': 'python',
            'framework': 'fastapi'
        },
        user_preferences={
            'review_strictness': 'strict',
            'summary_level': 'detailed'
        }
    )
    
    print(f"\n✅ Complete! Generated {len(result['files'])} files")
```

---

## Summary

This addendum adds the complete multi-agent orchestration workflow you described:

1. **Main Agent** plans and delegates
2. **Work Orchestrator** spawns parallel sub-agents
3. **Sub-Agents** execute tasks simultaneously (using cheap models)
4. **Code Reviewer** performs comprehensive review against ALL standards
5. **User Interface** presents review, collects feedback
6. **Revision Loop** applies user-approved changes, iterates until approved
7. **Workflow Storage** stores the workflow itself as a pattern in knowledge base

**Key Features:**
- ✅ Parallel sub-agent execution
- ✅ Comprehensive code review
- ✅ User feedback loop
- ✅ Multiple revision iterations
- ✅ Variable summary levels (low/medium/detailed)
- ✅ Workflow stored in knowledge base
- ✅ Universal application across projects
- ✅ Can be modified globally

**The workflow is now a first-class pattern** that can be versioned, modified, and applied universally!
