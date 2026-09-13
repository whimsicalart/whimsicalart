#!/bin/sh
#
# GitHub secrets: the generated keystore file (`release.keystore`) goes into
# `KEYSTORE_BASE64` as its base64 encoding; the keystore password goes into
# `KEYSTORE_PASSWORD`, the key password into `KEY_PASSWORD`, and the key alias
# into `KEY_ALIAS`.
#
# Generate a release signing keystore inside a throwaway eclipse-temurin
# container and save it under .tmp/keys/ (git-ignored). Everything crosses the
# docker boundary through stdin/stdout, so it works against a remote daemon
# with no volume mounts. The container is removed afterwards; the image is not
# kept (one-shot use, re-pulled only in extreme cases).
#
# Usage: scripts/genkey.sh [key-alias]
#
# Prompts for the keystore password and the key password (defaults to the
# keystore password). Prints the validation fingerprint and a ready-to-paste
# KEYSTORE_BASE64 line for the GitHub release workflow.

set -e

if ! command -v docker > /dev/null; then
  printf '!! Command docker not found!\n' >&2
  exit
fi

dir="$(dirname "$0")/.."
outdir="$dir/.tmp/keys"
keyalias=${1:-release}
if [ "$keyalias" != "${keyalias#*[!A-Za-z0-9_-]}" ]; then
  printf '!! Key alias may only contain [A-Za-z0-9_-].\n' >&2
  exit 1
fi

mkdir -p "$outdir"
outfile="$outdir/release.keystore"

printf '==> Checking Docker daemon\n'
if ! timeout 20 docker ps >/dev/null 2>&1; then
  printf '!! Docker daemon not reachable. Start dockerd on the host (or reboot if the host locked up).\n' >&2
  exit 1
fi

ask() {
  printf '%s' "$1"
  if [ -t 0 ]; then
    stty -echo
  fi
  IFS= read -r value
  if [ -t 0 ]; then
    stty echo
  fi
  printf '\n'
}

ask 'Keystore password: '
storepass=$value
ask 'Key password (enter for same as keystore): '
keypass=${value:-$storepass}
if [ -z "$storepass" ]; then
  printf '!! Password cannot be empty.\n' >&2
  exit 1
fi

printf '==> Generating keystore (alias "%s") inside a throwaway container\n' "$keyalias"
printf '    image: eclipse-temurin:17-jdk (pulled on demand, not kept)\n'

docker run \
  --rm \
  --pull=always \
  -i \
  --entrypoint sh \
  -e STOREPASS="$storepass" \
  -e KEYPASS="$keypass" \
  -e KEYALIAS="$keyalias" \
  eclipse-temurin:17-jdk \
  -ec '
set -e
keytool -genkeypair \
  -v \
  -keystore /tmp/release.keystore \
  -alias "$KEYALIAS" \
  -keyalg RSA \
  -keysize 4096 \
  -validity 10000 \
  -storepass "$STOREPASS" \
  -keypass "$KEYPASS" \
  -dname "CN=WhimsicalArt, OU=Release, O=WhimsicalArt, L=Unknown, ST=Unknown, C=XX" \
  >&2
cat /tmp/release.keystore
rm -f /tmp/release.keystore
' > "$outfile"

chmod 600 "$outfile"

printf '==> Validating the keystore (streaming it back into a throwaway container)\n'
cat "$outfile" | docker run \
  --rm \
  --pull=always \
  -i \
  --entrypoint sh \
  -e STOREPASS="$storepass" \
  eclipse-temurin:17-jdk \
  -ec '
set -e
cat > /tmp/release.keystore
keytool -list \
  -v \
  -keystore /tmp/release.keystore \
  -storepass "$STOREPASS" \
  >&2
rm -f /tmp/release.keystore
'

size=$(wc -c < "$outfile" | tr -d ' ')
if [ "$size" -lt 1000 ]; then
  printf '!! Generated keystore looks too small (%s bytes) — removal?\n' "$size" >&2
  exit 1
fi

printf '==> DONE\n\n'
printf 'Keystore:   %s\n' "$outfile"
printf 'Key alias:  %s\n' "$keyalias"
printf 'Keystore password and key password: as entered above\n\n'
printf 'For the GitHub release workflow, store these secrets '
printf '(Settings -> Secrets and variables -> Actions):\n'
printf '  KEYSTORE_BASE64   -> '
base64 -w 0 "$outfile"
printf '\n  KEYSTORE_PASSWORD -> (keystore password entered above)\n'
printf '  KEY_ALIAS         -> %s\n' "$keyalias"
printf '  KEY_PASSWORD      -> (key password used above)\n'