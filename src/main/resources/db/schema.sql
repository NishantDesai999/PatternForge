-- ============================================
-- PatternForge Database Schema
-- ============================================
-- Version: 1.0.1
-- Date: 2026-02-24
-- Description: Complete database schema for PatternForge
-- Execution: psql -U postgres -d patternforge -f schema.sql
--
-- IMPORTANT: This is the SINGLE SOURCE OF TRUTH for database schema
-- All changes should be made to this file using ALTER statements
-- with IF NOT EXISTS / IF EXISTS guards for idempotency
-- See SCHEMA MIGRATIONS section at end of file for all changes
-- ============================================

-- ============================================
-- SECTION 1: EXTENSIONS
-- ============================================

CREATE EXTENSION IF NOT EXISTS "uuid-ossp";
CREATE EXTENSION IF NOT EXISTS vector;

-- ============================================
-- SECTION 2: CORE TABLES
-- ============================================

-- Patterns Table (v1.0.0)
CREATE TABLE IF NOT EXISTS patterns (
    pattern_id UUID PRIMARY KEY DEFAULT uuid_generate_v4(),
    
    -- Identity
    pattern_name VARCHAR(200) UNIQUE NOT NULL,
    title VARCHAR(500) NOT NULL,
    description TEXT NOT NULL,
    category VARCHAR(100) NOT NULL,
    scope VARCHAR(50) DEFAULT 'project' CHECK (scope IN ('global', 'project', 'conversational')),
    
    -- Applicability
    applies_to TEXT[],
    languages TEXT[],
    frameworks TEXT[],
    
    -- Usage guidance
    when_to_use TEXT NOT NULL,
    when_not_to_use TEXT,
    benefits TEXT[],
    trade_offs TEXT[],
    
    -- Code examples (JSONB for multi-language support)
    code_examples JSONB DEFAULT '{}'::jsonb,
    anti_patterns TEXT[] DEFAULT ARRAY[]::TEXT[],
    
    -- Relationships
    requires TEXT[] DEFAULT ARRAY[]::TEXT[],
    conflicts_with TEXT[] DEFAULT ARRAY[]::TEXT[],
    related_patterns TEXT[] DEFAULT ARRAY[]::TEXT[],
    
    -- Metadata
    source VARCHAR(500),
    confidence FLOAT DEFAULT 0.95 CHECK (confidence >= 0 AND confidence <= 1),
    
    -- Performance tracking
    success_rate FLOAT CHECK (success_rate IS NULL OR (success_rate >= 0 AND success_rate <= 1)),
    usage_count INTEGER DEFAULT 0,
    last_used TIMESTAMP,
    
    -- Promotion tracking
    is_mandatory BOOLEAN DEFAULT FALSE,
    is_project_standard BOOLEAN DEFAULT FALSE,
    is_global_standard BOOLEAN DEFAULT FALSE,
    global_since DATE,
    
    -- Workflow association
    workflow_id UUID,
    
    -- Vector embedding (768 dims for nomic-embed-text)
    embedding vector(768),
    
    -- Timestamps
    created_at TIMESTAMP DEFAULT NOW(),
    updated_at TIMESTAMP DEFAULT NOW()
);

-- Indexes for patterns (v1.0.0)
CREATE INDEX IF NOT EXISTS idx_patterns_category ON patterns(category);
CREATE INDEX IF NOT EXISTS idx_patterns_scope ON patterns(scope);
CREATE INDEX IF NOT EXISTS idx_patterns_languages ON patterns USING GIN(languages);
CREATE INDEX IF NOT EXISTS idx_patterns_applies_to ON patterns USING GIN(applies_to);
CREATE INDEX IF NOT EXISTS idx_patterns_embedding ON patterns USING ivfflat (embedding vector_cosine_ops) WITH (lists = 100);
CREATE INDEX IF NOT EXISTS idx_patterns_mandatory ON patterns(is_mandatory) WHERE is_mandatory = TRUE;
CREATE INDEX IF NOT EXISTS idx_patterns_global ON patterns(is_global_standard) WHERE is_global_standard = TRUE;
CREATE INDEX IF NOT EXISTS idx_patterns_name ON patterns(pattern_name);

