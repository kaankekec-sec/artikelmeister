#!/bin/bash
# Compiles the pure logic files together with the test runner and executes it.
set -euo pipefail
cd "$(dirname "$0")/.."
OUT="${TMPDIR:-/tmp}/artikelmeister_logic_tests"
mkdir -p "$OUT"
swiftc -module-cache-path "$OUT/ModuleCache" \
    Sources/Models.swift Sources/CardLogic.swift Tests/main.swift \
    -o "$OUT/logic_tests"
"$OUT/logic_tests"
