#!/bin/bash
PACKAGE="com.golftracker"

echo "Updating app ($PACKAGE) while preserving data..."
./gradlew installDebug

echo "Done. App updated (Data preserved)."
