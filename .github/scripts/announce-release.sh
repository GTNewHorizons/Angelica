#!/usr/bin/env bash
set -euo pipefail

: "${REPO:?REPO required}"
: "${TAG:?TAG required}"

if [ -z "${DISCORD_WEBHOOK_URL:-}" ]; then
  echo "DISCORD_WEBHOOK_URL not set, skipping"
  exit 0
fi

if ! release_json=$(gh release view "$TAG" --repo "$REPO" --json body,url,publishedAt 2>&1); then
  echo "Could not read release $TAG in $REPO; skipping:"
  printf '%s\n' "$release_json"
  exit 0
fi
body=$(jq -r '.body // ""' <<<"$release_json")
url=$(jq -r '.url' <<<"$release_json")
ts=$(jq -r '.publishedAt // ""' <<<"$release_json")
[ -n "$ts" ] || ts=$(date -u +%Y-%m-%dT%H:%M:%SZ)

payload=$(jq -n \
  --arg title "$TAG" \
  --arg url "$url" \
  --arg desc "$body" \
  --arg ts "$ts" \
  '{
    embeds: [{
      title: ("Angelica " + $title + " released"),
      url: $url,
      description: (if ($desc | length) > 3900
                    then $desc[0:3900] + "\n\n...[full changelog](" + $url + ")"
                    else $desc end),
      color: 3066993,
      footer: { text: "GitHub Releases" },
      timestamp: $ts
    }]
  }')

curl -fsS -X POST -H "Content-Type: application/json" -d "$payload" "$DISCORD_WEBHOOK_URL"
