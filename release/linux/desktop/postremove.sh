#!/bin/sh
set -e

# Shared uninstall cleanup for deb postrm, rpm %postun, and pacman post_remove.
# Argument conventions:
#   deb:    $1 is remove|purge|upgrade|...
#   rpm:    $1 is remaining package count (0 = last uninstall; >=1 = upgrade)
#   pacman: post_remove runs only on real uninstall; $1 is the old version
# Upgrade restart is postinstall/posttrans/postupgrade — never stop the unit here.

case "$1" in
    0 | remove | purge) ;;
    upgrade | failed-upgrade | abort-install | abort-upgrade | disappear)
        exit 0
        ;;
    *[!0-9]*) ;; # pacman old-version string — post_remove only runs on real uninstall
    *) exit 0 ;; # rpm remaining package count >= 1 — upgrade, keep the unit
esac

systemctl disable --now husi-daemon.service 2>/dev/null || true
rm -f /etc/systemd/system/husi-daemon.service
systemctl daemon-reload 2>/dev/null || true
rm -f /run/husi/api.sock
