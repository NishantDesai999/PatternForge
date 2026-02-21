#!/bin/bash

set -e

# Configuration
CONTAINER="postgres17"
DB_NAME="patternforge"
DB_USER="postgres"
SCHEMA_FILE="src/main/resources/db/schema.sql"

echo "========================================="
echo "PatternForge Database Setup"
echo "========================================="
echo ""

# Check if container is running
if ! docker ps | grep -q $CONTAINER; then
    echo "❌ Error: PostgreSQL container '$CONTAINER' is not running"
    echo "   Please start it with: docker start $CONTAINER"
    exit 1
fi

echo "✓ PostgreSQL container '$CONTAINER' is running"

# Check if schema file exists
if [ ! -f "$SCHEMA_FILE" ]; then
    echo "❌ Error: Schema file not found: $SCHEMA_FILE"
    exit 1
fi

echo "✓ Schema file found: $SCHEMA_FILE"
echo ""

# Ask for confirmation
read -p "This will create database '$DB_NAME'. Continue? (y/N): " -n 1 -r
echo
if [[ ! $REPLY =~ ^[Yy]$ ]]; then
    echo "Aborted."
    exit 0
fi

# Create database (will fail if exists, but that's ok)
echo "Creating database '$DB_NAME'..."
docker exec -i $CONTAINER psql -U $DB_USER <<SQL 2>/dev/null || true
CREATE DATABASE $DB_NAME;
SQL

# Execute schema
echo "Executing schema.sql..."
cat $SCHEMA_FILE | docker exec -i $CONTAINER psql -U $DB_USER -d $DB_NAME

# Verify
echo ""
echo "Verifying installation..."
docker exec -i $CONTAINER psql -U $DB_USER -d $DB_NAME <<SQL
SELECT version FROM schema_version ORDER BY applied_at DESC LIMIT 1;
SQL

echo ""
echo "========================================="
echo "✓ Database setup complete!"
echo "========================================="
echo ""
echo "Database Details:"
echo "  - Container: $CONTAINER"
echo "  - Database: $DB_NAME"
echo "  - Connection: postgresql://localhost:5432/$DB_NAME"
echo ""
echo "Next steps:"
echo "  1. Run: ./scripts/migrate-opencode-patterns.sh (optional - migrate 13 patterns)"
echo "  2. Build: mvn clean install (generates jOOQ classes)"
echo "  3. Start: mvn spring-boot:run"
echo ""
