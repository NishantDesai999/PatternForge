# PatternForge Architecture - Addendum: Feedback & Dynamic Pattern Learning

## Critical Addition: User-Directed Pattern Evolution

---

## Problem Statement

The base architecture retrieves 5-7 relevant patterns per task, but this misses critical functionality:

1. **User teaches new patterns in conversation**: "From now on, always use this approach"
2. **Patterns emerge organically**: User corrects agent repeatedly on same issue
3. **Real-time pattern promotion**: User says "make this a standard" mid-conversation
4. **Cross-project pattern evolution**: Pattern used in 3 projects should auto-promote
5. **Weight adjustment feedback**: Agent needs to know when pattern weights change

**The retrieval limit (5-7 patterns) is for SEARCH EFFICIENCY, not for what the agent follows.**

The agent should:
- Retrieve 5-7 patterns from semantic search
- PLUS any "must-follow" patterns (user-promoted, project-standard)
- PLUS patterns learned in current conversation session
- Get feedback when pattern weights change

---

## Architecture Additions

### Component 7: Conversational Pattern Capture

**Purpose**: Capture patterns as user teaches them during conversation.

```python
# core/conversational_pattern_capture.py

from typing import Dict, List, Optional
from dataclasses import dataclass
from enum import Enum
import anthropic

class PatternSource(Enum):
    USER_EXPLICIT = "user_explicit"  # "Always do X"
    USER_CORRECTION = "user_correction"  # "No, do it this way"
    AGENT_OBSERVATION = "agent_observation"  # Agent notices pattern
    CROSS_PROJECT_PROMOTION = "cross_project_promotion"  # Seen in multiple projects

@dataclass
class ConversationalPattern:
    """Pattern learned during conversation."""
    
    description: str
    trigger_phrases: List[str]  # What user said to trigger this
    code_example: str
    rationale: str
    
    source: PatternSource
    confidence: float
    
    # Context
    project_id: str
    conversation_id: str
    timestamp: str
    
    # Promotion status
    is_project_standard: bool = False
    is_global_standard: bool = False
    promotion_count: int = 0  # How many times user has reinforced this

class ConversationalPatternCapture:
    """
    Captures patterns as user teaches them during conversation.
    """
    
    def __init__(self, anthropic_api_key: str, db_connection):
        self.client = anthropic.Anthropic(api_key=anthropic_api_key)
        self.db = db_connection
        
        # Session memory - patterns learned in this conversation
        self.session_patterns: List[ConversationalPattern] = []
    
    def analyze_user_message(
        self,
        user_message: str,
        conversation_history: List[Dict],
        project_context: Dict
    ) -> Optional[ConversationalPattern]:
        """
        Analyze user message for pattern-teaching intent.
        
        Detects phrases like:
        - "Always do X"
        - "From now on..."
        - "Remember to..."
        - "Don't forget..."
        - "Make sure you..."
        - "The correct way is..."
        """
        
        # Check for pattern-teaching indicators
        teaching_indicators = [
            "always", "from now on", "remember", "make sure",
            "don't forget", "the correct way", "you should",
            "never", "make this a standard", "use this approach"
        ]
        
        message_lower = user_message.lower()
        has_teaching_intent = any(ind in message_lower for ind in teaching_indicators)
        
        if not has_teaching_intent:
            return None
        
        # Use LLM to extract the pattern
        pattern = self._extract_pattern_from_message(
            user_message,
            conversation_history,
            project_context
        )
        
        if pattern:
            # Add to session memory
            self.session_patterns.append(pattern)
            
            # Store in database
            self._store_conversational_pattern(pattern)
        
        return pattern
    
    def detect_correction_pattern(
        self,
        user_message: str,
        agent_previous_output: str,
        conversation_history: List[Dict],
        project_context: Dict
    ) -> Optional[ConversationalPattern]:
        """
        Detect when user is correcting agent's approach.
        
        Correction indicators:
        - "No, do it this way..."
        - "Actually, use..."
        - "That's wrong, the correct approach is..."
        - "Don't do X, do Y instead"
        """
        
        correction_indicators = [
            "no,", "actually", "instead", "don't do",
            "that's wrong", "incorrect", "not like that",
            "change it to", "use this instead"
        ]
        
        message_lower = user_message.lower()
        has_correction = any(ind in message_lower for ind in correction_indicators)
        
        if not has_correction:
            return None
        
        # Extract what was wrong and what's correct
        pattern = self._extract_correction_pattern(
            user_message,
            agent_previous_output,
            conversation_history,
            project_context
        )
        
        if pattern:
            self.session_patterns.append(pattern)
            self._store_conversational_pattern(pattern)
        
        return pattern
    
    def _extract_pattern_from_message(
        self,
        user_message: str,
        conversation_history: List[Dict],
        project_context: Dict
    ) -> Optional[ConversationalPattern]:
        """
        Use LLM to extract structured pattern from user's teaching.
        """
        
        # Build conversation context
        recent_history = conversation_history[-5:]  # Last 5 messages
        history_text = "\n".join([
            f"{msg['role']}: {msg['content']}"
            for msg in recent_history
        ])
        
        prompt = f"""The user is teaching the AI a new pattern or standard to follow.

**Conversation History**:
{history_text}

**User's Latest Message**:
{user_message}

**Project Context**:
- Language: {project_context.get('language')}
- Framework: {project_context.get('framework')}

**Your Task**: Extract the pattern the user wants the AI to follow.

Return ONLY JSON:
```json
{{
  "description": "Clear description of the pattern/rule",
  "trigger_phrases": ["always use X", "never do Y"],
  "code_example": "Code example if user provided one",
  "rationale": "Why this pattern should be followed",
  "applies_to": ["components this applies to"],
  "confidence": 0.0-1.0
}}
```

If no clear pattern, return: {{"pattern_found": false}}
"""
        
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
        result = json.loads(json_text)
        
        if result.get('pattern_found') == False:
            return None
        
        return ConversationalPattern(
            description=result['description'],
            trigger_phrases=result['trigger_phrases'],
            code_example=result.get('code_example', ''),
            rationale=result['rationale'],
            source=PatternSource.USER_EXPLICIT,
            confidence=result['confidence'],
            project_id=project_context.get('project_id', 'unknown'),
            conversation_id=project_context.get('conversation_id', 'unknown'),
            timestamp=self._timestamp()
        )
    
    def _extract_correction_pattern(
        self,
        user_message: str,
        agent_output: str,
        conversation_history: List[Dict],
        project_context: Dict
    ) -> Optional[ConversationalPattern]:
        """
        Extract pattern from user correction.
        """
        
        prompt = f"""The user is correcting the AI's approach.

**What the AI did**:
{agent_output}

**User's Correction**:
{user_message}

**Your Task**: Extract the pattern the user wants followed going forward.

Return ONLY JSON with the correct approach:
```json
{{
  "description": "The correct approach to use",
  "wrong_approach": "What the AI did wrong",
  "correct_approach": "What should be done instead",
  "code_example": "Corrected code example",
  "rationale": "Why the correct approach is better",
  "confidence": 0.0-1.0
}}
```
"""
        
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
        result = json.loads(json_text)
        
        return ConversationalPattern(
            description=result['description'],
            trigger_phrases=[result['wrong_approach']],
            code_example=result['code_example'],
            rationale=result['rationale'],
            source=PatternSource.USER_CORRECTION,
            confidence=result['confidence'],
            project_id=project_context.get('project_id', 'unknown'),
            conversation_id=project_context.get('conversation_id', 'unknown'),
            timestamp=self._timestamp(),
            promotion_count=1  # User explicitly corrected this
        )
    
    def get_session_patterns(self) -> List[ConversationalPattern]:
        """
        Get all patterns learned in current session.
        """
        return self.session_patterns
    
    def _store_conversational_pattern(self, pattern: ConversationalPattern):
        """
        Store conversational pattern in database.
        """
        cursor = self.db.cursor()
        
        # Check if similar pattern already exists
        existing = self._find_similar_pattern(pattern)
        
        if existing:
            # Increment promotion count
            cursor.execute("""
                UPDATE conversational_patterns
                SET promotion_count = promotion_count + 1,
                    updated_at = NOW()
                WHERE id = %s
            """, (existing['id'],))
        else:
            # Insert new pattern
            cursor.execute("""
                INSERT INTO conversational_patterns
                (description, code_example, rationale, source, confidence,
                 project_id, conversation_id, promotion_count)
                VALUES (%s, %s, %s, %s, %s, %s, %s, %s)
            """, (
                pattern.description,
                pattern.code_example,
                pattern.rationale,
                pattern.source.value,
                pattern.confidence,
                pattern.project_id,
                pattern.conversation_id,
                pattern.promotion_count
            ))
        
        self.db.commit()
    
    def _find_similar_pattern(self, pattern: ConversationalPattern) -> Optional[Dict]:
        """
        Check if similar pattern already exists.
        """
        # Use semantic similarity to find duplicates
        # Implementation similar to pattern retrieval
        pass
    
    def _timestamp(self) -> str:
        from datetime import datetime
        return datetime.utcnow().isoformat()
```

