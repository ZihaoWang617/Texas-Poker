#!/usr/bin/env bash
set -euo pipefail

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

if ! command -v docker >/dev/null 2>&1; then
  echo "[ERROR] docker not found"
  exit 1
fi

if ! command -v docker-compose >/dev/null 2>&1; then
  echo "[ERROR] docker-compose not found"
  exit 1
fi

if ! docker info >/dev/null 2>&1; then
  echo "[ERROR] Docker daemon is not ready. Please start Docker Desktop first."
  exit 1
fi

# Avoid port conflicts with local services when using Docker mode.
if command -v brew >/dev/null 2>&1; then
  if brew services list | grep -q '^mysql\s\+started'; then
    brew services stop mysql >/dev/null
    echo "[INFO] stopped local mysql service to free 3306"
  fi
  if brew services list | grep -q '^redis\s\+started'; then
    brew services stop redis >/dev/null
    echo "[INFO] stopped local redis service to free 6379"
  fi
fi

echo "[INFO] starting docker dependencies (mysql, redis)"
docker-compose up -d redis mysql

# Wait for healthy status.
for i in $(seq 1 60); do
  mysql_ok=false
  redis_ok=false

  if docker ps --format '{{.Names}} {{.Status}}' | grep -q 'wepoker-mysql .*healthy'; then
    mysql_ok=true
  fi
  if docker ps --format '{{.Names}} {{.Status}}' | grep -q 'wepoker-redis .*healthy'; then
    redis_ok=true
  fi

  if [ "$mysql_ok" = true ] && [ "$redis_ok" = true ]; then
    break
  fi
  sleep 2
done

echo "[INFO] starting WePoker backend on http://localhost:8080"
exec env WEP_MYSQL_USER=root WEP_MYSQL_PASSWORD=root mvn -q spring-boot:run
