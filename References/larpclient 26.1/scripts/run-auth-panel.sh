#!/usr/bin/env bash
set -euo pipefail

ROOT="$(git rev-parse --show-toplevel)"
cd "$ROOT/larp-auth-panel"
exec npm run dev -- "$@"