---

### Component 8: Pattern Promotion System

**Purpose**: Automatically promote patterns from conversational → project → global based on usage.

```python
# core/pattern_promotion.py

from typing import List, Dict
from enum import Enum

class PromotionLevel(Enum):
    CONVERSATIONAL = "conversational"  # Only in current session
    PROJECT_SPECIFIC = "project_specific"  # Used in one project
    PROJECT_STANDARD = "project_standard"  # Official project standard
    CROSS_PROJECT = "cross_project"  # Used in multiple projects
    GLOBAL_STANDARD = "global_standard"  # Company-wide standard

class PatternPromotionSystem:
    """
    Manages pattern promotion from conversational → project → global.
    """
    
    def __init__(self, db_connection):
        self.db = db_connection
    
    def evaluate_patterns_for_promotion(self):
        """
        Run periodically (e.g., daily) to evaluate patterns for promotion.
        """
        
        # 1. Conversational → Project-Specific
        self._promote_reinforced_conversational_patterns()
        
        # 2. Project-Specific → Project-Standard
        self._promote_successful_project_patterns()
        
        # 3. Project-Standard → Cross-Project
        self._detect_cross_project_patterns()
        
        # 4. Cross-Project → Global Standard
        self._promote_cross_project_to_global()
    
    def _promote_reinforced_conversational_patterns(self):
        """
        Promote conversational patterns that user reinforced multiple times.
        
        Rule: If user mentions/uses a conversational pattern 3+ times,
        promote it to project-specific.
        """
        
        cursor = self.db.cursor()
        
        cursor.execute("""
            SELECT id, description, project_id, promotion_count
            FROM conversational_patterns
            WHERE promotion_count >= 3
              AND NOT is_project_standard
        """)
        
        candidates = cursor.fetchall()
        
        for pattern_id, description, project_id, promotion_count in candidates:
            print(f"📈 Promoting conversational pattern to project-specific: {description}")
            
            # Convert to formal pattern
            self._convert_conversational_to_formal(
                pattern_id,
                promotion_level=PromotionLevel.PROJECT_SPECIFIC
            )
    
    def _promote_successful_project_patterns(self):
        """
        Promote project-specific patterns with high success rates to project standards.
        
        Rule: If pattern has >85% success rate and 20+ uses in a project,
        promote to project standard.
        """
        
        cursor = self.db.cursor()
        
        cursor.execute("""
            SELECT 
                p.pattern_id,
                p.title,
                p.project_id,
                COUNT(*) as usage_count,
                AVG(CASE WHEN pu.success THEN 1.0 ELSE 0.0 END) as success_rate
            FROM patterns p
            JOIN pattern_usage pu ON p.pattern_id = pu.pattern_id
            WHERE p.scope = 'project'
              AND p.is_project_standard = FALSE
            GROUP BY p.pattern_id, p.title, p.project_id
            HAVING COUNT(*) >= 20
              AND AVG(CASE WHEN pu.success THEN 1.0 ELSE 0.0 END) >= 0.85
        """)
        
        candidates = cursor.fetchall()
        
        for pattern_id, title, project_id, usage_count, success_rate in candidates:
            print(f"⭐ Promoting to project standard: {title} "
                  f"(success: {success_rate:.1%}, uses: {usage_count})")
            
            cursor.execute("""
                UPDATE patterns
                SET is_project_standard = TRUE,
                    updated_at = NOW()
                WHERE pattern_id = %s
            """, (pattern_id,))
        
        self.db.commit()
    
    def _detect_cross_project_patterns(self):
        """
        Detect patterns used successfully across multiple projects.
        
        Rule: If same pattern appears in 3+ projects with >80% success rate,
        mark as cross-project.
        """
        
        cursor = self.db.cursor()
        
        # Find patterns with similar descriptions across projects
        cursor.execute("""
            WITH pattern_similarities AS (
                SELECT 
                    p1.pattern_id as pattern_1,
                    p2.pattern_id as pattern_2,
                    p1.project_id as project_1,
                    p2.project_id as project_2,
                    1 - (p1.embedding <=> p2.embedding) as similarity
                FROM patterns p1
                JOIN patterns p2 ON p1.pattern_id < p2.pattern_id
                WHERE p1.project_id != p2.project_id
                  AND 1 - (p1.embedding <=> p2.embedding) > 0.9
            )
            SELECT 
                pattern_1,
                COUNT(DISTINCT project_2) as project_count
            FROM pattern_similarities
            GROUP BY pattern_1
            HAVING COUNT(DISTINCT project_2) >= 2
        """)
        
        cross_project_patterns = cursor.fetchall()
        
        for pattern_id, project_count in cross_project_patterns:
            print(f"🌐 Pattern used in {project_count + 1} projects: {pattern_id}")
            
            cursor.execute("""
                UPDATE patterns
                SET scope = 'cross_project',
                    updated_at = NOW()
                WHERE pattern_id = %s
            """, (pattern_id,))
        
        self.db.commit()
    
    def _promote_cross_project_to_global(self):
        """
        Promote cross-project patterns to global standards.
        
        Rule: If cross-project pattern has >90% success rate across all uses
        and >50 total uses, promote to global.
        """
        
        cursor = self.db.cursor()
        
        cursor.execute("""
            SELECT 
                p.pattern_id,
                p.title,
                COUNT(*) as total_uses,
                AVG(CASE WHEN pu.success THEN 1.0 ELSE 0.0 END) as success_rate,
                COUNT(DISTINCT pu.project_id) as project_count
            FROM patterns p
            JOIN pattern_usage pu ON p.pattern_id = pu.pattern_id
            WHERE p.scope = 'cross_project'
            GROUP BY p.pattern_id, p.title
            HAVING COUNT(*) >= 50
              AND AVG(CASE WHEN pu.success THEN 1.0 ELSE 0.0 END) >= 0.90
              AND COUNT(DISTINCT pu.project_id) >= 3
        """)
        
        global_candidates = cursor.fetchall()
        
        for pattern_id, title, total_uses, success_rate, project_count in global_candidates:
            print(f"🌟 Promoting to GLOBAL standard: {title}")
            print(f"   - Success rate: {success_rate:.1%}")
            print(f"   - Total uses: {total_uses}")
            print(f"   - Projects: {project_count}")
            
            cursor.execute("""
                UPDATE patterns
                SET scope = 'global',
                    is_global_standard = TRUE,
                    updated_at = NOW()
                WHERE pattern_id = %s
            """, (pattern_id,))
        
        self.db.commit()
    
    def manually_promote_pattern(
        self,
        pattern_id: str,
        promotion_level: PromotionLevel,
        promoted_by: str  # User ID
    ):
        """
        Allow user to manually promote a pattern.
        
        User says: "Make this a project standard" or "Add to global standards"
        """
        
        cursor = self.db.cursor()
        
        if promotion_level == PromotionLevel.PROJECT_STANDARD:
            cursor.execute("""
                UPDATE patterns
                SET is_project_standard = TRUE,
                    updated_at = NOW()
                WHERE pattern_id = %s
            """, (pattern_id,))
            
        elif promotion_level == PromotionLevel.GLOBAL_STANDARD:
            cursor.execute("""
                UPDATE patterns
                SET scope = 'global',
                    is_global_standard = TRUE,
                    updated_at = NOW()
                WHERE pattern_id = %s
            """, (pattern_id,))
        
        # Log manual promotion
        cursor.execute("""
            INSERT INTO pattern_promotions
            (pattern_id, promoted_by, from_level, to_level, promotion_type)
            VALUES (%s, %s, (SELECT scope FROM patterns WHERE pattern_id = %s), %s, 'manual')
        """, (pattern_id, promoted_by, pattern_id, promotion_level.value))
        
        self.db.commit()
        
        print(f"✅ Pattern {pattern_id} manually promoted to {promotion_level.value}")
    
    def _convert_conversational_to_formal(
        self,
        conversational_pattern_id: int,
        promotion_level: PromotionLevel
    ):
        """
        Convert conversational pattern to formal pattern entry.
        """
        
        cursor = self.db.cursor()
        
        # Get conversational pattern
        cursor.execute("""
            SELECT description, code_example, rationale, project_id
            FROM conversational_patterns
            WHERE id = %s
        """, (conversational_pattern_id,))
        
        conv_pattern = cursor.fetchone()
        
        # Create formal pattern
        # (Use LLM to structure it properly based on pattern schema)
        # Then insert into patterns table with appropriate scope
        
        pass  # Implementation omitted for brevity
```

