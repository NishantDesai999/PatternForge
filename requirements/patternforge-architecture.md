# PatternForge: Context-Aware Code Pattern Engine
## Comprehensive Architecture Document

---

## Executive Summary

**Problem Statement:**
Multi-agent AI coding systems fail to consistently follow coding standards, even when provided with comprehensive rules and promoted patterns. Current approaches dump entire rulesets into context, overwhelming models and resulting in:
- Standards being ignored or forgotten
- Every new project feeling like starting from scratch
- Review loops that don't converge
- Pattern databases that grow but don't improve outcomes
- Expensive models required for basic tasks

**Solution:**
PatternForge is a context-aware pattern engine that uses semantic retrieval, hierarchical reasoning, and adaptive learning to ensure AI agents consistently follow coding standards across projects. Instead of context-dumping, it intelligently retrieves relevant patterns, provides step-by-step application guides, and learns from outcomes.

**Key Innovation:**
RAG (Retrieval-Augmented Generation) + Reasoning for code patterns, not context stuffing.

---

## Table of Contents

1. [System Overview](#system-overview)
2. [Core Architecture](#core-architecture)
3. [Data Collection & Migration](#data-collection--migration)
4. [Component Specifications](#component-specifications)
5. [Implementation Phases](#implementation-phases)
6. [Integration Guide](#integration-guide)
7. [API Specifications](#api-specifications)
8. [Database Schema](#database-schema)
9. [Deployment Architecture](#deployment-architecture)
10. [Success Metrics](#success-metrics)

---

## System Overview

### Architecture Diagram

```
┌─────────────────────────────────────────────────────────────────┐
│                        PatternForge System                       │
└─────────────────────────────────────────────────────────────────┘

┌─────────────────────────────────────────────────────────────────┐
│ Phase 1: Data Collection & Knowledge Base Construction          │
├─────────────────────────────────────────────────────────────────┤
│                                                                  │
│  ┌──────────────────┐  ┌──────────────────┐  ┌──────────────┐ │
│  │ Global Standards │  │ Project-Level    │  │ Existing     │ │
│  │ (agents.md)      │  │ agents.md Files  │  │ Pattern DB   │ │
│  └────────┬─────────┘  └────────┬─────────┘  └──────┬───────┘ │
│           │                     │                     │          │
│           └─────────────────────┼─────────────────────┘          │
│                                 │                                │
│                                 ▼                                │
│                    ┌─────────────────────────┐                  │
│                    │ Knowledge Extractor     │                  │
│                    │ - Parse standards docs  │                  │
│                    │ - Extract patterns      │                  │
│                    │ - Identify rules        │                  │
│                    │ - Categorize content    │                  │
│                    │ - Generate embeddings   │                  │
│                    └────────────┬────────────┘                  │
│                                 │                                │
│                                 ▼                                │
│                    ┌─────────────────────────┐                  │
│                    │ Pattern Storage Layer   │                  │
│                    │ (PostgreSQL + pgvector) │                  │
│                    └─────────────────────────┘                  │
└─────────────────────────────────────────────────────────────────┘

┌─────────────────────────────────────────────────────────────────┐
│ Phase 2: Runtime Pattern Retrieval & Application                │
├─────────────────────────────────────────────────────────────────┤
│                                                                  │
│  User Task: "Add authentication endpoint"                       │
│           │                                                      │
│           ▼                                                      │
│  ┌─────────────────────────────────────────┐                   │
│  │ 1. Task Analysis Engine                  │                   │
│  │    - Classify task type                  │                   │
│  │    - Extract components involved         │                   │
│  │    - Identify concerns (security, etc)   │                   │
│  │    Output: TaskContext                   │                   │
│  └────────────────┬─────────────────────────┘                   │
│                   │                                              │
│                   ▼                                              │
│  ┌─────────────────────────────────────────┐                   │
│  │ 2. Project Context Analyzer              │                   │
│  │    - Load project-specific patterns      │                   │
│  │    - Detect codebase conventions         │                   │
│  │    - Identify tech stack                 │                   │
│  │    Output: ProjectContext                │                   │
│  └────────────────┬─────────────────────────┘                   │
│                   │                                              │
│                   ▼                                              │
│  ┌─────────────────────────────────────────┐                   │
│  │ 3. Semantic Pattern Retrieval            │                   │
│  │    - Vector similarity search            │                   │
│  │    - Filter by project context           │                   │
│  │    - Rank by relevance × success_rate    │                   │
│  │    - Resolve dependencies & conflicts    │                   │
│  │    Output: Top 5-7 relevant patterns     │                   │
│  └────────────────┬─────────────────────────┘                   │
│                   │                                              │
│                   ▼                                              │
│  ┌─────────────────────────────────────────┐                   │
│  │ 4. Pattern Application Reasoner          │                   │
│  │    - Generate step-by-step guide         │                   │
│  │    - Combine patterns coherently         │                   │
│  │    - Provide code templates              │                   │
│  │    - Include validation checklist        │                   │
│  │    Output: ApplicationGuide              │                   │
│  └────────────────┬─────────────────────────┘                   │
│                   │                                              │
│                   ▼                                              │
│  ┌─────────────────────────────────────────┐                   │
│  │ 5. Multi-Agent Orchestrator              │                   │
│  │    - Planning Agent (expensive model)    │                   │
│  │    - Execution Agent (cheap + templates) │                   │
│  │    - Review Agent (hybrid verification)  │                   │
│  │    - Iteration controller                │                   │
│  │    Output: Generated code                │                   │
│  └────────────────┬─────────────────────────┘                   │
│                   │                                              │
│                   ▼                                              │
│  ┌─────────────────────────────────────────┐                   │
│  │ 6. Verification & Learning Loop          │                   │
│  │    - Static analysis validation          │                   │
│  │    - Semantic review                     │                   │
│  │    - Record outcome & update stats       │                   │
│  │    - Detect oscillation & escalate       │                   │
│  │    Output: Validated code + feedback     │                   │
│  └─────────────────────────────────────────┘                   │
└─────────────────────────────────────────────────────────────────┘
```

---

## Core Architecture

### Design Principles

1. **Context Efficiency**: Never load more than 5-7 patterns per task (vs. dumping 100+ rules)
2. **Semantic Retrieval**: Use vector embeddings for intelligent pattern matching
3. **Hierarchical Reasoning**: Combine patterns with decision trees, not flat lists
4. **Adaptive Learning**: Track outcomes and adjust pattern rankings
5. **Project Awareness**: Understand project-specific context, not just global standards
6. **Convergence Guarantees**: Detect oscillation, prevent infinite loops
7. **Model Economics**: Use cheap models with constraints, expensive models for planning only

---

## Data Collection & Migration

### Phase 1.1: Inventory Existing Standards

**Objective**: Collect all existing standards documents and pattern databases.

**Data Sources:**

1. **Global Standards**
   - Location: `/company/standards/agents.md` (or similar)
   - Content: Company-wide coding standards, security policies, architecture guidelines
   - Format: Markdown, likely 1000-5000 lines

2. **Project-Level Standards**
   - Location: Each project repository's `agents.md` or `.ai/` directory
   - Content: Project-specific patterns, conventions, tech stack rules
   - Format: Markdown, likely 100-1000 lines per project

3. **Existing Pattern Database**
   - Location: Current database (format TBD)
   - Content: Promoted patterns with usage counts
   - Format: Structured data (SQL/NoSQL)

4. **Code Examples**
   - Location: Existing codebases
   - Content: Real implementations that follow standards
   - Format: Source code files

**Collection Script:**

```python
# data_collection/collect_standards.py

import os
import json
from pathlib import Path
from typing import List, Dict
import yaml

class StandardsCollector:
    """
    Collects all existing standards documents and patterns.
    """
    
    def __init__(self, config_path: str):
        with open(config_path) as f:
            self.config = yaml.safe_load(f)
        self.collected_data = {
            "global_standards": [],
            "project_standards": [],
            "existing_patterns": [],
            "metadata": {}
        }
    
    def collect_global_standards(self):
        """
        Collect company-wide standards documents.
        """
        global_paths = self.config['global_standards_paths']
        
        for path in global_paths:
            if os.path.exists(path):
                content = self._read_file(path)
                self.collected_data['global_standards'].append({
                    "source": path,
                    "content": content,
                    "type": "global",
                    "format": self._detect_format(path),
                    "size_bytes": len(content.encode('utf-8')),
                    "collected_at": self._timestamp()
                })
                print(f"✓ Collected global standard: {path}")
            else:
                print(f"✗ Not found: {path}")
    
    def collect_project_standards(self):
        """
        Collect project-specific standards from all repositories.
        """
        project_roots = self.config['project_roots']
        standard_filenames = self.config['standard_filenames']  # e.g., ['agents.md', '.ai/config.md']
        
        for project_root in project_roots:
            project_name = os.path.basename(project_root)
            
            for filename in standard_filenames:
                path = os.path.join(project_root, filename)
                
                if os.path.exists(path):
                    content = self._read_file(path)
                    
                    # Also collect project metadata
                    project_metadata = self._analyze_project(project_root)
                    
                    self.collected_data['project_standards'].append({
                        "project_name": project_name,
                        "project_root": project_root,
                        "source": path,
                        "content": content,
                        "type": "project",
                        "metadata": project_metadata,
                        "collected_at": self._timestamp()
                    })
                    print(f"✓ Collected project standard: {project_name}/{filename}")
    
    def collect_existing_patterns(self):
        """
        Export existing pattern database.
        """
        # This will vary based on current implementation
        # Adapt to your existing database structure
        
        db_config = self.config['pattern_db']
        
        # Example for SQL database
        if db_config['type'] == 'postgres':
            patterns = self._export_from_postgres(db_config)
        elif db_config['type'] == 'json':
            patterns = self._export_from_json(db_config['path'])
        else:
            print(f"✗ Unsupported database type: {db_config['type']}")
            return
        
        self.collected_data['existing_patterns'] = patterns
        print(f"✓ Collected {len(patterns)} existing patterns")
    
    def _analyze_project(self, project_root: str) -> Dict:
        """
        Analyze project to extract metadata.
        """
        metadata = {
            "tech_stack": self._detect_tech_stack(project_root),
            "languages": self._detect_languages(project_root),
            "frameworks": self._detect_frameworks(project_root),
            "file_count": self._count_files(project_root),
        }
        return metadata
    
    def _detect_tech_stack(self, project_root: str) -> List[str]:
        """Detect technology stack from project files."""
        tech_stack = []
        
        # Check for common config files
        checks = {
            "package.json": ["node", "javascript", "typescript"],
            "requirements.txt": ["python"],
            "pom.xml": ["java", "maven"],
            "build.gradle": ["java", "gradle"],
            "Cargo.toml": ["rust"],
            "go.mod": ["golang"],
            "*.csproj": ["csharp", "dotnet"],
        }
        
        for file_pattern, techs in checks.items():
            if self._file_exists_in_project(project_root, file_pattern):
                tech_stack.extend(techs)
        
        return list(set(tech_stack))
    
    def _detect_languages(self, project_root: str) -> Dict[str, int]:
        """Count lines of code by language."""
        # Simplified - in production, use a tool like tokei or cloc
        extensions = {
            '.py': 'python',
            '.js': 'javascript',
            '.ts': 'typescript',
            '.java': 'java',
            '.cs': 'csharp',
            '.go': 'golang',
            '.rs': 'rust',
        }
        
        lang_counts = {}
        for ext, lang in extensions.items():
            count = self._count_files_by_extension(project_root, ext)
            if count > 0:
                lang_counts[lang] = count
        
        return lang_counts
    
    def save_collected_data(self, output_path: str):
        """
        Save all collected data to a structured format.
        """
        os.makedirs(os.path.dirname(output_path), exist_ok=True)
        
        with open(output_path, 'w') as f:
            json.dump(self.collected_data, f, indent=2)
        
        print(f"\n✓ Saved collected data to: {output_path}")
        print(f"  - Global standards: {len(self.collected_data['global_standards'])}")
        print(f"  - Project standards: {len(self.collected_data['project_standards'])}")
        print(f"  - Existing patterns: {len(self.collected_data['existing_patterns'])}")
    
    # Helper methods
    def _read_file(self, path: str) -> str:
        with open(path, 'r', encoding='utf-8') as f:
            return f.read()
    
    def _detect_format(self, path: str) -> str:
        ext = os.path.splitext(path)[1].lower()
        formats = {
            '.md': 'markdown',
            '.txt': 'text',
            '.yaml': 'yaml',
            '.yml': 'yaml',
            '.json': 'json',
        }
        return formats.get(ext, 'unknown')
    
    def _timestamp(self) -> str:
        from datetime import datetime
        return datetime.utcnow().isoformat()
    
    def _file_exists_in_project(self, project_root: str, pattern: str) -> bool:
        from glob import glob
        matches = glob(os.path.join(project_root, '**', pattern), recursive=True)
        return len(matches) > 0
    
    def _count_files(self, project_root: str) -> int:
        count = 0
        for root, dirs, files in os.walk(project_root):
            # Skip common ignore directories
            dirs[:] = [d for d in dirs if d not in ['.git', 'node_modules', '__pycache__', 'venv']]
            count += len(files)
        return count
    
    def _count_files_by_extension(self, project_root: str, extension: str) -> int:
        from glob import glob
        pattern = os.path.join(project_root, '**', f'*{extension}')
        return len(glob(pattern, recursive=True))
    
    def _export_from_postgres(self, db_config: Dict) -> List[Dict]:
        """Export patterns from PostgreSQL database."""
        import psycopg2
        import psycopg2.extras
        
        conn = psycopg2.connect(
            host=db_config['host'],
            database=db_config['database'],
            user=db_config['user'],
            password=db_config['password']
        )
        
        cursor = conn.cursor(cursor_factory=psycopg2.extras.RealDictCursor)
        cursor.execute("SELECT * FROM patterns")
        patterns = cursor.fetchall()
        
        conn.close()
        return [dict(p) for p in patterns]
    
    def _export_from_json(self, path: str) -> List[Dict]:
        """Export patterns from JSON file."""
        with open(path, 'r') as f:
            return json.load(f)


# Configuration file: data_collection/config.yaml
"""
global_standards_paths:
  - /company/standards/agents.md
  - /company/standards/security.md
  - /company/standards/architecture.md

project_roots:
  - /repos/project-a
  - /repos/project-b
  - /repos/project-c

standard_filenames:
  - agents.md
  - .ai/config.md
  - .ai/standards.md

pattern_db:
  type: postgres  # or 'json', 'mongodb', etc.
  host: localhost
  database: patterns
  user: readonly_user
  password: ${PATTERN_DB_PASSWORD}
"""

# Usage
if __name__ == "__main__":
    collector = StandardsCollector("config.yaml")
    
    print("Starting data collection...\n")
    collector.collect_global_standards()
    collector.collect_project_standards()
    collector.collect_existing_patterns()
    
    collector.save_collected_data("collected_data/standards_export.json")
    print("\n✓ Data collection complete!")
```

---

### Phase 1.2: Knowledge Extraction

**Objective**: Parse collected documents and extract structured patterns, rules, and standards.

**Extraction Strategy:**

Use an LLM (GPT-4 or Claude Sonnet) to parse unstructured markdown documents and extract structured patterns.

```python
# knowledge_extraction/extractor.py

from typing import List, Dict, Optional
import json
import anthropic
from dataclasses import dataclass, asdict
from enum import Enum

class PatternCategory(Enum):
    ARCHITECTURE = "architecture"
    SECURITY = "security"
    ERROR_HANDLING = "error_handling"
    DATA_ACCESS = "data_access"
    API_DESIGN = "api_design"
    TESTING = "testing"
    LOGGING = "logging"
    NAMING = "naming"
    CODE_STYLE = "code_style"
    PERFORMANCE = "performance"
    DOCUMENTATION = "documentation"

class PatternScope(Enum):
    GLOBAL = "global"
    PROJECT = "project"
    LANGUAGE = "language"
    FRAMEWORK = "framework"

@dataclass
class ExtractedPattern:
    """Structured representation of a code pattern."""
    
    # Identity
    title: str
    description: str
    category: str
    scope: str
    
    # Applicability
    applies_to: List[str]  # e.g., ["services", "controllers", "repositories"]
    languages: List[str]  # e.g., ["python", "java", "csharp"]
    frameworks: List[str]  # e.g., ["spring", "asp.net", "django"]
    
    # Usage guidance
    when_to_use: str
    when_not_to_use: str
    benefits: List[str]
    trade_offs: List[str]
    
    # Code examples
    code_examples: Dict[str, str]  # e.g., {"python": "...", "java": "..."}
    anti_patterns: List[str]
    
    # Relationships
    requires: List[str]  # Pattern IDs this depends on
    conflicts_with: List[str]  # Pattern IDs this conflicts with
    related_patterns: List[str]  # Similar or complementary patterns
    
    # Metadata
    source: str  # Where this was extracted from
    confidence: float  # Extraction confidence (0-1)
    
    # To be populated later
    pattern_id: Optional[str] = None
    embedding: Optional[List[float]] = None
    success_rate: Optional[float] = None
    usage_count: int = 0

@dataclass
class ExtractedRule:
    """Structured representation of a coding rule/standard."""
    
    rule_text: str
    category: str
    severity: str  # "critical", "important", "recommended"
    applies_to: List[str]
    rationale: str
    examples: Dict[str, str]  # good vs bad examples
    validation_method: str  # "static_analysis", "ai_review", "manual"
    source: str
    
    rule_id: Optional[str] = None

class KnowledgeExtractor:
    """
    Extracts structured patterns and rules from unstructured documents.
    """
    
    def __init__(self, anthropic_api_key: str):
        self.client = anthropic.Anthropic(api_key=anthropic_api_key)
    
    def extract_from_document(
        self, 
        content: str, 
        source: str, 
        scope: PatternScope
    ) -> Dict[str, List]:
        """
        Extract patterns and rules from a document.
        """
        
        # Break document into chunks if too large
        chunks = self._chunk_document(content)
        
        all_patterns = []
        all_rules = []
        
        for i, chunk in enumerate(chunks):
            print(f"Processing chunk {i+1}/{len(chunks)} from {source}...")
            
            extraction = self._extract_from_chunk(chunk, source, scope)
            
            all_patterns.extend(extraction['patterns'])
            all_rules.extend(extraction['rules'])
        
        # Deduplicate similar patterns
        unique_patterns = self._deduplicate_patterns(all_patterns)
        unique_rules = self._deduplicate_rules(all_rules)
        
        return {
            'patterns': unique_patterns,
            'rules': unique_rules
        }
    
    def _extract_from_chunk(
        self, 
        chunk: str, 
        source: str, 
        scope: PatternScope
    ) -> Dict[str, List]:
        """
        Use LLM to extract patterns from a chunk of text.
        """
        
        prompt = self._build_extraction_prompt(chunk, source, scope)
        
        response = self.client.messages.create(
            model="claude-sonnet-4-20250514",
            max_tokens=4000,
            messages=[
                {
                    "role": "user",
                    "content": prompt
                }
            ]
        )
        
        # Parse JSON response
        response_text = response.content[0].text
        
        # Extract JSON from response (handle markdown code blocks)
        if "```json" in response_text:
            json_text = response_text.split("```json")[1].split("```")[0].strip()
        else:
            json_text = response_text
        
        try:
            extracted_data = json.loads(json_text)
        except json.JSONDecodeError as e:
            print(f"Failed to parse JSON: {e}")
            print(f"Response: {response_text[:500]}")
            return {'patterns': [], 'rules': []}
        
        # Convert to structured objects
        patterns = [
            ExtractedPattern(**p, source=source, scope=scope.value)
            for p in extracted_data.get('patterns', [])
        ]
        
        rules = [
            ExtractedRule(**r, source=source)
            for r in extracted_data.get('rules', [])
        ]
        
        return {'patterns': patterns, 'rules': rules}
    
    def _build_extraction_prompt(
        self, 
        chunk: str, 
        source: str, 
        scope: PatternScope
    ) -> str:
        """
        Build prompt for LLM to extract patterns.
        """
        
        return f"""You are a software engineering expert analyzing coding standards documents.

Your task is to extract structured patterns and rules from the following document.

**Document Source**: {source}
**Scope**: {scope.value}

**Document Content**:
```
{chunk}
```

**Instructions**:

1. **Identify Code Patterns**: Look for recurring design patterns, architectural patterns, or coding conventions described in the document.

2. **Extract Coding Rules**: Identify explicit rules, standards, or requirements.

3. **Structure Your Output** as JSON with this exact schema:

```json
{{
  "patterns": [
    {{
      "title": "Short descriptive title",
      "description": "Detailed description of the pattern",
      "category": "architecture|security|error_handling|data_access|api_design|testing|logging|naming|code_style|performance|documentation",
      "scope": "{scope.value}",
      "applies_to": ["services", "controllers", etc],
      "languages": ["python", "java", etc],
      "frameworks": ["spring", "django", etc],
      "when_to_use": "When to apply this pattern",
      "when_not_to_use": "When NOT to use this pattern",
      "benefits": ["benefit 1", "benefit 2"],
      "trade_offs": ["trade-off 1", "trade-off 2"],
      "code_examples": {{
        "python": "code example in python",
        "java": "code example in java"
      }},
      "anti_patterns": ["anti-pattern 1", "anti-pattern 2"],
      "requires": ["pattern names this depends on"],
      "conflicts_with": ["pattern names this conflicts with"],
      "related_patterns": ["similar patterns"],
      "confidence": 0.95
    }}
  ],
  "rules": [
    {{
      "rule_text": "Always use parameterized queries",
      "category": "security",
      "severity": "critical|important|recommended",
      "applies_to": ["database access", "sql queries"],
      "rationale": "Why this rule exists",
      "examples": {{
        "good": "cursor.execute('SELECT * FROM users WHERE id = ?', (user_id,))",
        "bad": "cursor.execute(f'SELECT * FROM users WHERE id = {{user_id}}')"
      }},
      "validation_method": "static_analysis|ai_review|manual"
    }}
  ]
}}
```

**Important**:
- Only extract patterns and rules that are EXPLICITLY stated or strongly implied
- Set confidence to 0.9-1.0 for explicit patterns, 0.6-0.8 for implied ones
- If code examples are in the document, include them
- If relationships between patterns are mentioned, capture them in requires/conflicts_with/related_patterns
- Return ONLY the JSON, no additional text

Extract all patterns and rules from the document above."""
    
    def _chunk_document(self, content: str, max_chunk_size: int = 8000) -> List[str]:
        """
        Split document into chunks that fit in context window.
        """
        # Simple chunking by character count
        # In production, use smarter chunking (by section, preserving context)
        
        if len(content) <= max_chunk_size:
            return [content]
        
        chunks = []
        current_chunk = []
        current_size = 0
        
        lines = content.split('\n')
        
        for line in lines:
            line_size = len(line) + 1  # +1 for newline
            
            if current_size + line_size > max_chunk_size and current_chunk:
                chunks.append('\n'.join(current_chunk))
                current_chunk = [line]
                current_size = line_size
            else:
                current_chunk.append(line)
                current_size += line_size
        
        if current_chunk:
            chunks.append('\n'.join(current_chunk))
        
        return chunks
    
    def _deduplicate_patterns(self, patterns: List[ExtractedPattern]) -> List[ExtractedPattern]:
        """
        Remove duplicate patterns based on title similarity.
        """
        # Simple deduplication - in production, use embedding similarity
        seen_titles = set()
        unique = []
        
        for pattern in patterns:
            title_normalized = pattern.title.lower().strip()
            if title_normalized not in seen_titles:
                seen_titles.add(title_normalized)
                unique.append(pattern)
        
        return unique
    
    def _deduplicate_rules(self, rules: List[ExtractedRule]) -> List[ExtractedRule]:
        """
        Remove duplicate rules.
        """
        seen_rules = set()
        unique = []
        
        for rule in rules:
            rule_normalized = rule.rule_text.lower().strip()
            if rule_normalized not in seen_rules:
                seen_rules.add(rule_normalized)
                unique.append(rule)
        
        return unique


# Usage example
if __name__ == "__main__":
    import os
    
    # Load collected data
    with open("collected_data/standards_export.json") as f:
        collected_data = json.load(f)
    
    extractor = KnowledgeExtractor(
        anthropic_api_key=os.environ["ANTHROPIC_API_KEY"]
    )
    
    all_patterns = []
    all_rules = []
    
    # Extract from global standards
    for standard in collected_data['global_standards']:
        print(f"\nExtracting from: {standard['source']}")
        
        result = extractor.extract_from_document(
            content=standard['content'],
            source=standard['source'],
            scope=PatternScope.GLOBAL
        )
        
        all_patterns.extend(result['patterns'])
        all_rules.extend(result['rules'])
        
        print(f"  - Found {len(result['patterns'])} patterns")
        print(f"  - Found {len(result['rules'])} rules")
    
    # Extract from project standards
    for standard in collected_data['project_standards']:
        print(f"\nExtracting from: {standard['project_name']}/{standard['source']}")
        
        result = extractor.extract_from_document(
            content=standard['content'],
            source=f"{standard['project_name']}/{standard['source']}",
            scope=PatternScope.PROJECT
        )
        
        all_patterns.extend(result['patterns'])
        all_rules.extend(result['rules'])
        
        print(f"  - Found {len(result['patterns'])} patterns")
        print(f"  - Found {len(result['rules'])} rules")
    
    # Save extracted knowledge
    output = {
        'patterns': [asdict(p) for p in all_patterns],
        'rules': [asdict(r) for r in all_rules],
        'metadata': {
            'total_patterns': len(all_patterns),
            'total_rules': len(all_rules),
            'extraction_date': extractor._timestamp()
        }
    }
    
    with open("collected_data/extracted_knowledge.json", 'w') as f:
        json.dump(output, f, indent=2)
    
    print(f"\n✓ Extraction complete!")
    print(f"  - Total patterns: {len(all_patterns)}")
    print(f"  - Total rules: {len(all_rules)}")
```

---

### Phase 1.3: Embedding Generation

**Objective**: Generate vector embeddings for semantic search.

```python
# knowledge_extraction/embedding_generator.py

import json
import numpy as np
from typing import List, Dict
import anthropic
from tqdm import tqdm

class EmbeddingGenerator:
    """
    Generates embeddings for patterns and rules.
    """
    
    def __init__(self, anthropic_api_key: str):
        self.client = anthropic.Anthropic(api_key=anthropic_api_key)
    
    def generate_pattern_embeddings(
        self, 
        patterns: List[Dict]
    ) -> List[Dict]:
        """
        Generate embeddings for each pattern.
        """
        print(f"Generating embeddings for {len(patterns)} patterns...")
        
        for i, pattern in enumerate(tqdm(patterns)):
            # Create embedding text from pattern
            embedding_text = self._pattern_to_embedding_text(pattern)
            
            # Generate embedding using Voyage AI or OpenAI
            # Note: Anthropic doesn't have embeddings API yet, so use alternatives
            embedding = self._generate_embedding(embedding_text)
            
            pattern['embedding'] = embedding
            pattern['pattern_id'] = f"PAT-{i+1:05d}"
        
        return patterns
    
    def generate_rule_embeddings(
        self,
        rules: List[Dict]
    ) -> List[Dict]:
        """
        Generate embeddings for each rule.
        """
        print(f"Generating embeddings for {len(rules)} rules...")
        
        for i, rule in enumerate(tqdm(rules)):
            embedding_text = self._rule_to_embedding_text(rule)
            embedding = self._generate_embedding(embedding_text)
            
            rule['embedding'] = embedding
            rule['rule_id'] = f"RULE-{i+1:05d}"
        
        return rules
    
    def _pattern_to_embedding_text(self, pattern: Dict) -> str:
        """
        Convert pattern to text suitable for embedding.
        """
        parts = [
            f"Title: {pattern['title']}",
            f"Description: {pattern['description']}",
            f"Category: {pattern['category']}",
            f"When to use: {pattern['when_to_use']}",
            f"Applies to: {', '.join(pattern['applies_to'])}",
        ]
        
        return " | ".join(parts)
    
    def _rule_to_embedding_text(self, rule: Dict) -> str:
        """
        Convert rule to text suitable for embedding.
        """
        parts = [
            f"Rule: {rule['rule_text']}",
            f"Category: {rule['category']}",
            f"Rationale: {rule['rationale']}",
            f"Applies to: {', '.join(rule['applies_to'])}",
        ]
        
        return " | ".join(parts)
    
    def _generate_embedding(self, text: str) -> List[float]:
        """
        Generate embedding using Voyage AI (recommended for code/text).
        Alternative: OpenAI's text-embedding-3-small
        """
        
        # Using OpenAI as example (replace with Voyage AI in production)
        import openai
        
        response = openai.embeddings.create(
            model="text-embedding-3-small",
            input=text
        )
        
        return response.data[0].embedding


# Usage
if __name__ == "__main__":
    import os
    
    with open("collected_data/extracted_knowledge.json") as f:
        knowledge = json.load(f)
    
    generator = EmbeddingGenerator(
        anthropic_api_key=os.environ["ANTHROPIC_API_KEY"]
    )
    
    # Generate embeddings
    patterns_with_embeddings = generator.generate_pattern_embeddings(
        knowledge['patterns']
    )
    
    rules_with_embeddings = generator.generate_rule_embeddings(
        knowledge['rules']
    )
    
    # Save
    output = {
        'patterns': patterns_with_embeddings,
        'rules': rules_with_embeddings,
        'metadata': knowledge['metadata']
    }
    
    with open("collected_data/knowledge_with_embeddings.json", 'w') as f:
        json.dump(output, f, indent=2)
    
    print("✓ Embeddings generated!")
```

---

## Database Schema

### PostgreSQL with pgvector

```sql
-- schema.sql

-- Enable pgvector extension
CREATE EXTENSION IF NOT EXISTS vector;

-- Patterns table
CREATE TABLE patterns (
    pattern_id VARCHAR(50) PRIMARY KEY,
    
    -- Core content
    title VARCHAR(500) NOT NULL,
    description TEXT NOT NULL,
    category VARCHAR(100) NOT NULL,
    scope VARCHAR(50) NOT NULL,
    
    -- Applicability
    applies_to TEXT[], -- Array of component types
    languages TEXT[],
    frameworks TEXT[],
    
    -- Usage guidance
    when_to_use TEXT NOT NULL,
    when_not_to_use TEXT,
    benefits TEXT[],
    trade_offs TEXT[],
    
    -- Code examples (JSONB for flexibility)
    code_examples JSONB,
    anti_patterns TEXT[],
    
    -- Relationships
    requires TEXT[], -- Pattern IDs
    conflicts_with TEXT[],
    related_patterns TEXT[],
    
    -- Metadata
    source VARCHAR(500),
    confidence FLOAT,
    
    -- Performance tracking
    success_rate FLOAT DEFAULT NULL,
    usage_count INTEGER DEFAULT 0,
    last_used TIMESTAMP,
    
    -- Vector embedding for semantic search
    embedding vector(1536), -- Adjust dimension based on embedding model
    
    -- Timestamps
    created_at TIMESTAMP DEFAULT NOW(),
    updated_at TIMESTAMP DEFAULT NOW()
);

-- Create indexes
CREATE INDEX idx_patterns_category ON patterns(category);
CREATE INDEX idx_patterns_scope ON patterns(scope);
CREATE INDEX idx_patterns_languages ON patterns USING GIN(languages);
CREATE INDEX idx_patterns_applies_to ON patterns USING GIN(applies_to);
CREATE INDEX idx_patterns_embedding ON patterns USING ivfflat (embedding vector_cosine_ops);

-- Rules table
CREATE TABLE rules (
    rule_id VARCHAR(50) PRIMARY KEY,
    
    rule_text TEXT NOT NULL,
    category VARCHAR(100) NOT NULL,
    severity VARCHAR(50) NOT NULL, -- critical, important, recommended
    
    applies_to TEXT[],
    rationale TEXT NOT NULL,
    
    examples JSONB, -- {good: "...", bad: "..."}
    validation_method VARCHAR(100), -- static_analysis, ai_review, manual
    
    source VARCHAR(500),
    
    -- Vector embedding
    embedding vector(1536),
    
    -- Timestamps
    created_at TIMESTAMP DEFAULT NOW(),
    updated_at TIMESTAMP DEFAULT NOW()
);

CREATE INDEX idx_rules_category ON rules(category);
CREATE INDEX idx_rules_severity ON rules(severity);
CREATE INDEX idx_rules_embedding ON rules USING ivfflat (embedding vector_cosine_ops);

-- Projects table (for project-specific context)
CREATE TABLE projects (
    project_id VARCHAR(100) PRIMARY KEY,
    project_name VARCHAR(200) NOT NULL,
    project_root VARCHAR(500),
    
    -- Tech stack
    tech_stack TEXT[],
    languages JSONB, -- {language: file_count}
    frameworks TEXT[],
    
    -- Architecture
    architecture_style VARCHAR(100),
    primary_patterns TEXT[], -- Pattern IDs used in this project
    
    -- Custom naming conventions
    naming_conventions JSONB,
    
    -- Metadata
    file_count INTEGER,
    last_analyzed TIMESTAMP,
    
    created_at TIMESTAMP DEFAULT NOW(),
    updated_at TIMESTAMP DEFAULT NOW()
);

-- Pattern usage tracking
CREATE TABLE pattern_usage (
    usage_id SERIAL PRIMARY KEY,
    pattern_id VARCHAR(50) REFERENCES patterns(pattern_id),
    project_id VARCHAR(100) REFERENCES projects(project_id),
    
    task_type VARCHAR(100),
    task_description TEXT,
    
    -- Outcome
    success BOOLEAN,
    code_quality_score FLOAT, -- From review
    iterations_needed INTEGER,
    
    -- Context
    model_used VARCHAR(100),
    timestamp TIMESTAMP DEFAULT NOW()
);

CREATE INDEX idx_usage_pattern ON pattern_usage(pattern_id);
CREATE INDEX idx_usage_project ON pattern_usage(project_id);
CREATE INDEX idx_usage_timestamp ON pattern_usage(timestamp);

-- Task analysis cache (for performance)
CREATE TABLE task_analysis_cache (
    cache_id SERIAL PRIMARY KEY,
    task_hash VARCHAR(64) UNIQUE, -- SHA256 of task description
    
    task_type VARCHAR(100),
    components TEXT[],
    concerns TEXT[],
    
    relevant_pattern_ids TEXT[],
    
    created_at TIMESTAMP DEFAULT NOW()
);

CREATE INDEX idx_task_cache_hash ON task_analysis_cache(task_hash);
```

---

## Component Specifications

### Component 1: Task Analysis Engine

**Purpose**: Analyze incoming task to understand what needs to be done.

```python
# core/task_analyzer.py

from typing import Dict, List
from dataclasses import dataclass
import anthropic
import hashlib
import json

@dataclass
class TaskContext:
    """Structured representation of a task."""
    
    task_description: str
    task_type: str  # "api_endpoint", "data_access", "authentication", etc.
    components: List[str]  # ["controller", "service", "repository"]
    concerns: List[str]  # ["security", "validation", "error_handling"]
    language: str
    framework: str

class TaskAnalyzer:
    """
    Analyzes tasks to understand requirements and context.
    """
    
    def __init__(self, anthropic_api_key: str, db_connection):
        self.client = anthropic.Anthropic(api_key=anthropic_api_key)
        self.db = db_connection
    
    def analyze_task(
        self, 
        task_description: str,
        project_context: Dict
    ) -> TaskContext:
        """
        Analyze a task and return structured context.
        """
        
        # Check cache first
        task_hash = self._hash_task(task_description)
        cached = self._check_cache(task_hash)
        
        if cached:
            return self._cached_to_context(cached, task_description)
        
        # Perform analysis
        analysis = self._analyze_with_llm(task_description, project_context)
        
        # Cache result
        self._cache_analysis(task_hash, analysis)
        
        return analysis
    
    def _analyze_with_llm(
        self, 
        task_description: str,
        project_context: Dict
    ) -> TaskContext:
        """
        Use LLM to analyze task.
        """
        
        prompt = f"""Analyze this software development task and extract structured information.

**Task**: {task_description}

**Project Context**:
- Language: {project_context.get('language', 'unknown')}
- Framework: {project_context.get('framework', 'unknown')}
- Architecture: {project_context.get('architecture_style', 'unknown')}

**Your task**: Classify and extract:

1. **Task Type**: What kind of task is this?
   - Options: api_endpoint, data_access, authentication, authorization, business_logic, 
     ui_component, background_job, integration, migration, refactoring, bug_fix, testing

2. **Components Involved**: What components/layers will be affected?
   - Examples: controller, service, repository, model, view, middleware, client, etc.

3. **Concerns**: What cross-cutting concerns apply?
   - Examples: security, validation, error_handling, logging, performance, caching, 
     transactions, async, testing, documentation

Return ONLY JSON:
```json
{{
  "task_type": "...",
  "components": ["...", "..."],
  "concerns": ["...", "..."]
}}
```"""
        
        response = self.client.messages.create(
            model="claude-sonnet-4-20250514",
            max_tokens=500,
            messages=[{"role": "user", "content": prompt}]
        )
        
        # Parse response
        response_text = response.content[0].text
        if "```json" in response_text:
            json_text = response_text.split("```json")[1].split("```")[0].strip()
        else:
            json_text = response_text.strip()
        
        analysis = json.loads(json_text)
        
        return TaskContext(
            task_description=task_description,
            task_type=analysis['task_type'],
            components=analysis['components'],
            concerns=analysis['concerns'],
            language=project_context.get('language', 'unknown'),
            framework=project_context.get('framework', 'unknown')
        )
    
    def _hash_task(self, task_description: str) -> str:
        """Create hash of task for caching."""
        return hashlib.sha256(task_description.encode()).hexdigest()
    
    def _check_cache(self, task_hash: str) -> Dict:
        """Check if task analysis is cached."""
        cursor = self.db.cursor()
        cursor.execute(
            "SELECT * FROM task_analysis_cache WHERE task_hash = %s",
            (task_hash,)
        )
        return cursor.fetchone()
    
    def _cache_analysis(self, task_hash: str, analysis: TaskContext):
        """Cache task analysis."""
        cursor = self.db.cursor()
        cursor.execute("""
            INSERT INTO task_analysis_cache 
            (task_hash, task_type, components, concerns)
            VALUES (%s, %s, %s, %s)
            ON CONFLICT (task_hash) DO NOTHING
        """, (
            task_hash,
            analysis.task_type,
            analysis.components,
            analysis.concerns
        ))
        self.db.commit()
```

---

### Component 2: Pattern Retrieval Engine

**Purpose**: Retrieve relevant patterns using semantic search.

```python
# core/pattern_retriever.py

from typing import List, Dict
import numpy as np
from dataclasses import dataclass

@dataclass
class RetrievedPattern:
    """Pattern with relevance score."""
    
    pattern_id: str
    title: str
    description: str
    category: str
    
    # Full pattern data
    pattern_data: Dict
    
    # Relevance
    relevance_score: float
    retrieval_reason: str

class PatternRetriever:
    """
    Retrieves relevant patterns using semantic search.
    """
    
    def __init__(self, db_connection, embedding_generator):
        self.db = db_connection
        self.embedding_gen = embedding_generator
    
    def retrieve_patterns(
        self,
        task_context: 'TaskContext',
        project_context: Dict,
        top_k: int = 7
    ) -> List[RetrievedPattern]:
        """
        Retrieve top-k most relevant patterns for a task.
        """
        
        # Build search query
        query_text = self._build_search_query(task_context)
        
        # Generate query embedding
        query_embedding = self.embedding_gen.generate_embedding(query_text)
        
        # Semantic search
        candidates = self._vector_search(query_embedding, top_k=20)
        
        # Filter by project context
        applicable = self._filter_by_context(candidates, project_context, task_context)
        
        # Rank by relevance and success rate
        ranked = self._rank_patterns(applicable, task_context)
        
        # Return top K
        return ranked[:top_k]
    
    def _build_search_query(self, task_context: 'TaskContext') -> str:
        """
        Build semantic search query from task context.
        """
        parts = [
            f"Task type: {task_context.task_type}",
            f"Components: {', '.join(task_context.components)}",
            f"Concerns: {', '.join(task_context.concerns)}",
            f"Language: {task_context.language}",
            f"Framework: {task_context.framework}"
        ]
        return " | ".join(parts)
    
    def _vector_search(
        self,
        query_embedding: List[float],
        top_k: int
    ) -> List[Dict]:
        """
        Perform vector similarity search in database.
        """
        cursor = self.db.cursor()
        
        # Use pgvector's cosine similarity
        cursor.execute("""
            SELECT 
                pattern_id,
                title,
                description,
                category,
                scope,
                applies_to,
                languages,
                frameworks,
                when_to_use,
                when_not_to_use,
                benefits,
                trade_offs,
                code_examples,
                anti_patterns,
                requires,
                conflicts_with,
                related_patterns,
                success_rate,
                usage_count,
                1 - (embedding <=> %s::vector) as similarity
            FROM patterns
            ORDER BY embedding <=> %s::vector
            LIMIT %s
        """, (query_embedding, query_embedding, top_k))
        
        columns = [desc[0] for desc in cursor.description]
        results = []
        
        for row in cursor.fetchall():
            result = dict(zip(columns, row))
            results.append(result)
        
        return results
    
    def _filter_by_context(
        self,
        candidates: List[Dict],
        project_context: Dict,
        task_context: 'TaskContext'
    ) -> List[Dict]:
        """
        Filter patterns by project and task context.
        """
        filtered = []
        
        project_lang = project_context.get('language')
        project_framework = project_context.get('framework')
        
        for pattern in candidates:
            # Check if pattern applies to this language
            if project_lang and pattern['languages']:
                if project_lang not in pattern['languages']:
                    continue
            
            # Check if pattern applies to this framework
            if project_framework and pattern['frameworks']:
                if project_framework not in pattern['frameworks']:
                    continue
            
            # Check if pattern applies to task components
            pattern_applies_to = set(pattern['applies_to'] or [])
            task_components = set(task_context.components)
            
            if pattern_applies_to and task_components:
                if not pattern_applies_to.intersection(task_components):
                    continue
            
            filtered.append(pattern)
        
        return filtered
    
    def _rank_patterns(
        self,
        patterns: List[Dict],
        task_context: 'TaskContext'
    ) -> List[RetrievedPattern]:
        """
        Rank patterns by relevance and success rate.
        """
        scored_patterns = []
        
        for pattern in patterns:
            # Combined score: similarity * success_rate (if available)
            base_score = pattern['similarity']
            
            success_rate = pattern.get('success_rate', 0.5)
            if success_rate is None:
                success_rate = 0.5  # Neutral for new patterns
            
            # Boost score if success rate is high
            final_score = base_score * (0.7 + 0.3 * success_rate)
            
            # Boost if pattern was recently successful
            if pattern.get('usage_count', 0) > 10 and success_rate > 0.8:
                final_score *= 1.1
            
            retrieved_pattern = RetrievedPattern(
                pattern_id=pattern['pattern_id'],
                title=pattern['title'],
                description=pattern['description'],
                category=pattern['category'],
                pattern_data=pattern,
                relevance_score=final_score,
                retrieval_reason=f"Similarity: {base_score:.2f}, Success rate: {success_rate:.2f}"
            )
            
            scored_patterns.append(retrieved_pattern)
        
        # Sort by final score
        scored_patterns.sort(key=lambda x: x.relevance_score, reverse=True)
        
        return scored_patterns
```

---

### Component 3: Pattern Application Reasoner

**Purpose**: Generate step-by-step guides for applying patterns.

```python
# core/application_reasoner.py

from typing import List, Dict
from dataclasses import dataclass
import anthropic
import json

@dataclass
class ApplicationStep:
    """Single step in pattern application."""
    
    step_number: int
    action: str
    pattern_id: str
    pattern_title: str
    rationale: str
    code_template: str
    validation_check: str

@dataclass
class ApplicationGuide:
    """Complete guide for applying patterns to a task."""
    
    task_description: str
    steps: List[ApplicationStep]
    anti_patterns: List[str]
    validation_checklist: List[str]
    estimated_complexity: str  # "simple", "medium", "complex"

class PatternApplicationReasoner:
    """
    Generates step-by-step guides for applying patterns.
    """
    
    def __init__(self, anthropic_api_key: str):
        self.client = anthropic.Anthropic(api_key=anthropic_api_key)
    
    def create_application_guide(
        self,
        task_context: 'TaskContext',
        retrieved_patterns: List['RetrievedPattern'],
        project_context: Dict
    ) -> ApplicationGuide:
        """
        Create a step-by-step guide for applying patterns.
        """
        
        prompt = self._build_reasoner_prompt(
            task_context,
            retrieved_patterns,
            project_context
        )
        
        response = self.client.messages.create(
            model="claude-sonnet-4-20250514",
            max_tokens=3000,
            messages=[{"role": "user", "content": prompt}]
        )
        
        # Parse response
        response_text = response.content[0].text
        if "```json" in response_text:
            json_text = response_text.split("```json")[1].split("```")[0].strip()
        else:
            json_text = response_text.strip()
        
        guide_data = json.loads(json_text)
        
        # Convert to structured objects
        steps = [
            ApplicationStep(**step)
            for step in guide_data['steps']
        ]
        
        guide = ApplicationGuide(
            task_description=task_context.task_description,
            steps=steps,
            anti_patterns=guide_data['anti_patterns'],
            validation_checklist=guide_data['validation_checklist'],
            estimated_complexity=guide_data['estimated_complexity']
        )
        
        return guide
    
    def _build_reasoner_prompt(
        self,
        task_context: 'TaskContext',
        patterns: List['RetrievedPattern'],
        project_context: Dict
    ) -> str:
        """
        Build prompt for generating application guide.
        """
        
        # Format patterns for prompt
        patterns_text = ""
        for i, pattern in enumerate(patterns, 1):
            data = pattern.pattern_data
            patterns_text += f"""
Pattern {i}: {pattern.title}
- ID: {pattern.pattern_id}
- Category: {pattern.category}
- Description: {pattern.description}
- When to use: {data.get('when_to_use', 'N/A')}
- Benefits: {', '.join(data.get('benefits', []))}
- Requires: {', '.join(data.get('requires', []) or [])}
- Code example:
{json.dumps(data.get('code_examples', {}), indent=2)}

"""
        
        return f"""You are an expert software architect creating a step-by-step implementation guide.

**Task**: {task_context.task_description}

**Task Context**:
- Type: {task_context.task_type}
- Components: {', '.join(task_context.components)}
- Concerns: {', '.join(task_context.concerns)}
- Language: {task_context.language}
- Framework: {task_context.framework}

**Project Context**:
- Architecture: {project_context.get('architecture_style', 'unknown')}
- Primary patterns: {', '.join(project_context.get('primary_patterns', []))}

**Relevant Patterns**:
{patterns_text}

**Your Task**: Create a step-by-step implementation guide that:
1. Combines these patterns coherently
2. Provides specific code templates for each step
3. Explains WHY each step matters
4. Lists anti-patterns to avoid
5. Provides validation checklist

Return ONLY JSON with this structure:

```json
{{
  "steps": [
    {{
      "step_number": 1,
      "action": "Create the repository interface",
      "pattern_id": "PAT-00123",
      "pattern_title": "Repository Pattern",
      "rationale": "Separates data access from business logic, enabling testability",
      "code_template": "public interface IUserRepository {{ ... }}",
      "validation_check": "Repository interface defined with clear methods"
    }}
  ],
  "anti_patterns": [
    "Don't access database directly from controllers",
    "Don't return null - use Result<T> pattern"
  ],
  "validation_checklist": [
    "All dependencies injected via constructor",
    "Proper error handling in place",
    "Unit tests written"
  ],
  "estimated_complexity": "medium"
}}
```

Generate 3-7 steps depending on task complexity. Be specific and actionable."""
        
        return prompt
```

I'll continue building out the architecture document. Let me create the remaining critical components.

<function_calls>
<invoke name="str_replace">
<parameter name="description">Adding the Multi-Agent Orchestrator component and verification system to the architecture document
## Implementation Phases

### Phase 1: Foundation (Weeks 1-2)

**Goals:**
- Collect and migrate existing standards
- Set up database infrastructure
- Extract and store patterns with embeddings

**Deliverables:**
- Collected standards from all sources
- PostgreSQL database with pgvector
- ~100-500 patterns extracted and embedded
- Basic data validation

**Steps:**
1. Run data collection scripts
2. Set up PostgreSQL with pgvector extension
3. Run knowledge extraction on collected documents
4. Generate embeddings for all patterns
5. Load into database
6. Verify data quality

**Success Criteria:**
- All existing standards collected
- Database populated with structured patterns
- Vector search returns relevant results for test queries

---

### Phase 2: Core Retrieval Engine (Weeks 3-4)

**Goals:**
- Build task analyzer
- Implement semantic pattern retrieval
- Create application reasoner

**Deliverables:**
- Working task analysis with caching
- Semantic search returning top-K patterns
- Application guide generation

**Steps:**
1. Implement TaskAnalyzer with LLM-based classification
2. Build PatternRetriever with vector search
3. Add filtering by project context
4. Implement ranking algorithm
5. Build ApplicationReasoner for step-by-step guides
6. Test on 10-20 sample tasks

**Success Criteria:**
- Task analyzer correctly classifies 90%+ of tasks
- Retrieved patterns are relevant (manual review)
- Application guides are actionable and complete

---

### Phase 3: Multi-Agent Orchestration (Weeks 5-6)

**Goals:**
- Build multi-agent system with convergence guarantees
- Implement static + semantic verification
- Add oscillation detection

**Deliverables:**
- Working MultiAgentOrchestrator
- Static and semantic verification
- Oscillation detection and escalation

**Steps:**
1. Implement planning agent (expensive model)
2. Implement execution agent (cheap model + templates)
3. Build static verification (AST, linting, security scans)
4. Build semantic review agent
5. Add oscillation detection
6. Implement escalation to better models
7. Test convergence on 20+ tasks

**Success Criteria:**
- 80%+ of tasks converge within 3 iterations
- Static verification catches obvious errors
- Oscillation detection prevents infinite loops
- Escalation works when needed

---

### Phase 4: Learning System (Week 7)

**Goals:**
- Track pattern usage outcomes
- Update success rates
- Identify poorly performing patterns

**Deliverables:**
- Outcome recording system
- Automatic statistics updates
- Poor pattern identification

**Steps:**
1. Implement outcome recording
2. Build statistics calculation
3. Create deprecation detection
4. Add dashboard for monitoring
5. Test with historical data

**Success Criteria:**
- Pattern success rates update correctly
- Poor patterns are identified
- Ranking improves over time based on data

---

### Phase 5: Integration (Week 8)

**Goals:**
- Integrate with OpenCode
- Build API for external tools
- Create CLI tool

**Deliverables:**
- OpenCode plugin
- REST API
- CLI tool for testing

**Steps:**
1. Build OpenCode plugin interface
2. Implement request/response interception
3. Create REST API with FastAPI
4. Build simple CLI for testing
5. Document integration guides
6. Test end-to-end workflows

**Success Criteria:**
- OpenCode successfully uses PatternForge
- API works for external clients
- CLI can be used for testing and debugging

---

### Phase 6: Deployment & Monitoring (Week 9-10)

**Goals:**
- Deploy to production environment
- Set up monitoring and alerting
- Create operational runbooks

**Deliverables:**
- Production deployment
- Monitoring dashboards
- Operational documentation

**Steps:**
1. Set up production infrastructure
2. Deploy database and application
3. Configure monitoring (Prometheus, Grafana)
4. Set up logging (ELK stack)
5. Create alerting rules
6. Document operations
7. Train team on operations

**Success Criteria:**
- System runs in production
- Monitoring shows system health
- Team can operate and troubleshoot

---

## Conclusion

PatternForge solves the fundamental problem of AI coding agents not following standards by replacing context-dumping with intelligent retrieval, providing step-by-step application guides instead of just examples, detecting oscillation and escalating when needed, and continuously learning from outcomes to improve pattern rankings.

### Expected Outcomes

After 10 weeks of implementation:
- 80%+ of tasks converge within 3 iterations
- 50%+ reduction in model API costs  
- 30%+ reduction in code review time
- 90%+ standards compliance on first attempt
- Self-improving system that learns from usage

---

**Document Version**: 1.0  
**Last Updated**: 2025-02-15  
**Status**: Ready for Implementation

*This architecture document is designed to be implemented by AI agents as well as human developers.*
