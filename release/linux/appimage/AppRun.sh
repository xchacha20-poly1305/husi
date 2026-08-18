#!/bin/sh
# AppImage entry point. Rendered and installed as AppRun at the AppDir root.
#
# The bundled runtime is the whole point of the AppImage: it is the one Linux
# artifact that does not ask the machine for a Java 21. The launcher reads
# JAVA_HOME before anything else (see selectJavaCommand in launcher/src/main.zig),
# so pointing that at the bundled image is all the redirection needed.
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
