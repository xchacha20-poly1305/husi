#!/usr/bin/env python3

import os
from shlex import quote
import subprocess
import sys

args = [
    os.environ["RUST_ANDROID_GRADLE_CC"],
    os.environ["RUST_ANDROID_GRADLE_CC_LINK_ARG"],
] + sys.argv[1:]


def update_in_place(arglist):
    # The `gcc` library is not included starting from NDK version 23.
    # Work around by using `unwind` replacement.
    ndk_major_version = os.environ["CARGO_NDK_MAJOR_VERSION"]
    if ndk_major_version.isdigit():
        if 23 <= int(ndk_major_version):
            for i, arg in enumerate(arglist):
                if arg.startswith("-lgcc"):
                    # This is one way to preserve line endings.
                    arglist[i] = "-lunwind" + arg[len("-lgcc"):]


update_in_place(args)

for arg in args:
    if arg.startswith("@"):
        with open(arg[1:], "r", encoding='utf-8') as f:
            fileargs = f.read().splitlines(keepends=True)
        update_in_place(fileargs)
        with open(arg[1:], "w", encoding='utf-8') as f:
            f.write("".join(fileargs))


# This only appears when the subprocess call fails, but it's helpful then.
printable_cmd = " ".join(quote(arg) for arg in args)
print(printable_cmd)

sys.exit(subprocess.call(args))