---

### Component 9: Pattern Weight Adjustment & Feedback

**Purpose**: Agent receives real-time feedback when pattern weights change.

```python
# core/pattern_weight_system.py

from typing import List, Dict
from dataclasses import dataclass
from enum import Enum

class WeightChangeReason(Enum):
    USER_PROMOTION = "user_promotion"
    SUCCESS_RATE_INCREASE = "success_rate_increase"
    PROJECT_STANDARD = "project_standard"
    GLOBAL_STANDARD = "global_standard"
    RECENT_SUCCESS = "recent_success"
    USER_CORRECTION = "user_correction"

@dataclass
class WeightChange:
    """Represents a change in pattern weight."""
    
    pattern_id: str
    old_weight: float
    new_weight: float
    reason: WeightChangeReason
    description: str
    timestamp: str

class PatternWeightSystem:
    """
    Manages pattern weights and provides feedback to agents.
    """
    
    def __init__(self, db_connection):
        self.db = db_connection
        self.recent_changes: List[WeightChange] = []
    
    def calculate_pattern_weight(self, pattern: Dict) -> float:
        """
        Calculate effective weight for a pattern based on multiple factors.
        
        Base weight: 1.0
        Adjustments:
        - Is project standard: +2.0
        - Is global standard: +5.0
        - Success rate >90%: +0.5
        - Used in last 7 days: +0.3
        - User explicitly promoted: +1.0
        - Recently corrected by user: +1.5
        """
        
        weight = 1.0
        
        # Promotion level adjustments
        if pattern.get('is_global_standard'):
            weight += 5.0
        elif pattern.get('is_project_standard'):
            weight += 2.0
        
        # Success rate adjustment
        success_rate = pattern.get('success_rate', 0.5)
        if success_rate >= 0.9:
            weight += 0.5
        elif success_rate < 0.5:
            weight -= 0.3
        
        # Recency adjustment
        import datetime
        last_used = pattern.get('last_used')
        if last_used:
            days_since_use = (datetime.datetime.now() - last_used).days
            if days_since_use <= 7:
                weight += 0.3
        
        # Check for recent user promotion/correction
        cursor = self.db.cursor()
        cursor.execute("""
            SELECT COUNT(*) 
            FROM conversational_patterns
            WHERE pattern_id = %s
              AND source IN ('user_explicit', 'user_correction')
              AND timestamp > NOW() - INTERVAL '7 days'
        """, (pattern.get('pattern_id'),))
        
        recent_user_input = cursor.fetchone()[0]
        if recent_user_input > 0:
            weight += 1.5
        
        return weight
    
    def track_weight_change(
        self,
        pattern_id: str,
        old_weight: float,
        new_weight: float,
        reason: WeightChangeReason,
        description: str
    ):
        """
        Track weight changes for feedback to agents.
        """
        
        change = WeightChange(
            pattern_id=pattern_id,
            old_weight=old_weight,
            new_weight=new_weight,
            reason=reason,
            description=description,
            timestamp=self._timestamp()
        )
        
        self.recent_changes.append(change)
        
        # Store in database
        cursor = self.db.cursor()
        cursor.execute("""
            INSERT INTO pattern_weight_changes
            (pattern_id, old_weight, new_weight, reason, description)
            VALUES (%s, %s, %s, %s, %s)
        """, (
            pattern_id,
            old_weight,
            new_weight,
            reason.value,
            description
        ))
        self.db.commit()
    
    def get_weight_changes_for_agent(
        self,
        project_id: str,
        since: str  # ISO timestamp
    ) -> List[WeightChange]:
        """
        Get recent weight changes relevant to this agent/project.
        
        Agent calls this before generating code to know:
        "These patterns have become more/less important since you last ran"
        """
        
        cursor = self.db.cursor()
        cursor.execute("""
            SELECT 
                pwc.pattern_id,
                p.title,
                pwc.old_weight,
                pwc.new_weight,
                pwc.reason,
                pwc.description,
                pwc.timestamp
            FROM pattern_weight_changes pwc
            JOIN patterns p ON pwc.pattern_id = p.pattern_id
            WHERE pwc.timestamp > %s
              AND (p.project_id = %s OR p.scope = 'global')
            ORDER BY pwc.timestamp DESC
        """, (since, project_id))
        
        changes = []
        for row in cursor.fetchall():
            changes.append(WeightChange(
                pattern_id=row[0],
                old_weight=row[2],
                new_weight=row[3],
                reason=WeightChangeReason(row[4]),
                description=row[5],
                timestamp=row[6]
            ))
        
        return changes
    
    def _timestamp(self) -> str:
        from datetime import datetime
        return datetime.utcnow().isoformat()
```

