#!/usr/bin/env bash
# Starts the Looki backend locally on http://localhost:8081 with PostgreSQL in
# Docker. Works in Git Bash on Windows, macOS and Linux.
# Settings come from a file outside the repo (see local.env.example):
#   $LOOKI_ENV_FILE, else %LOCALAPPDATA%\Looki\postgres.env (Windows),
#   else ~/.looki/local.env
set -euo pipefail

if [[ -n "${LOOKI_ENV_FILE:-}" ]]; then
    env_file="$LOOKI_ENV_FILE"
elif [[ -n "${LOCALAPPDATA:-}" && -f "$LOCALAPPDATA/Looki/postgres.env" ]]; then
    env_file="$LOCALAPPDATA/Looki/postgres.env"
else
    env_file="$HOME/.looki/local.env"
fi
if [[ ! -f "$env_file" ]]; then
    echo "Settings file not found: $env_file" >&2
    echo "Copy local.env.example there and fill it in." >&2
    exit 1
fi

set -a
# shellcheck disable=SC1090
source <(tr -d '\r' < "$env_file")
set +a

for name in POSTGRES_DB POSTGRES_USER POSTGRES_PASSWORD JWT_SECRET; do
    if [[ -z "${!name:-}" ]]; then
        echo "Missing $name in $env_file" >&2
        exit 1
    fi
done

if ! docker inspect looki-postgres >/dev/null 2>&1; then
    echo "Creating the looki-postgres container..."
    docker run -d --name looki-postgres --restart unless-stopped \
        -e POSTGRES_DB="$POSTGRES_DB" -e POSTGRES_USER="$POSTGRES_USER" \
        -e POSTGRES_PASSWORD="$POSTGRES_PASSWORD" \
        -p 127.0.0.1:5432:5432 -v looki-postgres-data:/var/lib/postgresql/data \
        postgres:16-alpine >/dev/null
else
    docker start looki-postgres >/dev/null
fi

for _ in $(seq 1 30); do
    if docker exec looki-postgres pg_isready -U "$POSTGRES_USER" -d "$POSTGRES_DB" >/dev/null 2>&1; then
        break
    fi
    sleep 2
done
docker exec looki-postgres pg_isready -U "$POSTGRES_USER" -d "$POSTGRES_DB" >/dev/null \
    || { echo "PostgreSQL did not become ready." >&2; exit 1; }

if [[ -z "${MODAL_AI_TOKEN:-}" ]]; then
    echo "MODAL_AI_TOKEN is empty: AI features will use their non-AI fallbacks."
fi

export SPRING_DATASOURCE_URL="jdbc:postgresql://127.0.0.1:5432/$POSTGRES_DB"
export SPRING_DATASOURCE_USERNAME="$POSTGRES_USER"
export SPRING_DATASOURCE_PASSWORD="$POSTGRES_PASSWORD"
export SERVER_ADDRESS=127.0.0.1
export PORT=8081

cd "$(dirname "$0")"
if command -v mvn >/dev/null 2>&1; then
    exec mvn spring-boot:run
else
    exec ./mvnw spring-boot:run
fi
