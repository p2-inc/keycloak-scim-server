#!/usr/bin/env bash
#
# Compute the next Phase Two release version for keycloak-scim-server.
#
# Reads the base version from gradle.properties (the SNAPSHOT prefix from
# upstream), finds the highest existing release tag matching v{base}.N, and
# emits next_version = {base}.{N+1}.
#
# If the current upstream HEAD already has a release tag, skip is set to true.
#
# Designed to be sourced from a GitHub Actions step (uses GITHUB_OUTPUT).
# Run from inside a checkout of upstream/develop that has p2-inc fork tags
# fetched into its local tag namespace.
set -euo pipefail

BASE=$(grep -E '^version=' gradle.properties | head -1 | cut -d= -f2 | sed 's/-SNAPSHOT$//')

if [ -z "$BASE" ]; then
  echo "Could not parse base version from gradle.properties" >&2
  exit 1
fi

# Existing N values from v{BASE}.N tags
EXISTING=$(git tag -l "v${BASE}.*" \
  | sed -E "s/^v${BASE}\.([0-9]+)$/\1/" \
  | grep -E '^[0-9]+$' || true)

if [ -z "$EXISTING" ]; then
  MAX_N=0
else
  MAX_N=$(echo "$EXISTING" | sort -n | tail -1)
fi

NEXT=$((MAX_N + 1))
NEXT_VERSION="${BASE}.${NEXT}"

HEAD_SHA=$(git rev-parse HEAD)
ALREADY_TAGGED=$(git tag --points-at "$HEAD_SHA" | grep -E "^v${BASE}\.[0-9]+$" || true)

{
  echo "base_version=$BASE"
  echo "head_sha=$HEAD_SHA"
} >> "${GITHUB_OUTPUT:-/dev/stdout}"

if [ -n "$ALREADY_TAGGED" ]; then
  echo "Already tagged on $HEAD_SHA: $ALREADY_TAGGED" >&2
  echo "skip=true" >> "${GITHUB_OUTPUT:-/dev/stdout}"
else
  echo "Base version:        $BASE" >&2
  echo "Existing N values:   $(echo $EXISTING | tr '\n' ' ')" >&2
  echo "Next release version: $NEXT_VERSION" >&2
  {
    echo "skip=false"
    echo "next_version=$NEXT_VERSION"
  } >> "${GITHUB_OUTPUT:-/dev/stdout}"
fi