---

### Component 10: Enhanced Pattern Retrieval with "Must-Follow" Patterns

**Purpose**: Extend pattern retrieval to include mandatory patterns beyond the 5-7 semantic search results.

```python
# core/enhanced_pattern_retriever.py

from typing import List, Dict
from dataclasses import dataclass

@dataclass
class PatternSet:
    """Complete set of patterns for a task."""
    
    # Semantic search results (5-7 patterns)
    retrieved_patterns: List['RetrievedPattern']
    
    # Must-follow patterns (always included)
    mandatory_patterns: List['RetrievedPattern']
    
    # Session patterns (learned in conversation)
    session_patterns: List['ConversationalPattern']
    
    # Recently promoted patterns
    promoted_patterns: List['RetrievedPattern']
    
    # Weight changes agent should know about
    weight_changes: List['WeightChange']

class EnhancedPatternRetriever:
    """
    Enhanced retrieval that includes semantic search + mandatory patterns.
    """
    
    def __init__(
        self,
        base_retriever: 'PatternRetriever',
        weight_system: 'PatternWeightSystem',
        pattern_capture: 'ConversationalPatternCapture',
        db_connection
    ):
        self.base_retriever = base_retriever
        self.weight_system = weight_system
        self.pattern_capture = pattern_capture
        self.db = db_connection
    
    def retrieve_complete_pattern_set(
        self,
        task_context: 'TaskContext',
        project_context: Dict,
        last_retrieval_time: str = None
    ) -> PatternSet:
        """
        Retrieve COMPLETE set of patterns for a task:
        1. Semantic search (5-7 patterns)
        2. Mandatory patterns (project/global standards)
        3. Session patterns (learned in conversation)
        4. Recently promoted patterns
        5. Weight change notifications
        """
        
        # 1. Standard semantic retrieval (5-7 patterns)
        retrieved = self.base_retriever.retrieve_patterns(
            task_context,
            project_context,
            top_k=7
        )
        
        # 2. Get mandatory patterns
        mandatory = self._get_mandatory_patterns(task_context, project_context)
        
        # 3. Get session patterns
        session = self.pattern_capture.get_session_patterns()
        
        # 4. Get recently promoted patterns (in last 24 hours)
        promoted = self._get_recently_promoted_patterns(project_context)
        
        # 5. Get weight changes since last retrieval
        weight_changes = []
        if last_retrieval_time:
            weight_changes = self.weight_system.get_weight_changes_for_agent(
                project_context.get('project_id'),
                last_retrieval_time
            )
        
        return PatternSet(
            retrieved_patterns=retrieved,
            mandatory_patterns=mandatory,
            session_patterns=session,
            promoted_patterns=promoted,
            weight_changes=weight_changes
        )
    
    def _get_mandatory_patterns(
        self,
        task_context: 'TaskContext',
        project_context: Dict
    ) -> List['RetrievedPattern']:
        """
        Get patterns that MUST be followed (regardless of semantic search).
        
        Includes:
        - Global standards
        - Project standards
        - User-promoted patterns for this project
        """
        
        cursor = self.db.cursor()
        
        project_id = project_context.get('project_id')
        
        # Query mandatory patterns
        cursor.execute("""
            SELECT 
                pattern_id, title, description, category,
                applies_to, code_examples, when_to_use,
                'mandatory' as relevance_score
            FROM patterns
            WHERE (is_global_standard = TRUE 
                   OR (is_project_standard = TRUE AND project_id = %s))
              AND (
                  %s = ANY(applies_to)  -- Applies to task components
                  OR applies_to IS NULL  -- Universal pattern
              )
        """, (project_id, task_context.task_type))
        
        mandatory_patterns = []
        
        for row in cursor.fetchall():
            pattern = RetrievedPattern(
                pattern_id=row[0],
                title=row[1],
                description=row[2],
                category=row[3],
                pattern_data={
                    'applies_to': row[4],
                    'code_examples': row[5],
                    'when_to_use': row[6]
                },
                relevance_score=1.0,  # Mandatory = max relevance
                retrieval_reason="Mandatory pattern (project/global standard)"
            )
            mandatory_patterns.append(pattern)
        
        return mandatory_patterns
    
    def _get_recently_promoted_patterns(
        self,
        project_context: Dict
    ) -> List['RetrievedPattern']:
        """
        Get patterns that were recently promoted (last 24 hours).
        
        Agent should know: "Hey, this pattern was just promoted to standard"
        """
        
        cursor = self.db.cursor()
        
        cursor.execute("""
            SELECT 
                p.pattern_id,
                p.title,
                p.description,
                pp.to_level,
                pp.timestamp
            FROM patterns p
            JOIN pattern_promotions pp ON p.pattern_id = pp.pattern_id
            WHERE pp.timestamp > NOW() - INTERVAL '24 hours'
              AND (p.project_id = %s OR p.scope = 'global')
        """, (project_context.get('project_id'),))
        
        promoted = []
        
        for row in cursor.fetchall():
            pattern = RetrievedPattern(
                pattern_id=row[0],
                title=row[1],
                description=row[2],
                category="recent_promotion",
                pattern_data={'promotion_level': row[3]},
                relevance_score=0.9,
                retrieval_reason=f"Recently promoted to {row[3]} ({row[4]})"
            )
            promoted.append(pattern)
        
        return promoted
```

