#!/usr/bin/env bash
set -euo pipefail

if ! command -v docker-compose >/dev/null 2>&1; then
  echo "[ERROR] docker-compose not found"
  exit 1
fi

docker-compose down

echo "[INFO] docker dependencies stopped"
