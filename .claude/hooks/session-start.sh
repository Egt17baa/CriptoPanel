#!/bin/bash
# Installs playwright-cli and points it at the Chromium preinstalled in cloud sessions.
set -euo pipefail

if [ "${CLAUDE_CODE_REMOTE:-}" != "true" ]; then
  exit 0
fi

if ! command -v playwright-cli >/dev/null 2>&1; then
  npm install -g @playwright/cli@latest
fi

# playwright-cli defaults to Google Chrome, which cloud sessions don't have.
CHROMIUM=/opt/pw-browsers/chromium
if [ -x "$CHROMIUM" ] && [ -n "${CLAUDE_ENV_FILE:-}" ]; then
  echo 'export PLAYWRIGHT_MCP_BROWSER=chromium' >> "$CLAUDE_ENV_FILE"
  echo "export PLAYWRIGHT_MCP_EXECUTABLE_PATH=$CHROMIUM" >> "$CLAUDE_ENV_FILE"
fi
