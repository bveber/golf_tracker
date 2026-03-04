#!/bin/bash

echo "Force closing any running emulators..."
pkill -9 -f emulator

echo "Waiting for processes to clear..."
sleep 2

echo "Starting emulator with -no-snapshot-load to fix black screen..."
~/Library/Android/sdk/emulator/emulator -avd Medium_Phone_API_36.1 -no-snapshot-load &

echo "Emulator started. Please wait for boot."
