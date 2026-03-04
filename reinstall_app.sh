#!/bin/bash
PACKAGE="com.golftracker"

echo "Uninstalling existing app ($PACKAGE)..."
~/Library/Android/sdk/platform-tools/adb uninstall $PACKAGE

echo "Building and installing debug APK..."
./gradlew installDebug

echo "Done. App reinstalled."
