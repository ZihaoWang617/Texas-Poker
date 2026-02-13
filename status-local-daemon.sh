#!/usr/bin/env bash
set -euo pipefail

PID_FILE="/tmp/wepoker_daemon.pid"
pid=$(lsof -t -iTCP:8080 -sTCP:LISTEN 2>/dev/null || true)

if [ -z "${pid:-}" ]; then
  echo "status: stopped"
  rm -f "$PID_FILE"
  exit 0
fi

echo "$pid" > "$PID_FILE"
if curl -fsS http://127.0.0.1:8080/api/game/health >/dev/null 2>&1; then
  echo "status: running (pid=$pid, healthy)"
else
  echo "status: running (pid=$pid, health-check unavailable)"
fi
