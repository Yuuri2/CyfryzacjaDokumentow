#!/usr/bin/env bash
# Generuje token JWT (HS256) akceptowany przez usługę. Wypisuje sam token na stdout.
# Wymaga: openssl. Parametry z env: JWT_SECRET (wymagany, min. 32 znaki), JWT_ISSUER (formforge),
# JWT_TTL_SECONDS (3600), JWT_SUBJECT (jury).
# Użycie:  set -a; source .env; set +a;  TOKEN=$(./scripts/generate-jwt.sh)
set -eu
: "${JWT_SECRET:?Ustaw JWT_SECRET (ten sam co w .env aplikacji)}"
if [ "${#JWT_SECRET}" -lt 32 ]; then echo "JWT_SECRET musi mieć min. 32 znaki" >&2; exit 1; fi
ISSUER="${JWT_ISSUER:-formforge}"
TTL="${JWT_TTL_SECONDS:-3600}"
SUBJECT="${JWT_SUBJECT:-jury}"

b64url() { openssl base64 -A | tr '+/' '-_' | tr -d '='; }

now="$(date +%s)"
header="$(printf '%s' '{"alg":"HS256","typ":"JWT"}' | b64url)"
payload="$(printf '{"iss":"%s","sub":"%s","iat":%s,"exp":%s}' "$ISSUER" "$SUBJECT" "$now" "$((now + TTL))" | b64url)"
signature="$(printf '%s' "$header.$payload" | openssl dgst -sha256 -hmac "$JWT_SECRET" -binary | b64url)"
printf '%s.%s.%s\n' "$header" "$payload" "$signature"
