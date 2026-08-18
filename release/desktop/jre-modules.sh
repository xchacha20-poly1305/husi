# Modules linked into the bundled runtimes (the Linux AppImage and the Windows
# JBR packages). Sourced by the packaging scripts, never executed, so it carries
# a shell directive instead of a shebang.
# shellcheck shell=bash

# Derived from
#   jdeps --print-module-deps --ignore-missing-deps --multi-release 21 <uber jar>
# and then widened by hand, because jdeps only sees static references:
#   jdk.crypto.ec     TLS key agreement, reached reflectively by the JSSE provider
#   jdk.unsupported   sun.misc.Unsafe, which Skiko and Compose Desktop reach for
#   jdk.charsets      GBK and friends — this app has a large Chinese audience
#   jdk.localedata    non-root locales, same reason
#   jdk.zipfs         the ZIP filesystem provider used to read resources
# Dropping any of these fails at runtime, not at link time, so widen rather
# than trim when in doubt.
DESKTOP_JRE_MODULES_COMMON="java.base,java.desktop,java.instrument,java.logging,java.management,java.naming,java.net.http,java.prefs,java.security.jgss,java.sql,jdk.accessibility,jdk.charsets,jdk.crypto.ec,jdk.localedata,jdk.unsupported,jdk.zipfs"

# jdk.crypto.mscapi is the SunMSCAPI provider. The Windows java.security lists
# it, and a missing provider is only skipped rather than reported, so link it in
# instead of leaving a silent hole in the security provider list.
DESKTOP_JRE_MODULES_WINDOWS="$DESKTOP_JRE_MODULES_COMMON,jdk.crypto.mscapi"
