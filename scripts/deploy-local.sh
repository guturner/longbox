#!/usr/bin/env bash
# Build the server + web app and start the local Docker Compose stack.
#
# Prerequisites:
#   deploy/local/secrets/db_password      one line, any password
#   deploy/local/secrets/metron_password  your Metron account password
#   deploy/local/.env                     non-secret config (GOOGLE_CLIENT_IDS, etc.)
#
# The compose file pins the image to longbox-server:local, which this script builds.
set -euo pipefail

COMPOSE="deploy/local/docker-compose.yml"
SECRETS_DIR="deploy/local/secrets"

# ── preflight ────────────────────────────────────────────────────────────────
missing=0
for f in "$SECRETS_DIR/db_password" "$SECRETS_DIR/metron_password"; do
    if [[ ! -f "$f" ]]; then
        echo "Missing: $f" >&2
        missing=1
    fi
done
if [[ $missing -ne 0 ]]; then
    echo
    echo "Create the missing secret files (one value per file, no trailing newline)." >&2
    exit 1
fi

if [[ ! -f "deploy/local/.env" ]]; then
    echo "Missing deploy/local/.env — copy deploy/local/.env.example and fill it in." >&2
    exit 1
fi

# ── build ─────────────────────────────────────────────────────────────────────
echo "=== Longbox local deploy ==="
echo
echo "Building server fat jar + web distribution…"
./gradlew :server:buildFatJar :webApp:wasmJsBrowserDistribution

echo
echo "Building Docker image longbox-server:local…"
docker build -f server/Dockerfile -t longbox-server:local .

# ── start ─────────────────────────────────────────────────────────────────────
echo
echo "Starting stack…"
docker compose -f "$COMPOSE" up -d

echo
echo "Done."
echo "  Server:  http://localhost:8080"
echo "  Logs:    docker compose -f $COMPOSE logs -f server"
echo "  DB:      ./scripts/db-console.sh"
echo "  Stop:    docker compose -f $COMPOSE down"
