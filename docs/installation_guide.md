# App Installation Guide

This document outlines the steps to install and update the Golf Tracker application on a physical Android device or an emulator.

## Initial Installation (Fresh Install)

A fresh install will completely wipe any existing data and re-seed the database with the default Pebble Beach course and sample round.

### Option 1: Direct Install via Android Studio (Recommended)

This is the easiest method since the project is already open on your Mac.

1. **Enable Developer Options on your phone:**
   * Go to **Settings > About Phone**.
   * Scroll down to **Build Number** and tap it 7 times until you see a message saying "You are now a developer!"
2. **Enable USB Debugging:**
   * Go back to the main Settings menu, tap **System > Developer Options**.
   * Toggle **USB Debugging** to ON.
3. **Connect and Install:**
   * Connect your phone to your Mac using a USB cable. (If a prompt appears on your phone asking to "Allow USB debugging," tap **Allow** or **OK**).
   * Open the **Golf Tracker** project in Android Studio.
   * Look at the top toolbar in Android Studio; your physical device should now appear in the device dropdown menu (next to the green "Play" button).
   * Select your phone and click the **Run** button (the green play arrow). Android Studio will build the app and install it directly onto your phone.

### Option 2: Transfer the APK File

If you prefer to install the app manually via the APK file:

1. **Build the APK:**
   Open a terminal in the project directory and run:
   ```bash
   ./gradlew assembleDebug
   ```
2. **Locate the APK:** 
   The built app file is located at:
   `/Users/bveber/antigravity/golf_tracker/app/build/outputs/apk/debug/app-debug.apk`
3. **Transfer to your phone:**
   Send this `app-debug.apk` file to your phone (via Google Drive, email, Android File Transfer, etc.).
4. **Install on your phone:**
   * Open the file on your phone through your file manager or browser downloads.
   * If prompted about "Unknown Sources," tap **Settings** and toggle **"Allow from this source"**.
   * Tap **Install**.

---

## Updating the App (Keeping Your Data)

If you have been using the app and logging real rounds, you **do not** want to perform a fresh install, as it will wipe your database. To install an updated version of the app while preserving all your saved courses, rounds, and statistics, use the "replace" (`-r`) flag with `adb`.

### Updating via Terminal (Recommended for preserving data)

1. **Connect your phone** via USB (ensure USB Debugging is ON).
2. **Build the latest APK:**
   ```bash
   ./gradlew assembleDebug
   ```
3. **Install the update using `adb` with the `-r` flag:**
   ```bash
   adb install -r app/build/outputs/apk/debug/app-debug.apk
   ```
   *The `-r` flag stands for "replace" and tells Android to update the existing application, keeping all its data intact.*

### Updating via Android Studio

By default, simply clicking the **Run** button (the green play arrow) in Android Studio while your phone is connected will perform an update installation and **will preserve your data**. 

> [!WARNING]
> Do NOT use the `uninstallDebug` gradle task or click the "Clean Project" option followed by reinstalling, as this will wipe your app's data. Only use standard `installDebug` or the `adb install -r` command shown above.

## Database Migrations

If a new version of the app includes structural changes to the database (e.g., adding a new column like `chipLie`), the app uses Room migrations to update the database schema without losing your existing data.

As long as you use the update methods outlined above (Android Studio "Run" or `adb install -r`), the migrations will run automatically upon the first launch of the updated app, preserving your history.

---

## Troubleshooting Build Issues

If you encounter unexpected build errors or want to ensure a completely fresh installation without using any cached data, follow these steps.

### Perform a Clean Build (No Cache)

To build the app while ignoring the Gradle build cache and cleaning previous outputs:

```bash
./gradlew clean assembleDebug --no-build-cache
```

*   `clean`: Deletes the `build` directory.
*   `assembleDebug`: Builds the debug APK.
*   `--no-build-cache`: Tells Gradle not to use any cached task outputs from previous builds.

### Clear Dependency Cache

If you are having issues with library dependencies (e.g., a corrupted download or a missing version), you can force Gradle to refresh them:

```bash
./gradlew assembleDebug --refresh-dependencies
```

### Clearing the Global Gradle Cache (Nuclear Option)

In extreme cases where the above doesn't work, you can manually delete the Gradle cache directory on your Mac:

```bash
rm -rf ~/.gradle/caches/
```

> [!CAUTION]
> Deleting the global caches will cause Gradle to re-download all dependencies for EVERY project on your machine the next time you build them. This can take a significant amount of time and bandwidth.