---

### Modified Agent Prompt with Pattern Feedback

```python
# core/agent_with_feedback.py

class AgentWithPatternFeedback:
    """
    Agent that receives feedback about pattern changes.
    """
    
    def build_agent_prompt_with_feedback(
        self,
        task_description: str,
        pattern_set: PatternSet
    ) -> str:
        """
        Build agent prompt that includes:
        1. Retrieved patterns (semantic search)
        2. Mandatory patterns (MUST follow)
        3. Session patterns (learned in conversation)
        4. Weight change notifications
        """
        
        prompt = f"""Task: {task_description}

---

## MANDATORY PATTERNS (MUST FOLLOW)

These are project/global standards that MUST be followed:

"""
        
        for i, pattern in enumerate(pattern_set.mandatory_patterns, 1):
            prompt += f"""
{i}. **{pattern.title}** [MANDATORY]
   - {pattern.description}
   - When to use: {pattern.pattern_data.get('when_to_use')}
   - Code example: {pattern.pattern_data.get('code_examples')}
"""
        
        prompt += """

---

## RELEVANT PATTERNS (RECOMMENDED)

These patterns are relevant to your task:

"""
        
        for i, pattern in enumerate(pattern_set.retrieved_patterns, 1):
            prompt += f"""
{i}. **{pattern.title}** (Relevance: {pattern.relevance_score:.2f})
   - {pattern.description}
"""
        
        if pattern_set.session_patterns:
            prompt += """

---

## LEARNED IN THIS CONVERSATION

You've learned these patterns during our conversation:

"""
            for i, pattern in enumerate(pattern_set.session_patterns, 1):
                prompt += f"""
{i}. {pattern.description}
   - Code example: {pattern.code_example}
   - Rationale: {pattern.rationale}
"""
        
        if pattern_set.weight_changes:
            prompt += """

---

## PATTERN UPDATES

These patterns have changed since you last ran:

"""
            for change in pattern_set.weight_changes:
                if change.new_weight > change.old_weight:
                    direction = "↑ MORE IMPORTANT"
                else:
                    direction = "↓ LESS IMPORTANT"
                
                prompt += f"""
- {change.pattern_id}: {direction}
  Reason: {change.description}
"""
        
        prompt += """

---

**Your Task**: Generate code that follows ALL mandatory patterns and applies relevant patterns where appropriate.

If you're unsure about a pattern, ASK rather than guess.
"""
        
        return prompt
```

