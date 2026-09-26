#!/usr/bin/env sh
set -eu
base=${1:-http://localhost:8088}
curl --fail --silent "$base/actuator/health" | python3 -c 'import sys,json; assert json.load(sys.stdin)["status"]=="UP"'
curl --fail --silent "$base/" | python3 -c 'import sys; assert "TeamFlow" in sys.stdin.read()'
curl --fail --silent "$base/auth/csrf" | python3 -c 'import sys,json; assert json.load(sys.stdin)["token"]'
printf 'Health, frontend and CSRF checks passed for %s\n' "$base"
