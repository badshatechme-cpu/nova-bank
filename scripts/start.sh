#!/usr/bin/env bash
# Starts billed-by-the-hour platform resources for a work session.
set -euo pipefail

RESOURCE_GROUP="nb-dev-rg"
POSTGRES_SERVER="nb-dev-psql"

echo "Starting PostgreSQL Flexible Server ($POSTGRES_SERVER)..."
az postgres flexible-server start --name "$POSTGRES_SERVER" --resource-group "$RESOURCE_GROUP"

echo "Done. Remember to run scripts/stop.sh when you're finished for the day."
