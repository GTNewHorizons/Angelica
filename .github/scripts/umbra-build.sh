#!/usr/bin/env bash
set -euo pipefail

REPO_ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")/../.." && pwd)"
cd "$REPO_ROOT/umbra"

gradle_args=(:compileJava :compileMixinJava :test --no-daemon --console=plain --stacktrace)
if [[ -n "${UMBRA_GRADLE_ARGS:-}" ]]; then
    read -ra extra_args <<< "$UMBRA_GRADLE_ARGS"
    gradle_args+=("${extra_args[@]}")
fi

sh ./gradlew "${gradle_args[@]}"
