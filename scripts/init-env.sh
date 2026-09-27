#!/usr/bin/env sh
set -eu
cd "$(dirname "$0")/.."
if [ -e .env ]; then
  printf '%s\n' 'Keeping existing .env unchanged.'
  exit 0
fi
command -v openssl >/dev/null 2>&1 || { printf '%s\n' 'OpenSSL is required to generate the database password.' >&2; exit 1; }
teamflow_password=$(openssl rand -hex 32)
umask 077
set -C
{
  printf 'DB_PASSWORD=%s\n' "$teamflow_password"
  sed '/^DB_PASSWORD=/d' .env.example
} > .env
printf '%s\n' 'Created .env with a random database password. Keep this file for subsequent starts.'
