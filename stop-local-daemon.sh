#!/usr/bin/env bash
set -euo pipefail

PID_FILE="/tmp/wepoker_daemon.pid"

listener_pid=$(lsof -t -iTCP:8080 -sTCP:LISTEN 2>/dev/null || true)
file_pid=""
if [ -f "$PID_FILE" ]; then
  file_pid=$(cat "$PID_FILE" 2>/dev/null || true)
fi

if [ -z "${listener_pid:-}" ] && [ -z "${file_pid:-}" ]; then
  echo "[INFO] WePoker is not running"
  rm -f "$PID_FILE"
  exit 0
fi

if [ -n "${listener_pid:-}" ] && kill -0 "$listener_pid" 2>/dev/null; then
  kill "$listener_pid" 2>/dev/null || true
  sleep 1
  if kill -0 "$listener_pid" 2>/dev/null; then
    kill -9 "$listener_pid" 2>/dev/null || true
  fi
  echo "[INFO] WePoker stopped (pid=$listener_pid)"
fi

if [ -n "${file_pid:-}" ] && kill -0 "$file_pid" 2>/dev/null; then
  kill "$file_pid" 2>/dev/null || true
fi

rm -f "$PID_FILE"
