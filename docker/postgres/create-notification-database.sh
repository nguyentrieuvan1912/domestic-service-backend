#!/usr/bin/env bash
# Existing volumes: provision only the new database, without rerunning init or resetting data.
set -euo pipefail
psql --username "$POSTGRES_USER" --dbname postgres --set=ON_ERROR_STOP=1 \
  --set=role="${NOTIFICATION_DB_USER:?}" --set=password="${NOTIFICATION_DB_PASSWORD:?}" <<'SQL'
SELECT format('CREATE ROLE %I LOGIN PASSWORD %L', :'role', :'password')
WHERE NOT EXISTS (SELECT 1 FROM pg_roles WHERE rolname = :'role') \gexec
SELECT format('CREATE DATABASE notification_db OWNER %I', :'role')
WHERE NOT EXISTS (SELECT 1 FROM pg_database WHERE datname = 'notification_db') \gexec
SELECT 1 / CASE WHEN pg_get_userbyid(datdba) = :'role' THEN 1 ELSE 0 END
FROM pg_database WHERE datname = 'notification_db';
REVOKE ALL ON DATABASE notification_db FROM PUBLIC;
SQL
