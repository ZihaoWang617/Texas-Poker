#!/usr/bin/env bash
set -euo pipefail

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

MYSQL_USER="${WEP_MYSQL_USER:-root}"
MYSQL_PASSWORD="${WEP_MYSQL_PASSWORD:-}"
MYSQL_HOST="${WEP_MYSQL_HOST:-127.0.0.1}"
MYSQL_PORT="${WEP_MYSQL_PORT:-3306}"
MYSQL_ARGS=(-h "${MYSQL_HOST}" -P "${MYSQL_PORT}" -u "${MYSQL_USER}")
if [ -n "${MYSQL_PASSWORD}" ]; then
  MYSQL_ARGS+=(-p"${MYSQL_PASSWORD}")
fi

if command -v brew >/dev/null 2>&1; then
  if brew services list | grep -q '^redis\s'; then
    if ! brew services list | grep '^redis\s' | grep -q 'started'; then
      brew services start redis >/dev/null
      echo "[INFO] redis service started"
    fi
  fi

  if brew services list | grep -q '^mysql\s'; then
    if ! brew services list | grep '^mysql\s' | grep -q 'started'; then
      brew services start mysql >/dev/null
      echo "[INFO] mysql service started"
    fi
  fi
fi

if command -v mysql >/dev/null 2>&1; then
  for i in $(seq 1 30); do
    if mysql "${MYSQL_ARGS[@]}" -e "SELECT 1" >/dev/null 2>&1; then
      break
    fi
    sleep 1
  done

  if ! mysql "${MYSQL_ARGS[@]}" -e "SELECT 1" >/dev/null 2>&1; then
    echo "[ERROR] local mysql is not ready"
    exit 1
  fi

  mysql "${MYSQL_ARGS[@]}" -e "CREATE DATABASE IF NOT EXISTS wepoker DEFAULT CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci;" >/dev/null

  table_count=$(mysql "${MYSQL_ARGS[@]}" -N -e "SELECT COUNT(*) FROM information_schema.tables WHERE table_schema='wepoker';")
  if [ "${table_count:-0}" = "0" ] && [ -f schema.sql ]; then
    mysql "${MYSQL_ARGS[@]}" wepoker < schema.sql
    echo "[INFO] schema.sql imported into wepoker"
  fi
fi

echo "[INFO] starting WePoker backend on http://localhost:8080"
exec mvn -q spring-boot:run
