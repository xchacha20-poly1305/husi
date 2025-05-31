#!/usr/bin/env bash

TARGET_PACKAGE="$1"
if [ -z "$TARGET_PACKAGE" ]; then
    read -p "Please enter target package name: " TARGET_PACKAGE
fi
if [ -z "$TARGET_PACKAGE" ]; then
    echo "Target package should not be empty."
    exit 1
fi

SHORTCUTS_FILE="app/src/main/res/xml/shortcuts.xml"
sed -i "s/android:targetPackage=\"[^\"]*\"/android:targetPackage=\"$TARGET_PACKAGE\"/" "$SHORTCUTS_FILE"
echo "Updated $SHORTCUTS_FILE android:targetPackage to $TARGET_PACKAGE"

PROPERTIES_FILE="husi.properties"
sed -i "s#^PACKAGE_NAME=.*#PACKAGE_NAME=$TARGET_PACKAGE#" "$PROPERTIES_FILE"
echo "Updated $PROPERTIES_FILE  PACKAGE_NAME to $TARGET_PACKAGE"
