#!/usr/bin/env bash
# Install PatternForge git hooks into a project.
#
# Usage (run from any git project root):
#   bash /path/to/pattern-forge/scripts/install-hooks.sh
#
# Or install globally for all new repos via git init template:
#   bash /path/to/pattern-forge/scripts/install-hooks.sh --global
#
# Environment:
#   PATTERNFORGE_URL  Override the PatternForge server URL (default: http://localhost:15550)

set -euo pipefail

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
HOOK_SRC="${SCRIPT_DIR}/hooks/post-commit"
GLOBAL=false

for arg in "$@"; do
  case "$arg" in
    --global) GLOBAL=true ;;
    *) echo "Unknown argument: $arg"; exit 1 ;;
  esac
done

if [ "$GLOBAL" = true ]; then
  # Install into git's global init template so every new `git init` gets the hook
  TEMPLATE_DIR="${GIT_TEMPLATE_DIR:-$(git config --global init.templateDir 2>/dev/null || echo "$HOME/.git-templates")}"
  HOOKS_DIR="${TEMPLATE_DIR}/hooks"
  mkdir -p "$HOOKS_DIR"
  cp "$HOOK_SRC" "${HOOKS_DIR}/post-commit"
  chmod +x "${HOOKS_DIR}/post-commit"
  git config --global init.templateDir "$TEMPLATE_DIR"
  echo "[PatternForge] Global hook installed → ${HOOKS_DIR}/post-commit"
  echo "  New repos created with 'git init' will auto-include it."
  echo "  For existing repos, run without --global from inside the repo."
else
  # Install into the current project
  if ! git rev-parse --git-dir > /dev/null 2>&1; then
    echo "Error: not inside a git repository. Run from your project root or use --global."
    exit 1
  fi

  GIT_DIR="$(git rev-parse --git-dir)"
  HOOKS_DIR="${GIT_DIR}/hooks"
  mkdir -p "$HOOKS_DIR"
  TARGET="${HOOKS_DIR}/post-commit"

  if [ -f "$TARGET" ]; then
    # Append to existing hook rather than overwrite
    if grep -q "PatternForge" "$TARGET"; then
      echo "[PatternForge] Hook already installed at ${TARGET} — skipping."
      exit 0
    fi
    echo "" >> "$TARGET"
    echo "# --- PatternForge ---" >> "$TARGET"
    cat "$HOOK_SRC" >> "$TARGET"
    echo "[PatternForge] Appended to existing hook at ${TARGET}"
  else
    cp "$HOOK_SRC" "$TARGET"
    chmod +x "$TARGET"
    echo "[PatternForge] Hook installed at ${TARGET}"
  fi

  echo "  Every commit in this repo will now auto-extract patterns → ${PATTERNFORGE_URL:-http://localhost:15550}"
fi
