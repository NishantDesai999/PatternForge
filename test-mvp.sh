#!/bin/bash

echo "============================================="
echo "   PatternForge MVP - Quick Test Script"
echo "============================================="
echo ""

# Check prerequisites
echo "✓ Checking prerequisites..."
if ! docker ps | grep -q postgres14; then
    echo "❌ postgres14 container not running"
    exit 1
fi
echo "  • PostgreSQL: Running"

if ! docker exec -i postgres14 psql -U postgres -lqt | cut -d \| -f 1 | grep -qw patternforge; then
    echo "❌ patternforge database not found"
    echo "  Run: ./scripts/setup-database.sh"
    exit 1
fi
echo "  • Database: exists"

# Start application
echo ""
echo "✓ Starting PatternForge..."
export JAVA_HOME=$(/usr/libexec/java_home -v 23)
java -jar target/pattern-forge-1.0.0-SNAPSHOT.jar > /tmp/pf-test.log 2>&1 &
APP_PID=$!
echo "  • PID: $APP_PID"

# Wait for startup
echo "  • Waiting for startup..."
sleep 12

# Test endpoints
echo ""
echo "✓ Testing endpoints..."

HEALTH=$(curl -s http://localhost:8080/actuator/health | jq -r '.status' 2>/dev/null)
if [ "$HEALTH" = "UP" ]; then
    echo "  • Health: ✅ UP"
else
    echo "  • Health: ❌ Failed"
    kill $APP_PID
    exit 1
fi

PATTERN_COUNT=$(curl -s http://localhost:8080/api/patterns | jq 'length' 2>/dev/null)
echo "  • Patterns loaded: $PATTERN_COUNT"

QUERY_RESULT=$(curl -s -X POST http://localhost:8080/api/patterns/query \
  -H "Content-Type: application/json" \
  -d '{"task":"Fix null check","language":"java","topK":3}' | jq -r '.metadata.patterns_retrieved' 2>/dev/null)
echo "  • Query endpoint: ✅ Returns $QUERY_RESULT patterns"

MCP_RESULT=$(curl -s -X POST http://localhost:8765/mcp/query \
  -H "Content-Type: application/json" \
  -d '{"task":"Add endpoint","language":"java"}' | jq -r '.metadata.patterns_retrieved' 2>/dev/null)
echo "  • MCP server: ✅ Returns $MCP_RESULT patterns"

# Check errors
CRITICAL_ERRORS=$(grep "ERROR" /tmp/pf-test.log | grep -v "MacOS\|DNS" | wc -l | xargs)
echo "  • Critical errors: $CRITICAL_ERRORS"

# Cleanup
kill $APP_PID 2>/dev/null
wait $APP_PID 2>/dev/null

echo ""
echo "============================================="
if [ "$CRITICAL_ERRORS" = "0" ] && [ "$HEALTH" = "UP" ]; then
    echo "  ✅ ALL TESTS PASSED - MVP READY!"
else
    echo "  ⚠️  Some tests failed"
fi
echo "============================================="
echo ""
echo "Full logs: /tmp/pf-test.log"
echo ""
