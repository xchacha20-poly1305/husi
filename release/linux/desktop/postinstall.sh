#!/bin/sh
set -e

# Shared install/upgrade hook for deb postinst, rpm %posttrans and pacman
# post_install / post_upgrade. The work is identical every time and has to be
# idempotent regardless, so one script serves all four.

DAEMON_USER="husi"
UNIT="husi-daemon.service"
STATE_DIR="/var/lib/husi"
SYSUSERS_CONF="/usr/lib/sysusers.d/husi.conf"
DROPIN_DIR="/etc/systemd/system/$UNIT.d"
ROOT_FALLBACK="$DROPIN_DIR/10-root-fallback.conf"
CORE_PATH="__HUSI_CORE_PATH__"
SELINUX_MODULE="/usr/share/selinux/packages/husi.cil"
SELINUX_MODULE_PRIORITY=200

# The unit runs unprivileged and holds capabilities instead. A system that can
# create neither the account nor anything like it still gets a working daemon,
# just the root one every earlier release shipped.
ensure_daemon_user() {
    if id -u "$DAEMON_USER" >/dev/null 2>&1; then
        return 0
    fi
    if command -v systemd-sysusers >/dev/null 2>&1; then
        systemd-sysusers "$SYSUSERS_CONF" >/dev/null 2>&1 || true
    fi
    if id -u "$DAEMON_USER" >/dev/null 2>&1; then
        return 0
    fi
    useradd --system --user-group --no-create-home \
        --shell /usr/sbin/nologin --comment "Husi Core Daemon" \
        "$DAEMON_USER" >/dev/null 2>&1 || true
    id -u "$DAEMON_USER" >/dev/null 2>&1
}

# CapabilityBoundingSet=~ is the inverted empty set, meaning every capability:
# the root daemon still needs CAP_SETUID to drop plugin children to the owner.
# An empty assignment would mean the opposite and leave it with nothing.
write_root_fallback() {
    mkdir -p "$DROPIN_DIR"
    cat >"$ROOT_FALLBACK" <<'EOF'
# Written by the package because the husi account could not be created.
# The daemon keeps running as root, as it did before it learned to drop.
[Service]
User=
Group=
AmbientCapabilities=
CapabilityBoundingSet=~
DeviceAllow=
ProtectHome=no
ProtectSystem=no
EOF
}

if ensure_daemon_user; then
    rm -f "$ROOT_FALLBACK"
    rmdir "$DROPIN_DIR" 2>/dev/null || true
    if [ -d "$STATE_DIR" ]; then
        # Installs older than the unprivileged unit left root-owned state here.
        chown -R "$DAEMON_USER:$DAEMON_USER" "$STATE_DIR"
    fi
else
    echo "husi: could not create the $DAEMON_USER user, the daemon will run as root." >&2
    write_root_fallback
fi

# The package manager laid husi-core down with the label the policy had before
# the module existed, so the file has to be relabeled after loading it.
install_selinux_module() {
    if ! command -v selinuxenabled >/dev/null 2>&1 || ! selinuxenabled; then
        return 0
    fi
    if ! command -v semodule >/dev/null 2>&1; then
        return 0
    fi
    semodule -X "$SELINUX_MODULE_PRIORITY" -i "$SELINUX_MODULE" || {
        echo "husi: could not load the SELinux module, TUN mode may be denied." >&2
        return 0
    }
    restorecon -F "$CORE_PATH" || true
}

install_selinux_module

systemctl daemon-reload && systemctl enable "$UNIT" && systemctl restart "$UNIT" || true
