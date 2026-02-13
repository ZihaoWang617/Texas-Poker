#!/usr/bin/env bash
set -euo pipefail

ok=true

check_cmd() {
  local name="$1"
  local hint="$2"
  if command -v "$name" >/dev/null 2>&1; then
    echo "[OK] $name"
  else
    echo "[MISS] $name - $hint"
    ok=false
  fi
}

have_cmd() {
  command -v "$1" >/dev/null 2>&1
}

echo "WePoker environment check"
echo "========================"

if have_cmd java; then
  java_v=$(java -version 2>&1 | sed -n '1p')
  echo "[INFO] $java_v"
else
  echo "[MISS] java - install JDK 17+"
  ok=false
fi

check_cmd mvn "install Maven 3.8+"

has_docker=false
if have_cmd docker; then
  echo "[OK] docker"
  has_docker=true
else
  echo "[INFO] docker not found (optional if using local MySQL/Redis)"
fi

if have_cmd docker-compose || (have_cmd docker && docker compose version >/dev/null 2>&1); then
  echo "[OK] docker compose"
else
  echo "[INFO] docker compose not found (optional if using local MySQL/Redis)"
fi

has_local_db=true
if ! have_cmd redis-server; then
  echo "[MISS] redis-server - install Redis or use Docker"
  has_local_db=false
else
  echo "[OK] redis-server"
fi

if ! have_cmd mysql; then
  echo "[MISS] mysql - install MySQL or use Docker"
  has_local_db=false
else
  echo "[OK] mysql"
fi

if ! $has_docker && ! $has_local_db; then
  echo "[MISS] runtime dependencies - need either Docker, or both local Redis and MySQL"
  ok=false
fi

if have_cmd python3; then
  py_v=$(python3 --version 2>&1)
  echo "[INFO] $py_v"
else
  echo "[MISS] python3 - install Python 3.9+"
  ok=false
fi

if [ "$ok" = true ]; then
  printf "\nAll required tools are available.\n"
  echo "Then run: ./start-local.sh"
else
  printf "\nSome dependencies are missing. Install them first.\n"
fi
