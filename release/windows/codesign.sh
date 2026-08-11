#!/usr/bin/env bash

# Authenticode signing for the Windows packages, sourced by package.sh.
#
# husi releases are signed with a self-signed code signing certificate. That
# buys nothing from SmartScreen, but it is what lets the privileged daemon bind
# husi-core.exe to the husicore.dll sitting next to it: the Go side verifies
# that both carry the same signer certificate, the way sing-box's boxdd does.
# Every PE in one build therefore has to be signed with the same certificate.
#
# Signing is on by default. Building without a certificate is allowed, but it
# has to be asked for with --no-sign, so an unsigned release is never an
# accident.
#
# Configuration:
#   WINDOWS_SIGNING_P12            path to a PKCS#12 bundle
#   WINDOWS_SIGNING_P12_BASE64     the same bundle, base64 encoded (for CI)
#   WINDOWS_SIGNING_P12_PASSWORD   its password
#   WINDOWS_SIGNING_TIMESTAMP_URL  RFC3161 timestamp server, empty to skip
#   HUSI_WINDOWS_NO_SIGN=1         same as --no-sign

SIGNING_ENABLED=1
SIGNING_P12=""
SIGNING_P12_PASSWORD=""
SIGNING_TIMESTAMP_URL_DEFAULT="http://timestamp.digicert.com"
SIGNING_TIMESTAMP_URL=""
SIGNING_TOOL="osslsigncode"

resolve_signing() {
    local work_dir="$1"

    if [[ "${HUSI_WINDOWS_NO_SIGN:-0}" == "1" ]]; then
        SIGNING_ENABLED=0
    fi
    if [[ "$SIGNING_ENABLED" -eq 0 ]]; then
        log "Code signing disabled: packages will be unsigned."
        return
    fi

    SIGNING_P12="${WINDOWS_SIGNING_P12:-}"
    if [[ -z "$SIGNING_P12" && -n "${WINDOWS_SIGNING_P12_BASE64:-}" ]]; then
        SIGNING_P12="$work_dir/windows-signing.p12"
        if ! printf '%s' "$WINDOWS_SIGNING_P12_BASE64" | base64 -d > "$SIGNING_P12"; then
            error "WINDOWS_SIGNING_P12_BASE64 is not valid base64."
            exit 1
        fi
    fi

    if [[ -z "$SIGNING_P12" ]]; then
        error "No signing certificate configured."
        error "Set WINDOWS_SIGNING_P12 (or WINDOWS_SIGNING_P12_BASE64), or pass --no-sign to build unsigned packages."
        exit 1
    fi
    if [[ ! -f "$SIGNING_P12" ]]; then
        error "Signing certificate not found: $SIGNING_P12"
        exit 1
    fi

    SIGNING_P12_PASSWORD="${WINDOWS_SIGNING_P12_PASSWORD:-}"
    SIGNING_TIMESTAMP_URL="${WINDOWS_SIGNING_TIMESTAMP_URL-$SIGNING_TIMESTAMP_URL_DEFAULT}"
}

require_signing_tool() {
    if [[ "$SIGNING_ENABLED" -eq 0 ]]; then
        return
    fi
    if command -v "$SIGNING_TOOL" >/dev/null 2>&1; then
        return
    fi
    error "Missing required tool: $SIGNING_TOOL. Install the osslsigncode package, or pass --no-sign."
    exit 2
}

# sign_pe <file> signs a portable executable in place.
sign_pe() {
    local path="$1"
    local signed_path="$path.signed"
    local -a arguments=(
        sign
        -pkcs12 "$SIGNING_P12"
        -pass "$SIGNING_P12_PASSWORD"
        -h sha256
        -n "$APP_NAME"
        -i "$APP_URL"
        -in "$path"
        -out "$signed_path"
    )

    if [[ -n "$SIGNING_TIMESTAMP_URL" ]]; then
        arguments+=(-ts "$SIGNING_TIMESTAMP_URL")
    fi

    rm -f "$signed_path"
    if ! "$SIGNING_TOOL" "${arguments[@]}"; then
        rm -f "$signed_path"
        error "Failed to sign: $path"
        exit 1
    fi
    mv -f "$signed_path" "$path"
    log "Signed: $(basename "$path")"
}

# sign_payloads stages the launcher, shim and core library into work_dir, signs
# the copies and repoints the INPUT_* variables at them. Both the zip rootfs and
# the NSIS template read those variables, so signing them once covers both
# formats. The originals under libcore/build and launcher/zig-out are left
# alone: signing build outputs in place would re-sign them on the next run.
sign_payloads() {
    local work_dir="$1"
    local staging="$work_dir/signed"
    local path

    if [[ "$SIGNING_ENABLED" -eq 0 ]]; then
        return
    fi

    mkdir -p "$staging"
    for variable in INPUT_LAUNCHER_BIN INPUT_CORE_BIN INPUT_CORE_LIB; do
        path="$staging/$(basename "${!variable}")"
        cp "${!variable}" "$path"
        sign_pe "$path"
        printf -v "$variable" '%s' "$path"
    done
}
