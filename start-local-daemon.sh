#!/usr/bin/env bash
set -euo pipefail

ROOT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
cd "$ROOT_DIR"

PID_FILE="/tmp/wepoker_daemon.pid"
LOG_FILE="/tmp/wepoker_daemon.log"

running_pid=$(lsof -t -iTCP:8080 -sTCP:LISTEN 2>/dev/null || true)
if [ -n "${running_pid:-}" ]; then
  echo "$running_pid" > "$PID_FILE"
  echo "[INFO] WePoker already running (pid=$running_pid)"
  echo "[INFO] URL: http://127.0.0.1:8080/"
  exit 0
fi

if [ -f .env.local ]; then
  set -a
  # shellcheck disable=SC1091
  . ./.env.local
  set +a
fi

if [ -z "${JAVA_HOME:-}" ] && command -v /usr/libexec/java_home >/dev/null 2>&1; then
  if /usr/libexec/java_home -v 17 >/dev/null 2>&1; then
    export JAVA_HOME
    JAVA_HOME=$(/usr/libexec/java_home -v 17)
    export PATH="$JAVA_HOME/bin:$PATH"
  fi
fi

if ! command -v mvn >/dev/null 2>&1; then
  echo "[ERROR] mvn not found"
  exit 1
fi

if command -v brew >/dev/null 2>&1; then
  brew services start redis >/dev/null 2>&1 || true
  brew services start mysql >/dev/null 2>&1 || true
fi

nohup ./start-local.sh >"$LOG_FILE" 2>&1 &
bootstrap_pid=$!
echo "$bootstrap_pid" > "$PID_FILE"

for i in $(seq 1 180); do
  listening_pid=$(lsof -t -iTCP:8080 -sTCP:LISTEN 2>/dev/null || true)
  if [ -n "${listening_pid:-}" ]; then
    echo "$listening_pid" > "$PID_FILE"
    echo "[INFO] WePoker started (pid=$listening_pid)"
    echo "[INFO] URL: http://127.0.0.1:8080/"
    curl -fsS http://127.0.0.1:8080/api/game/health >/dev/null 2>&1 || true
    exit 0
  fi
  if ! kill -0 "$bootstrap_pid" 2>/dev/null; then
    echo "[ERROR] WePoker failed to start"
    tail -n 80 "$LOG_FILE" || true
    exit 1
  fi
  sleep 1
done

echo "[ERROR] WePoker startup timeout"
exit 1
