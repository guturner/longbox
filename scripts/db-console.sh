#!/usr/bin/env bash
# Open a psql session against the local Docker Compose Postgres instance.
# Requires the local stack to be running: ./scripts/deploy-local.sh
set -euo pipefail

COMPOSE="deploy/local/docker-compose.yml"

if ! docker compose -f "$COMPOSE" ps --services --filter status=running 2>/dev/null | grep -q "^db$"; then
    echo "The local stack isn't running. Start it with: ./scripts/deploy-local.sh" >&2
    exit 1
fi

echo "Connecting to longbox database…  (\\q to exit)"
echo
docker compose -f "$COMPOSE" exec db psql -U longbox -d longbox
