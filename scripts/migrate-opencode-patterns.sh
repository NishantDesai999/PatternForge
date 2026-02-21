#!/bin/bash

set -e

CONTAINER="postgres14"
SOURCE_DB="opencode_standards"
TARGET_DB="patternforge"
DB_USER="postgres"

echo "========================================="
echo "Migrating Patterns from OpenCode"
echo "========================================="
echo ""
echo "Source: $SOURCE_DB"
echo "Target: $TARGET_DB"
echo ""

# Check if source database exists
if ! docker exec $CONTAINER psql -U $DB_USER -lqt | cut -d \| -f 1 | grep -qw $SOURCE_DB; then
    echo "❌ Error: Source database '$SOURCE_DB' not found"
    exit 1
fi

echo "✓ Source database found"

# Direct migration in a single SQL block
echo "Migrating patterns..."
docker exec -i $CONTAINER psql -U $DB_USER -d $TARGET_DB <<'SQL'
-- Insert patterns directly from source database
INSERT INTO patterns (
    pattern_name,
    title,
    description,
    category,
    scope,
    languages,
    when_to_use,
    code_examples,
    source,
    is_global_standard,
    is_mandatory,
    global_since,
    usage_count,
    created_at
)
SELECT 
    p.pattern_name,
    p.pattern_name,
    p.pattern_text,
    p.category,
    'global',
    ARRAY[p.language],
    'Migrated from opencode_standards',
    CASE 
        WHEN p.pattern_example IS NOT NULL THEN 
            jsonb_build_object(p.language, p.pattern_example)
        ELSE '{}'::jsonb
    END,
    'migrated_from_opencode_standards',
    true,
    (p.user_emphasis = 'mandatory'),
    p.global_since,
    COALESCE(p.seen_count, 0),
    NOW()
FROM dblink('dbname=opencode_standards user=postgres', 
    'SELECT pattern_name, pattern_text, pattern_example, category, language, 
            user_emphasis, global_since, seen_count 
     FROM patterns WHERE status = ''global''')
AS p(pattern_name text, pattern_text text, pattern_example text, 
     category text, language text, user_emphasis text, global_since date, seen_count integer)
ON CONFLICT (pattern_name) DO UPDATE
SET 
    usage_count = EXCLUDED.usage_count,
    is_global_standard = true,
    updated_at = NOW();

SELECT COUNT(*) as patterns_migrated FROM patterns WHERE source = 'migrated_from_opencode_standards';
SQL

echo ""
echo "========================================="
echo "✓ Migration complete!"
echo "========================================="
echo ""
echo "Verify with:"
echo "  docker exec -i $CONTAINER psql -U $DB_USER -d $TARGET_DB -c 'SELECT pattern_name, category, is_global_standard FROM patterns;'"
echo ""
