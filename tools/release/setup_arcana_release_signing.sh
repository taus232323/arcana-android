#!/usr/bin/env bash

# Copyright (c) 2026 Element Creations Ltd.
#
# SPDX-License-Identifier: AGPL-3.0-only OR LicenseRef-Element-Commercial.
# Please see LICENSE files in the repository root for full details.

set -euo pipefail

ROOT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")/../.." && pwd)"
KEYSTORE_PATH="${ROOT_DIR}/app/signature/release.keystore"
LOCAL_PROPERTIES="${ROOT_DIR}/local.properties"
CREDENTIALS_FILE="${ROOT_DIR}/app/signature/release-credentials.txt"
KEY_ALIAS="arcana"
NON_INTERACTIVE=0
FORCE=0

for arg in "$@"; do
    case "${arg}" in
        --non-interactive) NON_INTERACTIVE=1 ;;
        --force) FORCE=1 ;;
    esac
done

if [[ -f "${KEYSTORE_PATH}" && "${FORCE}" != "1" ]]; then
    printf "Release keystore already exists at %s\n" "${KEYSTORE_PATH}"
    printf "Run with --force to replace it.\n"
    exit 1
fi

if [[ -f "${KEYSTORE_PATH}" && "${FORCE}" == "1" ]]; then
    rm -f "${KEYSTORE_PATH}"
fi

if [[ "${NON_INTERACTIVE}" == "1" ]]; then
    STORE_PASS="$(openssl rand -base64 24 | tr -d '/+=' | head -c 24)"
    KEY_PASS="${STORE_PASS}"
else
    printf "Create a Play Store upload keystore for Arcana.\n"
    printf "You will be prompted for passwords. Store them safely — Google Play needs the same upload key for all future releases.\n\n"

    read -r -s -p "Keystore password: " STORE_PASS
    printf "\n"
    read -r -s -p "Confirm keystore password: " STORE_PASS_CONFIRM
    printf "\n"
    if [[ "${STORE_PASS}" != "${STORE_PASS_CONFIRM}" ]]; then
        printf "Passwords do not match.\n"
        exit 1
    fi

    read -r -s -p "Key password (press Enter to match keystore password): " KEY_PASS
    printf "\n"
    if [[ -z "${KEY_PASS}" ]]; then
        KEY_PASS="${STORE_PASS}"
    fi
fi

keytool -genkeypair -v \
    -storetype PKCS12 \
    -keystore "${KEYSTORE_PATH}" \
    -alias "${KEY_ALIAS}" \
    -keyalg RSA \
    -keysize 2048 \
    -validity 10000 \
    -storepass "${STORE_PASS}" \
    -keypass "${KEY_PASS}" \
    -dname "CN=Arcana, OU=Mobile, O=Arcana, C=RU"

touch "${LOCAL_PROPERTIES}"
grep -v '^signing\.arcana\.release\.' "${LOCAL_PROPERTIES}" > "${LOCAL_PROPERTIES}.tmp" || true
mv "${LOCAL_PROPERTIES}.tmp" "${LOCAL_PROPERTIES}"

{
    printf '\n# Arcana Play Store release signing\n'
    printf 'signing.arcana.release.storeFile=signature/release.keystore\n'
    printf 'signing.arcana.release.keyId=%s\n' "${KEY_ALIAS}"
    printf 'signing.arcana.release.storePassword=%s\n' "${STORE_PASS}"
    printf 'signing.arcana.release.keyPassword=%s\n' "${KEY_PASS}"
} >> "${LOCAL_PROPERTIES}"

if [[ "${NON_INTERACTIVE}" == "1" ]]; then
    {
        printf 'Arcana release signing credentials\n'
        printf 'Store these in a password manager, then delete this file.\n\n'
        printf 'Keystore: app/signature/release.keystore\n'
        printf 'Key alias: %s\n' "${KEY_ALIAS}"
        printf 'Store password: %s\n' "${STORE_PASS}"
        printf 'Key password: %s\n' "${KEY_PASS}"
    } > "${CREDENTIALS_FILE}"
    chmod 600 "${CREDENTIALS_FILE}"
fi

printf "\nDone.\n"
printf "Keystore: %s\n" "${KEYSTORE_PATH}"
printf "Credentials saved to: %s\n" "${LOCAL_PROPERTIES}"
if [[ "${NON_INTERACTIVE}" == "1" ]]; then
    printf "One-time credentials file: %s\n" "${CREDENTIALS_FILE}"
fi
printf "\nBuild a signed bundle with:\n"
printf "  ./gradlew bundleGplayRelease\n"