-- Workflow Steps Table (v1.0.0)
CREATE TABLE IF NOT EXISTS workflow_steps (
    workflow_id UUID PRIMARY KEY DEFAULT uuid_generate_v4(),
    workflow_name VARCHAR(200) NOT NULL,
    pattern_id UUID REFERENCES patterns(pattern_id) ON DELETE CASCADE,
    
    -- Detailed execution steps (JSONB array)
    steps JSONB NOT NULL DEFAULT '[]'::jsonb,
    
    -- Quality gates (JSONB)
    quality_gates JSONB DEFAULT '{}'::jsonb,
    
    created_at TIMESTAMP DEFAULT NOW(),
    updated_at TIMESTAMP DEFAULT NOW()
);

CREATE INDEX IF NOT EXISTS idx_workflow_pattern ON workflow_steps(pattern_id);
CREATE INDEX IF NOT EXISTS idx_workflow_name ON workflow_steps(workflow_name);

-- Workflow Templates Table (v1.0.0)
-- Stores user-defined workflows from .opencode/workflows/*.md
CREATE TABLE IF NOT EXISTS workflow_templates (
    template_id UUID PRIMARY KEY DEFAULT uuid_generate_v4(),
    
    workflow_name VARCHAR(200) NOT NULL,
    task_types TEXT[],
    languages TEXT[],
    frameworks TEXT[],
    
    -- Source tracking
    source_type VARCHAR(50) CHECK (source_type IN ('project', 'global', 'patternforge')),
    source_path TEXT,
    priority INTEGER DEFAULT 50,
    
    -- Workflow content (parsed from file)
    steps JSONB NOT NULL DEFAULT '[]'::jsonb,
    quality_gates JSONB DEFAULT '{}'::jsonb,
    
    -- Pattern references (IDs mentioned in workflow)
    referenced_patterns TEXT[] DEFAULT ARRAY[]::TEXT[],
    
    created_at TIMESTAMP DEFAULT NOW(),
    updated_at TIMESTAMP DEFAULT NOW()
);

CREATE INDEX IF NOT EXISTS idx_workflow_templates_task_types ON workflow_templates USING GIN(task_types);
CREATE INDEX IF NOT EXISTS idx_workflow_templates_priority ON workflow_templates(priority DESC);
CREATE INDEX IF NOT EXISTS idx_workflow_templates_source ON workflow_templates(source_type, source_path);

-- Rules Table (v1.0.0)
CREATE TABLE IF NOT EXISTS rules (
    rule_id UUID PRIMARY KEY DEFAULT uuid_generate_v4(),
    
    rule_text TEXT NOT NULL,
    category VARCHAR(100) NOT NULL,
    severity VARCHAR(50) NOT NULL CHECK (severity IN ('critical', 'important', 'recommended')),
    
    applies_to TEXT[],
    rationale TEXT NOT NULL,
    
    examples JSONB DEFAULT '{}'::jsonb,
    validation_method VARCHAR(100),
    
    source VARCHAR(500),
    embedding vector(768),
    
    created_at TIMESTAMP DEFAULT NOW(),
    updated_at TIMESTAMP DEFAULT NOW()
);

CREATE INDEX IF NOT EXISTS idx_rules_category ON rules(category);
CREATE INDEX IF NOT EXISTS idx_rules_severity ON rules(severity);
CREATE INDEX IF NOT EXISTS idx_rules_embedding ON rules USING ivfflat (embedding vector_cosine_ops);

-- Projects Table (v1.0.0)
CREATE TABLE IF NOT EXISTS projects (
    project_id UUID PRIMARY KEY DEFAULT uuid_generate_v4(),
    project_name VARCHAR(200) UNIQUE NOT NULL,
    project_path TEXT,
    
    -- Tech stack metadata
    tech_stack TEXT[],
    languages JSONB DEFAULT '{}'::jsonb,
    frameworks TEXT[],
    
    -- Architecture
    architecture_style VARCHAR(100),
    primary_patterns TEXT[],
    naming_conventions JSONB DEFAULT '{}'::jsonb,
    
    -- Metadata
    file_count INTEGER,
    last_analyzed TIMESTAMP,
    
    created_at TIMESTAMP DEFAULT NOW(),
    updated_at TIMESTAMP DEFAULT NOW()
);

CREATE INDEX IF NOT EXISTS idx_projects_name ON projects(project_name);
CREATE INDEX IF NOT EXISTS idx_projects_path ON projects(project_path);

-- Conversational Patterns Table (v1.0.0)
-- Stores patterns captured during agent-user interactions.
CREATE TABLE IF NOT EXISTS conversational_patterns (
    id UUID PRIMARY KEY DEFAULT uuid_generate_v4(),
    
    description TEXT NOT NULL,
    code_example TEXT,
    rationale TEXT,
    
    source VARCHAR(50) CHECK (source IN ('user_explicit', 'user_correction', 'agent_observation')),
    confidence FLOAT CHECK (confidence >= 0 AND confidence <= 1),
    
    project_id UUID REFERENCES projects(project_id) ON DELETE SET NULL,
    conversation_id VARCHAR(200),
    
    -- Promotion tracking
    promotion_count INTEGER DEFAULT 1,
    is_project_standard BOOLEAN DEFAULT FALSE,
    is_global_standard BOOLEAN DEFAULT FALSE,
    
    -- Link to formal pattern (when promoted)
    formal_pattern_id UUID REFERENCES patterns(pattern_id) ON DELETE SET NULL,
    
    timestamp TIMESTAMP DEFAULT NOW(),
    updated_at TIMESTAMP DEFAULT NOW()
);

CREATE INDEX IF NOT EXISTS idx_conv_patterns_project ON conversational_patterns(project_id);
CREATE INDEX IF NOT EXISTS idx_conv_patterns_promotion ON conversational_patterns(promotion_count);
CREATE INDEX IF NOT EXISTS idx_conv_patterns_conversation ON conversational_patterns(conversation_id);

-- Pattern Usage Table (v1.0.0)
CREATE TABLE IF NOT EXISTS pattern_usage (
    usage_id UUID PRIMARY KEY DEFAULT uuid_generate_v4(),
    
    pattern_id UUID REFERENCES patterns(pattern_id) ON DELETE CASCADE,
    project_id UUID REFERENCES projects(project_id) ON DELETE SET NULL,
    
    task_type VARCHAR(100),
    task_description TEXT,
    
    -- Outcome
    success BOOLEAN,
    code_quality_score FLOAT CHECK (code_quality_score IS NULL OR (code_quality_score >= 0 AND code_quality_score <= 1)),
    iterations_needed INTEGER,
    
    -- Context
    model_used VARCHAR(100),
    timestamp TIMESTAMP DEFAULT NOW()
);

CREATE INDEX IF NOT EXISTS idx_usage_pattern ON pattern_usage(pattern_id);
CREATE INDEX IF NOT EXISTS idx_usage_project ON pattern_usage(project_id);
CREATE INDEX IF NOT EXISTS idx_usage_timestamp ON pattern_usage(timestamp DESC);
CREATE INDEX IF NOT EXISTS idx_usage_success ON pattern_usage(success);

-- Pattern Promotions Table (v1.0.0)
CREATE TABLE IF NOT EXISTS pattern_promotions (
    id UUID PRIMARY KEY DEFAULT uuid_generate_v4(),
    pattern_id UUID REFERENCES patterns(pattern_id) ON DELETE CASCADE,
    
    promoted_by VARCHAR(200),
    from_level VARCHAR(50) CHECK (from_level IN ('conversational', 'project', 'cross_project')),
    to_level VARCHAR(50) CHECK (to_level IN ('project', 'cross_project', 'global')),
    promotion_type VARCHAR(50) CHECK (promotion_type IN ('manual', 'automatic')),
    
    -- User decision (for automatic promotions)
    user_prompted BOOLEAN DEFAULT FALSE,
    user_approved BOOLEAN,
    
    timestamp TIMESTAMP DEFAULT NOW()
);

CREATE INDEX IF NOT EXISTS idx_promotions_pattern ON pattern_promotions(pattern_id);
CREATE INDEX IF NOT EXISTS idx_promotions_timestamp ON pattern_promotions(timestamp DESC);

-- Quality Gates Table (v1.0.0)
CREATE TABLE IF NOT EXISTS quality_gates (
    gate_id UUID PRIMARY KEY DEFAULT uuid_generate_v4(),
    
    gate_name VARCHAR(200) NOT NULL UNIQUE,
    gate_type VARCHAR(50) CHECK (gate_type IN ('mandatory_review', 'user_approval', 'build_check', 'test_check')),
    
    validation_command TEXT,
    applies_to TEXT[],
    
    is_blocking BOOLEAN DEFAULT TRUE,
    description TEXT,
    
    created_at TIMESTAMP DEFAULT NOW()
);

-- Pattern Quality Gates Junction Table (v1.0.0)
CREATE TABLE IF NOT EXISTS pattern_quality_gates (
    pattern_id UUID REFERENCES patterns(pattern_id) ON DELETE CASCADE,
    gate_id UUID REFERENCES quality_gates(gate_id) ON DELETE CASCADE,
    PRIMARY KEY (pattern_id, gate_id)
);

-- ============================================
-- SECTION 3: SEED DATA
-- ============================================

-- Insert default quality gates
INSERT INTO quality_gates (gate_name, gate_type, validation_command, applies_to, is_blocking, description)
VALUES 
    ('code_review', 'mandatory_review', NULL, ARRAY['all'], TRUE, 'Code must be reviewed by code reviewer agent'),
    ('user_approval', 'user_approval', NULL, ARRAY['all'], TRUE, 'User must approve changes before application'),
    ('mvn_clean', 'build_check', 'mvn clean', ARRAY['java', 'maven'], FALSE, 'Maven clean build (for Lombok/jOOQ)'),
    ('mvn_test', 'test_check', 'mvn test', ARRAY['java', 'maven'], TRUE, 'Maven test execution'),
    ('npm_test', 'test_check', 'npm test', ARRAY['javascript', 'typescript'], TRUE, 'NPM test execution'),
    ('pytest', 'test_check', 'pytest', ARRAY['python'], TRUE, 'Pytest execution'),
    ('cargo_test', 'test_check', 'cargo test', ARRAY['rust'], TRUE, 'Cargo test execution'),
    ('go_test', 'test_check', 'go test ./...', ARRAY['go'], TRUE, 'Go test execution')
ON CONFLICT (gate_name) DO NOTHING;

-- ============================================
-- SECTION 4: HELPER FUNCTIONS
-- ============================================

-- Function to calculate pattern similarity
CREATE OR REPLACE FUNCTION calculate_pattern_similarity(
    query_embedding vector(768),
    pattern_embedding vector(768)
) RETURNS FLOAT AS $$
BEGIN
    IF query_embedding IS NULL OR pattern_embedding IS NULL THEN
        RETURN 0.0;
    END IF;
    RETURN 1 - (query_embedding <=> pattern_embedding);
END;
$$ LANGUAGE plpgsql IMMUTABLE;

-- Function to update pattern success rate
CREATE OR REPLACE FUNCTION update_pattern_success_rate(pattern_uuid UUID)
RETURNS VOID AS $$
DECLARE
    total_uses INTEGER;
    successful_uses INTEGER;
    new_success_rate FLOAT;
BEGIN
    SELECT COUNT(*), COUNT(*) FILTER (WHERE success = TRUE)
    INTO total_uses, successful_uses
    FROM pattern_usage
    WHERE pattern_id = pattern_uuid;
    
    IF total_uses > 0 THEN
        new_success_rate := successful_uses::FLOAT / total_uses;
        
        UPDATE patterns
        SET success_rate = new_success_rate,
            usage_count = total_uses,
            updated_at = NOW()
        WHERE pattern_id = pattern_uuid;
    END IF;
END;
$$ LANGUAGE plpgsql;

-- ============================================
-- SECTION 5: TRIGGERS
-- ============================================

-- Trigger to auto-update pattern success rate on usage insert
CREATE OR REPLACE FUNCTION trigger_update_pattern_success_rate()
RETURNS TRIGGER AS $$
BEGIN
    PERFORM update_pattern_success_rate(NEW.pattern_id);
    RETURN NEW;
END;
$$ LANGUAGE plpgsql;

DROP TRIGGER IF EXISTS trg_pattern_usage_update_success_rate ON pattern_usage;
CREATE TRIGGER trg_pattern_usage_update_success_rate
AFTER INSERT ON pattern_usage
FOR EACH ROW
EXECUTE FUNCTION trigger_update_pattern_success_rate();

-- Trigger to update updated_at timestamp
CREATE OR REPLACE FUNCTION trigger_set_updated_at()
RETURNS TRIGGER AS $$
BEGIN
    NEW.updated_at = NOW();
    RETURN NEW;
END;
$$ LANGUAGE plpgsql;

DROP TRIGGER IF EXISTS trg_patterns_updated_at ON patterns;
CREATE TRIGGER trg_patterns_updated_at
BEFORE UPDATE ON patterns
FOR EACH ROW
EXECUTE FUNCTION trigger_set_updated_at();

DROP TRIGGER IF EXISTS trg_workflow_steps_updated_at ON workflow_steps;
CREATE TRIGGER trg_workflow_steps_updated_at
BEFORE UPDATE ON workflow_steps
FOR EACH ROW
EXECUTE FUNCTION trigger_set_updated_at();

DROP TRIGGER IF EXISTS trg_workflow_templates_updated_at ON workflow_templates;
CREATE TRIGGER trg_workflow_templates_updated_at
BEFORE UPDATE ON workflow_templates
FOR EACH ROW
EXECUTE FUNCTION trigger_set_updated_at();

-- ============================================
-- SECTION 6: VIEWS (for convenience)
-- ============================================

-- View for active global patterns
CREATE OR REPLACE VIEW v_global_patterns AS
SELECT 
    pattern_id,
    pattern_name,
    title,
    description,
    category,
    languages,
    when_to_use,
    code_examples,
    success_rate,
    usage_count,
    created_at
FROM patterns
WHERE is_global_standard = TRUE
ORDER BY usage_count DESC, success_rate DESC NULLS LAST;

-- View for promotion candidates
CREATE OR REPLACE VIEW v_promotion_candidates AS
SELECT 
    cp.id,
    cp.description,
    cp.promotion_count,
    cp.project_id,
    p.project_name,
    cp.is_project_standard,
    cp.is_global_standard
FROM conversational_patterns cp
LEFT JOIN projects p ON cp.project_id = p.project_id
WHERE cp.promotion_count >= 3
  AND cp.is_project_standard = FALSE
ORDER BY cp.promotion_count DESC;

-- ============================================
-- SECTION 7: SCHEMA VERSION TRACKING
-- ============================================

CREATE TABLE IF NOT EXISTS schema_version (
    version VARCHAR(20) PRIMARY KEY,
    applied_at TIMESTAMP DEFAULT NOW(),
    description TEXT
);

INSERT INTO schema_version (version, description)
VALUES ('1.0.0', 'Initial schema with patterns, workflows, rules, projects, conversational patterns, quality gates')
ON CONFLICT (version) DO NOTHING;

-- ============================================
-- SECTION 8: VERIFICATION
-- ============================================

-- Verify installation
DO $$
DECLARE
    table_count INTEGER;
BEGIN
    SELECT COUNT(*)
    INTO table_count
    FROM information_schema.tables
    WHERE table_schema = 'public'
    AND table_name IN (
        'patterns', 'workflow_steps', 'workflow_templates', 'rules',
        'projects', 'conversational_patterns', 'pattern_usage',
        'pattern_promotions', 'quality_gates', 'pattern_quality_gates'
    );
    
    IF table_count = 10 THEN
        RAISE NOTICE 'PatternForge schema v1.0.0 installed successfully - % tables created', table_count;
    ELSE
        RAISE WARNING 'Expected 10 tables, found %', table_count;
    END IF;
END $$;

-- ============================================
-- END OF SCHEMA v1.0.0
-- ============================================

-- ============================================
-- SCHEMA MIGRATIONS (v1.0.1+)
-- ============================================
-- All schema changes after initial v1.0.0 release must be captured
-- as ALTER statements with idempotent guards for safe re-execution.
-- ============================================

-- Migration v1.0.1 (2026-02-24)
-- Change: Auto-promote conversational patterns to project standard on capture
-- Rationale: Patterns captured for a project should be immediately available
--            in project queries without requiring manual promotion or conversationId linking
-- Impact: PatternRetriever now returns all conversational patterns for a project,
--         making project-specific learning work across sessions
DO $$
BEGIN
    -- Change default value for is_project_standard from FALSE to TRUE
    ALTER TABLE conversational_patterns 
    ALTER COLUMN is_project_standard SET DEFAULT TRUE;
    
    -- Note: We do NOT update existing patterns here to preserve test data integrity.
    -- Production database update should be done separately if needed.
    
    RAISE NOTICE 'Migration v1.0.1 applied: conversational_patterns.is_project_standard now defaults to TRUE';
    
EXCEPTION
    WHEN OTHERS THEN
        RAISE NOTICE 'Migration v1.0.1 already applied or failed: %', SQLERRM;
END $$;

-- ============================================
-- END OF MIGRATIONS
-- ============================================
