#!/usr/bin/env bash
# Populates a running instance with a handful of sample short URLs for demos.
# Usage: ./scripts/seed.sh [base_url]   (default: http://localhost:8080)
set -euo pipefail

BASE_URL="${1:-${BASE_URL:-http://localhost:8080}}"

urls=(
  "https://github.com/spring-projects/spring-boot"
  "https://en.wikipedia.org/wiki/URL_shortening"
  "https://www.postgresql.org/docs/current/"
  "https://prometheus.io/docs/introduction/overview/"
  "https://www.google.com/search?q=spring+boot+url+shortener"
  "https://news.ycombinator.com/"
)

echo "Seeding ${#urls[@]} short URLs against ${BASE_URL} ..."

for url in "${urls[@]}"; do
  response=$(curl -s -w '\n%{http_code}' -X POST "${BASE_URL}/api/v1/short-urls" \
    -H 'Content-Type: application/json' \
    -d "{\"url\":\"${url}\"}")
  http_status="${response##*$'\n'}"
  body="${response%$'\n'*}"
  code=$(echo "${body}" | sed -n 's/.*"code":"\([^"]*\)".*/\1/p')
  printf '  %s  %-55s -> %s\n' "${http_status}" "${url}" "${code}"
done

echo "Done. Try a redirect: curl -i ${BASE_URL}/<code>"
