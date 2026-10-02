#!/usr/bin/env bash
# One-time setup: creates a dedicated, least-privilege Postgres role per service,
# using the passwords Bicep already generated and stored in Key Vault. Run this
# yourself once after Stage 3's `az deployment sub create` succeeds — it connects
# directly to the live database with the admin credential, which is a step you
# should run rather than have an assistant run on your behalf.
#
# Requires: az cli (logged in), psql client installed locally.
set -euo pipefail

RESOURCE_GROUP="nb-dev-rg"
VAULT_NAME="nbdevkvzoyuqrptm2qia"
PG_HOST="nb-dev-psql.postgres.database.azure.com"
PG_ADMIN_USER="nbadmin"

ADMIN_PW=$(az keyvault secret show --vault-name "$VAULT_NAME" --name postgres-admin-password --query value -o tsv)

create_role_for() {
  local service="$1"
  local db="${service}_db"
  local role="${service}_app"
  local pw
  pw=$(az keyvault secret show --vault-name "$VAULT_NAME" --name "${service}-db-password" --query value -o tsv)

  echo "Creating role '$role' on database '$db'..."
  PGPASSWORD="$ADMIN_PW" psql "host=$PG_HOST port=5432 dbname=$db user=$PG_ADMIN_USER sslmode=require" \
    -v ON_ERROR_STOP=1 \
    -v pw="$pw" \
    -v role="$role" \
    -v db="$db" \
    <<'SQL'
SELECT EXISTS (SELECT FROM pg_roles WHERE rolname = :'role') AS role_exists \gset

\if :role_exists
ALTER ROLE :"role" WITH LOGIN PASSWORD :'pw';
\else
CREATE ROLE :"role" WITH LOGIN PASSWORD :'pw';
\endif

GRANT ALL PRIVILEGES ON DATABASE :"db" TO :"role";
GRANT ALL ON SCHEMA public TO :"role";
SQL
}

create_role_for customer
create_role_for account
create_role_for card

echo "Done. Each service now has its own Postgres role, matching the password already stored in Key Vault under <service>-db-password."
