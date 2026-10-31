#!/usr/bin/env bash
set -euo pipefail

# Ayne — release signing setup helper
#
# Prepares everything needed to sign release builds on GitHub Actions:
#   1. Generates a release keystore (my-upload-key.jks) if one doesn't exist
#   2. Base64-encodes it for the KEYSTORE_BASE64 secret
#   3. Prints step-by-step instructions for adding the 4 GitHub secrets
#
# Passwords are entered interactively and never echoed, logged, or stored.
#
# Usage: ./scripts/setup-signing-secrets.sh

REPO_ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
KEYSTORE="$REPO_ROOT/my-upload-key.jks"
B64_FILE="$KEYSTORE.b64"
ALIAS="upload"

echo "======================================"
echo " Ayne — release signing setup"
echo "======================================"
echo

# --- 1. Keystore ---------------------------------------------------------------

if [ -f "$KEYSTORE" ]; then
  echo "Found existing keystore: $KEYSTORE"
  read -r -p "Reuse it? [Y/n] " reuse
  if [[ ! "$reuse" =~ ^[Yy]?$ ]]; then
    echo "Aborting — move or delete the existing keystore first."
    exit 1
  fi
else
  echo "No release keystore found. One will be created at:"
  echo "  $KEYSTORE"
  echo
  echo "IMPORTANT: back this file up somewhere safe (password manager, USB drive)."
  echo "If you lose it, users can never upgrade an installed release — Android"
  echo "refuses updates signed with a different key. The file is gitignored and"
  echo "will never be committed."
  echo
  read -r -p "Create a new keystore? [Y/n] " create
  if [[ ! "$create" =~ ^[Yy]?$ ]]; then
    echo "Aborting."
    exit 1
  fi

  echo
  echo "keytool will prompt you for a keystore password (typed blind, confirmed twice)."
  echo "When asked for the key password for '$ALIAS', press Enter to reuse it."
  echo
  keytool -genkeypair -v \
    -keystore "$KEYSTORE" \
    -alias "$ALIAS" \
    -keyalg RSA \
    -keysize 2048 \
    -validity 10000 \
    -dname "CN=Ayne, OU=Mobile, O=Ayne App, L=Addis Ababa, C=ET"
fi

echo
echo "Keystore ready: $KEYSTORE"

# --- 2. Base64 encode ----------------------------------------------------------

echo "Encoding keystore for the KEYSTORE_BASE64 secret..."
base64 -w0 "$KEYSTORE" > "$B64_FILE"

# Round-trip check so a truncated secret never reaches GitHub.
if ! base64 -d "$B64_FILE" | cmp -s - "$KEYSTORE"; then
  echo "ERROR: base64 round-trip verification failed." >&2
  exit 1
fi
echo "Encoded and verified: $B64_FILE ($(wc -c < "$B64_FILE") bytes)"

# --- 3. GitHub secrets instructions --------------------------------------------

echo
echo "======================================"
echo " Next: add 4 secrets on GitHub"
echo "======================================"
echo
echo " 1. Open: https://github.com/Tegegndev/ayne-app/settings/secrets/actions"
echo " 2. Click 'New repository secret' for EACH row below:"
echo
echo "    Name              Value"
echo "    ----------------  ----------------------------------------------------"
echo "    KEYSTORE_BASE64   full contents of: $B64_FILE"
echo "    STORE_PASSWORD    the keystore password you just set"
echo "    KEY_ALIAS         $ALIAS"
echo "    KEY_PASSWORD      the key password you just set"
echo
echo " Copy the base64 value with one of:"
echo "    xclip -selection clipboard < my-upload-key.jks.b64   # X11"
echo "    wl-copy < my-upload-key.jks.b64                      # Wayland"
echo "    cat my-upload-key.jks.b64                            # then select & copy"
echo
echo " 3. After all 4 secrets are added, delete the .b64 file:"
echo "    rm $B64_FILE"
echo
echo " 4. Test it: push a tag (e.g. ./scripts/release.sh 1.0.0 --push) and watch"
echo "    https://github.com/Tegegndev/ayne-app/actions"
