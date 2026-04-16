#!/usr/bin/env bash
set -euo pipefail

ROOT="$(git rev-parse --show-toplevel)"

git -C "$ROOT" push origin main
"$ROOT/scripts/publish-public-mod.sh"
"$ROOT/scripts/publish-addon.sh"
