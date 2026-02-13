#!/usr/bin/env bash
set -euo pipefail

if command -v brew >/dev/null 2>&1; then
  if brew services list | grep -q '^mysql\s\+started'; then
    brew services stop mysql >/dev/null
    echo "[INFO] stopped local mysql"
  fi
  if brew services list | grep -q '^redis\s\+started'; then
    brew services stop redis >/dev/null
    echo "[INFO] stopped local redis"
  fi
fi

echo "[INFO] local dependencies stopped"
