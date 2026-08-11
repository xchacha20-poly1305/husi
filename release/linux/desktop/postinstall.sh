#!/bin/sh
set -e

systemctl daemon-reload && systemctl enable husi-daemon.service && systemctl restart husi-daemon.service || true