---

## Database Schema Additions

```sql
-- Add to schema.sql

-- Conversational patterns table
CREATE TABLE conversational_patterns (
    id SERIAL PRIMARY KEY,
    description TEXT NOT NULL,
    code_example TEXT,
    rationale TEXT,
    
    source VARCHAR(50), -- user_explicit, user_correction, etc.
    confidence FLOAT,
    
    project_id VARCHAR(100),
    conversation_id VARCHAR(200),
    
    -- Promotion tracking
    promotion_count INTEGER DEFAULT 1,
    is_project_standard BOOLEAN DEFAULT FALSE,
    is_global_standard BOOLEAN DEFAULT FALSE,
    
    -- Formal pattern link (when converted)
    formal_pattern_id VARCHAR(50) REFERENCES patterns(pattern_id),
    
    timestamp TIMESTAMP DEFAULT NOW(),
    updated_at TIMESTAMP DEFAULT NOW()
);

CREATE INDEX idx_conv_patterns_project ON conversational_patterns(project_id);
CREATE INDEX idx_conv_patterns_conversation ON conversational_patterns(conversation_id);
CREATE INDEX idx_conv_patterns_promotion ON conversational_patterns(promotion_count);

-- Pattern promotions log
CREATE TABLE pattern_promotions (
    id SERIAL PRIMARY KEY,
    pattern_id VARCHAR(50) REFERENCES patterns(pattern_id),
    
    promoted_by VARCHAR(200), -- User ID or 'system'
    from_level VARCHAR(50),
    to_level VARCHAR(50),
    promotion_type VARCHAR(50), -- manual, automatic
    
    timestamp TIMESTAMP DEFAULT NOW()
);

CREATE INDEX idx_promotions_pattern ON pattern_promotions(pattern_id);
CREATE INDEX idx_promotions_timestamp ON pattern_promotions(timestamp);

-- Pattern weight changes log
CREATE TABLE pattern_weight_changes (
    id SERIAL PRIMARY KEY,
    pattern_id VARCHAR(50) REFERENCES patterns(pattern_id),
    
    old_weight FLOAT,
    new_weight FLOAT,
    reason VARCHAR(100),
    description TEXT,
    
    timestamp TIMESTAMP DEFAULT NOW()
);

CREATE INDEX idx_weight_changes_pattern ON pattern_weight_changes(pattern_id);
CREATE INDEX idx_weight_changes_timestamp ON pattern_weight_changes(timestamp);

-- Add columns to patterns table
ALTER TABLE patterns ADD COLUMN is_project_standard BOOLEAN DEFAULT FALSE;
ALTER TABLE patterns ADD COLUMN is_global_standard BOOLEAN DEFAULT FALSE;
ALTER TABLE patterns ADD COLUMN effective_weight FLOAT DEFAULT 1.0;

CREATE INDEX idx_patterns_project_standard ON patterns(is_project_standard) WHERE is_project_standard = TRUE;
CREATE INDEX idx_patterns_global_standard ON patterns(is_global_standard) WHERE is_global_standard = TRUE;
```

