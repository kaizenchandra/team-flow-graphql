#!/usr/bin/env sh
set -eu
cd "$(dirname "$0")/.."
set -a
. ./.env
set +a
export APP_ORIGIN=http://localhost:5173
cd backend
exec ./mvnw spring-boot:run
