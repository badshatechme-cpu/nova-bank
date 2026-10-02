#!/usr/bin/env bash
# Stops billed-by-the-hour platform resources to save cost between sessions.
set -euo pipefail

RESOURCE_GROUP="nb-dev-rg"
POSTGRES_SERVER="nb-dev-psql"

echo "Stopping PostgreSQL Flexible Server ($POSTGRES_SERVER)..."
az postgres flexible-server stop --name "$POSTGRES_SERVER" --resource-group "$RESOURCE_GROUP"

echo "Done."
