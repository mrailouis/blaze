#!/usr/bin/env bash
set -euo pipefail

if [[ $# -lt 2 ]]; then
    echo "Usage: $0 <database> <sql-file> [wrangler d1 execute args...]"
    echo "Example: $0 larp-auth-db larp-auth-panel/migrations/2026-04-09_wiki_framework.sql --remote"
    exit 1
fi

database="$1"
sql_file="$2"
shift 2

if [[ ! -f "$sql_file" ]]; then
    echo "SQL file not found: $sql_file" >&2
    exit 1
fi

python3 - "$database" "$sql_file" "$@" <<'PY'
import sqlite3
import subprocess
import sys
from pathlib import Path

database = sys.argv[1]
sql_path = Path(sys.argv[2])
extra_args = sys.argv[3:]
buffer = ""
statements: list[str] = []

def is_comment_only_sql(text: str) -> bool:
    for raw_line in text.splitlines():
        line = raw_line.strip()
        if not line:
            continue
        if line.startswith("--"):
            continue
        return False
    return True

def strip_comment_lines(text: str) -> str:
    kept_lines: list[str] = []
    for raw_line in text.splitlines():
        line = raw_line.strip()
        if not line:
            continue
        if line.startswith("--"):
            continue
        kept_lines.append(raw_line)
    return "\n".join(kept_lines).strip()

for line in sql_path.read_text().splitlines(keepends=True):
    buffer += line
    if sqlite3.complete_statement(buffer):
        statement = buffer.strip()
        if statement and not is_comment_only_sql(statement):
            cleaned = strip_comment_lines(statement)
            if cleaned:
                statements.append(cleaned)
        buffer = ""

if buffer.strip() and not is_comment_only_sql(buffer):
    raise SystemExit(f"Incomplete SQL statement at end of file: {sql_path}")

if not statements:
    raise SystemExit(f"No SQL statements found in {sql_path}")

for index, statement in enumerate(statements, start=1):
    print(f"Applying statement {index}/{len(statements)}")
    subprocess.run(
        ["npx", "wrangler", "d1", "execute", database, *extra_args, "--command", statement],
        check=True,
    )

print(f"Applied {len(statements)} statements from {sql_path}")
PY
