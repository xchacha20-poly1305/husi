#!/bin/sh
# User-level installer shipped inside the relocatable tarball.
# Nothing here needs root: the app tree, the launcher symlink, the desktop
# entry and the icon all land under the user's own prefix. Only the optional
# system daemon asks for privileges, and it asks for them on its own.
set -eu

PACKAGE_NAME="__HUSI_PACKAGE_NAME__"
APP_NAME="__HUSI_APP_NAME__"

SOURCE_DIR="$(cd "$(dirname "$0")" && pwd)"
PREFIX="${HOME}/.local"
WITH_DAEMON=0
WITH_DESKTOP_ENTRY=1

log() {
    echo "[install] $*"
}

error() {
    echo "[install] $*" >&2
}

usage() {
    cat <<EOF
Usage: ./install.sh [--prefix DIR] [--with-daemon] [--no-desktop-entry]

Installs $APP_NAME for the current user only.

Options:
  --prefix DIR         Install prefix (default: \$HOME/.local).
  --with-daemon        Also install the system daemon, which enables TUN mode.
                       This is the one step that asks for administrator rights,
                       through pkexec. Settings can do it later instead.
  --no-desktop-entry   Skip the application menu entry and icon.
  -h, --help           Show this help.
EOF
}

# sed replacement text is not a plain string: these three characters steer it.
escape_for_sed_replacement() {
    printf '%s' "$1" | sed -e 's/[\\&#]/\\&/g'
}

require_source_layout() {
    if [ ! -x "$SOURCE_DIR/bin/$PACKAGE_NAME" ] || [ ! -f "$SOURCE_DIR/app/$PACKAGE_NAME.jar" ]; then
        error "This does not look like an unpacked $APP_NAME tarball: $SOURCE_DIR"
        exit 1
    fi
}

warn_when_root() {
    if [ "$(id -u)" -eq 0 ]; then
        log "Running as root. The deb / rpm / pacman packages are the better fit"
        log "for a system-wide install; this script is meant for a single user."
    fi
}

# The app tree is ours to replace wholesale, but only once it is recognisably
# ours — a mistyped --prefix should not take a directory with it.
remove_previous_install() {
    app_root="$1"
    if [ ! -e "$app_root" ]; then
        return
    fi
    if [ ! -f "$app_root/bin/husi-core" ] || [ ! -d "$app_root/app" ]; then
        error "Refusing to overwrite $app_root: it holds something other than $APP_NAME."
        exit 1
    fi
    rm -rf "$app_root"
}

install_app_tree() {
    app_root="$1"
    remove_previous_install "$app_root"
    mkdir -p "$app_root" "$PREFIX/bin"
    cp -a "$SOURCE_DIR/bin" "$SOURCE_DIR/app" "$app_root/"
    if [ -f "$SOURCE_DIR/uninstall.sh" ]; then
        # Kept next to the app so uninstalling survives deleting the tarball.
        cp "$SOURCE_DIR/uninstall.sh" "$app_root/uninstall.sh"
        chmod 755 "$app_root/uninstall.sh"
    fi
    ln -sfn "$app_root/bin/$PACKAGE_NAME" "$PREFIX/bin/$PACKAGE_NAME"
    log "Installed into $app_root"
}

# The shipped entry carries the system-wide Exec/Icon, which resolve through
# PATH and the icon theme. Neither is dependable for a user-level prefix, so
# both become absolute here.
install_desktop_entry() {
    app_root="$1"
    data_home="${XDG_DATA_HOME:-$HOME/.local/share}"
    source_entry="$SOURCE_DIR/share/applications/$PACKAGE_NAME.desktop"
    source_icon="$SOURCE_DIR/share/icons/hicolor/512x512/apps/$PACKAGE_NAME.png"
    target_entry="$data_home/applications/$PACKAGE_NAME.desktop"
    target_icon="$data_home/icons/hicolor/512x512/apps/$PACKAGE_NAME.png"

    if [ ! -f "$source_entry" ]; then
        log "No desktop entry in this tarball, skipping menu integration."
        return
    fi

    mkdir -p "$(dirname "$target_entry")"
    if [ -f "$source_icon" ]; then
        mkdir -p "$(dirname "$target_icon")"
        cp "$source_icon" "$target_icon"
        icon_value="$(escape_for_sed_replacement "$target_icon")"
    else
        icon_value="$(escape_for_sed_replacement "$PACKAGE_NAME")"
    fi
    exec_value="$(escape_for_sed_replacement "$app_root/bin/$PACKAGE_NAME")"

    sed -e "s#^Exec=.*#Exec=$exec_value open %u#" \
        -e "s#^Icon=.*#Icon=$icon_value#" \
        "$source_entry" >"$target_entry"

    update-desktop-database "$data_home/applications" >/dev/null 2>&1 || true
    gtk-update-icon-cache -f -t "$data_home/icons/hicolor" >/dev/null 2>&1 || true
    log "Installed desktop entry $target_entry"
}

# TUN needs a privileged daemon; everything else runs fine without one, so a
# refused prompt is a skipped feature rather than a failed install.
install_daemon() {
    app_root="$1"
    if ! command -v pkexec >/dev/null 2>&1; then
        error "pkexec not found. Install the daemon by hand instead:"
        error "  sudo $app_root/bin/husi-core service install"
        return
    fi
    log "Installing the system daemon (this asks for administrator rights)…"
    if pkexec "$app_root/bin/husi-core" service install; then
        log "System daemon installed."
    else
        error "Daemon install did not complete. $APP_NAME still works as a local"
        error "proxy; TUN mode needs the daemon. You can retry from Settings."
    fi
}

report_path() {
    case ":$PATH:" in
        *":$PREFIX/bin:"*) ;;
        *)
            log "Note: $PREFIX/bin is not on your PATH, so \`$PACKAGE_NAME\` will not"
            log "resolve in a shell. The application menu entry works regardless."
            ;;
    esac
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
        --with-daemon)
            WITH_DAEMON=1
            shift
            ;;
        --no-desktop-entry)
            WITH_DESKTOP_ENTRY=0
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

require_source_layout
warn_when_root

mkdir -p "$PREFIX"
PREFIX="$(cd "$PREFIX" && pwd)"
APP_ROOT="$PREFIX/lib/$PACKAGE_NAME"

install_app_tree "$APP_ROOT"
if [ "$WITH_DESKTOP_ENTRY" -eq 1 ]; then
    install_desktop_entry "$APP_ROOT"
fi
if [ "$WITH_DAEMON" -eq 1 ]; then
    install_daemon "$APP_ROOT"
fi
report_path

log "Done. Start it with: $APP_ROOT/bin/$PACKAGE_NAME"
if [ "$WITH_DAEMON" -eq 0 ]; then
    log "TUN mode needs the system daemon: rerun with --with-daemon, or install"
    log "it from Settings inside the app."
fi
log "Uninstall with: $APP_ROOT/uninstall.sh --prefix $PREFIX"
