#!/usr/bin/env bash
set -euo pipefail

ROOT="$(git rev-parse --show-toplevel)"
REMOTE="${1:-public-addon}"
BRANCH="${2:-main}"
TMP_DIR="$(mktemp -d)"

cleanup() {
    rm -rf "$TMP_DIR"
}

trap cleanup EXIT

AUTHOR_NAME="$(git -C "$ROOT" config user.name || true)"
AUTHOR_EMAIL="$(git -C "$ROOT" config user.email || true)"

if [[ -z "$AUTHOR_NAME" ]]; then
    AUTHOR_NAME="LarpClient Workspace"
fi

if [[ -z "$AUTHOR_EMAIL" ]]; then
    AUTHOR_EMAIL="workspace@example.invalid"
fi

git -C "$ROOT" archive HEAD --format=tar \
    .editorconfig \
    .gitattributes \
    .github \
    .gitignore \
    LICENSE \
    README.md \
    build.gradle.kts \
    settings.gradle.kts \
    gradle \
    gradle.properties \
    gradlew \
    gradlew.bat \
    docs \
    larp \
    larp-addon \
    scripts \
    | tar -xf - -C "$TMP_DIR"

git -C "$TMP_DIR" init -q
git -C "$TMP_DIR" add .
git -C "$TMP_DIR" -c user.name="$AUTHOR_NAME" -c user.email="$AUTHOR_EMAIL" commit -q -m "Publish addon workspace from $(git -C "$ROOT" rev-parse --short HEAD)"
git -C "$TMP_DIR" remote add origin "$(git -C "$ROOT" remote get-url "$REMOTE")"
git -C "$TMP_DIR" push --force origin "HEAD:${BRANCH}"
