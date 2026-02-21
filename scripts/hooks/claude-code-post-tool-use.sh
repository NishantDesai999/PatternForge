#!/usr/bin/env bash
# PatternForge — Claude Code PostToolUse hook
#
# Automatically captures patterns from file-edit tool uses.
# Runs after every Edit/Write tool call in Claude Code.
#
# Installation (Claude Code hooks):
# Add to ~/.claude/settings.json under "hooks":
#
#   "PostToolUse": [
#     {
#       "matcher": "Edit|Write|MultiEdit",
#       "hooks": [
#         {
#           "type": "command",
#           "command": "/path/to/pattern-forge/scripts/hooks/claude-code-post-tool-use.sh"
#         }
#       ]
#     }
#   ]
#
# Or use the installer:
#   bash scripts/install-hooks.sh --claude-code
#
# Environment variables:
#   PATTERNFORGE_URL     PatternForge server URL (default: http://localhost:15550)
#   PATTERNFORGE_CAPTURE Enable capture (default: true; set to false to disable)
#
# Claude Code passes tool input/output as JSON on stdin:
#   {"tool_name": "Edit", "tool_input": {...}, "tool_response": {...}, "session_id": "..."}

set -euo pipefail

PATTERNFORGE_URL="${PATTERNFORGE_URL:-http://localhost:15550}"
CAPTURE_ENABLED="${PATTERNFORGE_CAPTURE:-true}"

# If capture is disabled, exit silently
if [ "$CAPTURE_ENABLED" != "true" ]; then
  exit 0
fi

# Read JSON from stdin (Claude Code passes hook input here)
HOOK_INPUT=""
if [ -p /dev/stdin ]; then
  HOOK_INPUT=$(cat /dev/stdin 2>/dev/null || true)
fi

if [ -z "$HOOK_INPUT" ]; then
  exit 0
fi

# Check PatternForge is reachable (fail silently — never block the agent)
if ! curl -sf --max-time 2 "${PATTERNFORGE_URL}/actuator/health" > /dev/null 2>&1; then
  exit 0
fi

# Extract fields from hook input using python3 (more reliable than jq for nested JSON)
TOOL_NAME=$(echo "$HOOK_INPUT" | python3 -c "import json,sys; d=json.load(sys.stdin); print(d.get('tool_name',''))" 2>/dev/null || true)
SESSION_ID=$(echo "$HOOK_INPUT" | python3 -c "import json,sys; d=json.load(sys.stdin); print(d.get('session_id',''))" 2>/dev/null || true)
FILE_PATH=$(echo "$HOOK_INPUT" | python3 -c "import json,sys; d=json.load(sys.stdin); inp=d.get('tool_input',{}); print(inp.get('file_path','') or inp.get('path',''))" 2>/dev/null || true)

# Only act on edit/write tools
case "$TOOL_NAME" in
  Edit|Write|MultiEdit|NotebookEdit) : ;;  # proceed
  *) exit 0 ;;
esac

# Skip non-code files
if [ -z "$FILE_PATH" ]; then
  exit 0
fi

EXTENSION="${FILE_PATH##*.}"
case "$EXTENSION" in
  java|kt|py|ts|tsx|js|go|rs|rb|cs|swift|cpp|c|h|php|scala|sh) : ;;  # proceed
  *) exit 0 ;;
esac

# Derive project path (walk up to find .git root)
CANDIDATE_DIR=$(dirname "$FILE_PATH")
PROJECT_PATH=""
while [ "$CANDIDATE_DIR" != "/" ]; do
  if [ -d "${CANDIDATE_DIR}/.git" ]; then
    PROJECT_PATH="$CANDIDATE_DIR"
    break
  fi
  CANDIDATE_DIR=$(dirname "$CANDIDATE_DIR")
done

# Fall back to directory of edited file if no git root found
if [ -z "$PROJECT_PATH" ]; then
  PROJECT_PATH=$(dirname "$FILE_PATH")
fi

# Extract what changed (description from tool input)
DESCRIPTION=$(echo "$HOOK_INPUT" | python3 -c "
import json, sys
d = json.load(sys.stdin)
inp = d.get('tool_input', {})
path = inp.get('file_path', inp.get('path', ''))
tool = d.get('tool_name', 'Edit')
old = inp.get('old_string', '')
new = inp.get('new_string', inp.get('content', ''))
short_path = path.split('/')[-1] if '/' in path else path
# Build a short description
if old and new:
    desc = f'{tool} in {short_path}: replaced pattern'
else:
    desc = f'{tool} {short_path}'
print(desc[:200])
" 2>/dev/null || echo "Code edit via Claude Code")

# Capture as agent_observation (low-confidence; user can promote later)
curl -sf --max-time 5 \
  -X POST "${PATTERNFORGE_URL}/api/patterns/capture" \
  -H "Content-Type: application/json" \
  -d "$(python3 -c "
import json
print(json.dumps({
  'description': '${DESCRIPTION//\'/\\'\\'}',
  'source': 'agent_observation',
  'projectPath': '${PROJECT_PATH//\'/\\'\\'}',
  'conversationId': '${SESSION_ID//\'/\\'\\'}'
}))
" 2>/dev/null)" \
  > /dev/null 2>&1 &

exit 0
