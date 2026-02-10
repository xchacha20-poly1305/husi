#!/usr/bin/env bash
set -euo pipefail

launcher_dir="$(cd -- "$(dirname -- "${BASH_SOURCE[0]}")" >/dev/null 2>&1 && pwd)"
app_home="$(cd -- "$launcher_dir/.." >/dev/null 2>&1 && pwd)"
jar_file="$app_home/app/__HUSI_PACKAGE_NAME__.jar"

if [ -n "${JAVA_HOME:-}" ] && [ -x "${JAVA_HOME}/bin/java" ]; then
    java_bin="${JAVA_HOME}/bin/java"
else
    java_bin="${JAVA:-java}"
fi

exec "$java_bin" -jar "$jar_file" "$@"
