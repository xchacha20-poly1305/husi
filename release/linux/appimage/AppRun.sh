#!/bin/sh
# AppImage entry point. Rendered and installed as AppRun at the AppDir root.
#
# Use the bundled runtime without requiring a system Java installation.
# The launcher reads JAVA_HOME first (see selectJavaCommand in launcher/src/main.zig).
set -eu

PACKAGE_NAME="__HUSI_PACKAGE_NAME__"

# The AppImage runtime exports APPDIR; the fallback covers running an extracted
# AppDir directly, which is how the packaging is usually smoke tested.
if [ -z "${APPDIR:-}" ]; then
    APPDIR="$(dirname "$(readlink -f "$0")")"
fi

if [ -x "$APPDIR/usr/lib/jvm/bin/java" ]; then
    JAVA_HOME="$APPDIR/usr/lib/jvm"
    export JAVA_HOME
fi

exec "$APPDIR/usr/lib/$PACKAGE_NAME/bin/$PACKAGE_NAME" "$@"