---

## Summary: How This Solves Your Problem

### Before (The Problem):
- Agent gets 5-7 patterns from semantic search
- User teaches patterns in conversation → ignored next time
- Patterns emerge across projects → never promoted
- Agent doesn't know when standards change
- "Always do X" → forgotten after conversation ends

### After (The Solution):

**1. User Teaches → System Learns**
```
User: "From now on, always use Result<T> for error handling"
System: ✅ Captured as conversational pattern
        ✅ Applied immediately in this conversation
        ✅ Stored in database
        ✅ Promoted if reinforced 3+ times
```

**2. Patterns Promoted Automatically**
```
Conversational (1 use) 
  → Project-Specific (3+ reinforcements)
  → Project-Standard (85% success, 20+ uses)
  → Cross-Project (3+ projects)
  → Global Standard (90% success, 50+ uses)
```

**3. Agent Receives Complete Context**
```
Semantic Search: 5-7 relevant patterns
+ Mandatory: All project/global standards (regardless of search)
+ Session: Patterns learned in THIS conversation
+ Promoted: Recently promoted patterns (last 24h)
+ Feedback: "Pattern X weight increased because..."
```

**4. Real-Time Weight Adjustments**
```
User corrects agent → Pattern weight ↑ immediately
Pattern succeeds → Weight ↑ gradually
Pattern fails → Weight ↓ gradually
Promoted to standard → Weight ↑↑ significantly
```

---

## Usage Example

```python
# When agent starts a task

retriever = EnhancedPatternRetriever(...)

# Get COMPLETE pattern set
pattern_set = retriever.retrieve_complete_pattern_set(
    task_context=task,
    project_context=project,
    last_retrieval_time="2025-02-15T10:00:00Z"
)

# Agent now knows:
print(f"Semantic search: {len(pattern_set.retrieved_patterns)} patterns")
print(f"Mandatory (MUST follow): {len(pattern_set.mandatory_patterns)} patterns")
print(f"Learned in conversation: {len(pattern_set.session_patterns)} patterns")
print(f"Recently promoted: {len(pattern_set.promoted_patterns)} patterns")
print(f"Weight changes: {len(pattern_set.weight_changes)} notifications")

# Build prompt with ALL context
prompt = agent.build_agent_prompt_with_feedback(task_description, pattern_set)

# Agent generates code with complete knowledge
```

---

**This addendum solves the exact problem you identified: user-directed learning, dynamic pattern evolution, cross-project promotion, and real-time feedback loops.**
