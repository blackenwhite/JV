#!/usr/bin/env bash
set -euo pipefail
SCRIPT_DIR="$(cd "$(dirname "$0")" && pwd)"
PROJECT_DIR="$(cd "$SCRIPT_DIR/.." && pwd)"
REPO_ROOT="$(cd "$PROJECT_DIR/../.." && pwd)"

cd "$REPO_ROOT"
./gradlew :system-design-arpit-bh:compileJava

cd "$PROJECT_DIR"
java -cp build/classes/java/main com.nabajyoti.systemdesign.week6.bloom.FprExperiment
python3 week6-bloom/plot_fpr.py