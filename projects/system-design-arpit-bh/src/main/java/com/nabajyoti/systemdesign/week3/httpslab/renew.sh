#!/usr/bin/env bash
set -euo pipefail
cd "$(dirname "$0")"

# 1. Renew if due (writes new files into certs/archive, repoints live/ symlinks)
docker compose run --rm certbot renew --no-verify-ssl

# 2. Zero-downtime reload so Nginx picks up the new cert
docker compose exec nginx nginx -s reload