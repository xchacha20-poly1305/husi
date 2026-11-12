#!/bin/sh
set -eu

JAVA_HOME=/app/lib/jvm
export JAVA_HOME
exec /app/lib/__HUSI_PACKAGE_NAME__/bin/__HUSI_PACKAGE_NAME__ "$@"
