#!/bin/bash
set -euo pipefail

for db in customer_db account_db card_db; do
  psql -v ON_ERROR_STOP=1 --username "$POSTGRES_USER" --dbname "postgres" <<-EOSQL
    CREATE DATABASE ${db};
EOSQL
done
