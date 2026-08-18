#!/bin/sh
# Reverse of install.sh. Removes the per-user install; the system daemon, if
# one was installed, is removed through a separate privileged step.
set -eu

PACKAGE_NAME="__HUSI_PACKAGE_NAME__"
APP_NAME="__HUSI_APP_NAME__"

# Where husi-core copies itself when installed from a user-writable tree
# (defaultInstallBin in libcore/daemonhost/service_linux.go). A daemon that
# came from deb/rpm/pacman lives elsewhere and belongs to the package manager,
# so its presence must not drag it into this uninstall.
PORTABLE_DAEMON_BIN="/usr/local/libexec/husi/husi-core"

PREFIX="${HOME}/.local"
KEEP_DAEMON=0

log() {
    echo "[uninstall] $*"
}

error() {
    echo "[uninstall] $*" >&2
}

usage() {
    cat <<EOF
Usage: ./uninstall.sh [--prefix DIR] [--keep-daemon]

Removes the per-user install of $APP_NAME.

Options:
  --prefix DIR     Install prefix that was used (default: \$HOME/.local).
  --keep-daemon    Leave the system daemon installed.
  -h, --help       Show this help.
EOF
}

remove_app_tree() {
    app_root="$1"
    if [ -d "$app_root" ]; then
        rm -rf "$app_root"
        log "Removed $app_root"
    fi
    launcher_link="$PREFIX/bin/$PACKAGE_NAME"
    if [ -L "$launcher_link" ] || [ -f "$launcher_link" ]; then
        rm -f "$launcher_link"
    fi
}

remove_desktop_entry() {
    data_home="${XDG_DATA_HOME:-$HOME/.local/share}"
    rm -f "$data_home/applications/$PACKAGE_NAME.desktop"
    rm -f "$data_home/icons/hicolor/512x512/apps/$PACKAGE_NAME.png"
    update-desktop-database "$data_home/applications" >/dev/null 2>&1 || true
    gtk-update-icon-cache -f -t "$data_home/icons/hicolor" >/dev/null 2>&1 || true
}

# Runs before the app tree goes away: husi-core removes its own installed copy,
# so the binary has to still be there to be asked.
remove_daemon() {
    app_root="$1"
    if [ ! -e "$PORTABLE_DAEMON_BIN" ]; then
        return
    fi
    if [ ! -x "$app_root/bin/husi-core" ]; then
        error "The system daemon is still installed but $app_root/bin/husi-core is"
        error "gone. Remove it with: sudo husi-core service uninstall"
        return
    fi
    if ! command -v pkexec >/dev/null 2>&1; then
        error "pkexec not found. Remove the system daemon by hand:"
        error "  sudo $app_root/bin/husi-core service uninstall"
        return
    fi
    log "Removing the system daemon (this asks for administrator rights)…"
    if pkexec "$app_root/bin/husi-core" service uninstall; then
        log "System daemon removed."
    else
        error "The system daemon was left installed. Remove it later with:"
        error "  sudo husi-core service uninstall"
    fi
}

while [ $# -gt 0 ]; do
    case "$1" in
        --prefix)
            if [ $# -lt 2 ] || [ -z "$2" ]; then
                error "Missing value for --prefix."
                exit 1
            fi
            PREFIX="$2"
            shift 2
            ;;
        --keep-daemon)
            KEEP_DAEMON=1
            shift
            ;;
        -h|--help)
            usage
            exit 0
            ;;
        *)
            error "Unknown argument: $1"
            usage
            exit 1
            ;;
    esac
done

if [ ! -d "$PREFIX" ]; then
    error "Prefix not found: $PREFIX"
    exit 1
fi
PREFIX="$(cd "$PREFIX" && pwd)"
APP_ROOT="$PREFIX/lib/$PACKAGE_NAME"

if [ "$KEEP_DAEMON" -eq 0 ]; then
    remove_daemon "$APP_ROOT"
fi
remove_desktop_entry
remove_app_tree "$APP_ROOT"

log "Done. Your configuration in \${XDG_CONFIG_HOME:-\$HOME/.config}/husi was kept."
