#!/usr/bin/env bash
set -euo pipefail
create_service_database() {
  psql --username "$POSTGRES_USER" --dbname postgres --set=ON_ERROR_STOP=1 \
    --set=role="$1" --set=password="$2" --set=database="$3" <<'SQL'
CREATE ROLE :"role" LOGIN PASSWORD :'password';
CREATE DATABASE :"database" OWNER :"role";
REVOKE ALL ON DATABASE :"database" FROM PUBLIC;
SQL
}
create_service_database "$IDENTITY_DB_USER" "$IDENTITY_DB_PASSWORD" identity_db
create_service_database "$CATALOG_DB_USER" "$CATALOG_DB_PASSWORD" catalog_db
create_service_database "$BOOKING_DB_USER" "$BOOKING_DB_PASSWORD" booking_db
create_service_database "$FINANCE_DB_USER" "$FINANCE_DB_PASSWORD" finance_db
create_service_database "$AI_DB_USER" "$AI_DB_PASSWORD" ai_db
