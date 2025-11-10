# RaiChess (来Chess) - Android Testing Instructions

## Quick Start Testing

This guide provides step-by-step instructions for testing the RaiChess Android application on physical devices and emulators.

---

## Table of Contents

1. [Prerequisites](#prerequisites)
2. [Testing on Android Emulator](#testing-on-android-emulator)
3. [Testing on Physical Android Device](#testing-on-physical-android-device)
4. [Building the APK](#building-the-apk)
5. [Running Tests](#running-tests)
6. [Troubleshooting](#troubleshooting)

---

## Prerequisites

Before testing, ensure you have:

- **Android Studio** Hedgehog (2023.1.1) or newer
- **JDK 17** configured in Android Studio
- **Android SDK** with API 24+ installed
- **Android NDK** (for future Stockfish integration)
- **Git** installed and configured

### Installing Prerequisites

#### 1. Install Android Studio

Download from: https://developer.android.com/studio

#### 2. Install Required SDK Components

In Android Studio:
1. Go to **Tools → SDK Manager**
2. In **SDK Platforms** tab, install:
   - Android 14.0 (API 34) - for compileSdk
   - Android 7.0 (API 24) - minimum supported version
3. In **SDK Tools** tab, install:
   - Android SDK Build-Tools
   - Android Emulator
   - Android SDK Platform-Tools
   - NDK (Side by side)

#### 3. Verify Java Version

```bash
java -version
# Should show Java 17 or higher
```

---

## Testing on Android Emulator

### Creating an Emulator (AVD)

1. **Open AVD Manager:**
   - In Android Studio: **Tools → Device Manager**
   - Or click the device icon in the toolbar

2. **Create New Virtual Device:**
   - Click **"Create Device"**
   - Select a device (recommended: **Pixel 5** or **Pixel 7**)
   - Click **"Next"**

3. **Select System Image:**
   - Choose **API Level 34** (Android 14.0)
   - Select an image with **Google Play** (if available)
   - Download if not already installed
   - Click **"Next"**

4. **Configure AVD:**
   - Give it a name (e.g., "RaiChess Test Device")
   - **Graphics:** Hardware - GLES 2.0
   - **RAM:** 2048 MB or more
   - **VM heap:** 512 MB
   - **Internal Storage:** 2048 MB
   - Click **"Finish"**

### Running on Emulator

#### Method 1: Using Android Studio

1. **Open the project** in Android Studio
2. Wait for Gradle sync to complete
3. **Select your emulator** from the device dropdown
4. Click the **"Run"** button (green triangle) or press **Shift+F10**

#### Method 2: Using Command Line

```bash
# Navigate to project directory
cd /path/to/RaiChess

# List available emulators
emulator -list-avds

# Start emulator
emulator -avd <emulator-name>

# In another terminal, build and install
./gradlew installDebug

# Or build and run
./gradlew assembleDebug
adb install -r app/build/outputs/apk/debug/app-debug.apk
```

### Expected Results

When the app launches on the emulator, you should see:
- **Pure black background** (OLED optimized)
- **"RAICHESS"** in large white text
- **"来Chess"** with Japanese kanji
- **"The Next Chess App"** tagline
- **"Development in Progress"** status message

---

## Testing on Physical Android Device

### Enabling Developer Options

#### On Most Android Devices:

1. Go to **Settings**
2. Scroll to **About Phone** (or **About Device**)
3. Find **Build Number**
4. **Tap Build Number 7 times** rapidly
5. You'll see a message: "You are now a developer!"

#### Device-Specific Instructions:

- **Samsung:** Settings → About Phone → Software Information → Build Number
- **Google Pixel:** Settings → About Phone → Build Number
- **OnePlus:** Settings → About Phone → Build Number
- **Xiaomi/MIUI:** Settings → About Phone → MIUI Version (tap 7 times)

### Enabling USB Debugging

1. Go to **Settings → Developer Options**
2. Turn on **"Developer Options"** (toggle at top)
3. Enable **"USB Debugging"**
4. (Optional) Enable **"Install via USB"** if available

### Connecting Device to Computer

#### Windows:

1. Connect device via USB cable
2. Install device drivers if prompted
3. On device, accept the "Allow USB Debugging" prompt
4. Check connection: Open Command Prompt and run:
   ```bash
   adb devices
   ```
   You should see your device listed

#### macOS/Linux:

1. Connect device via USB cable
2. On device, accept the "Allow USB Debugging" prompt
3. Verify connection:
   ```bash
   adb devices
   ```
   You should see your device listed

### Installing and Running

#### Method 1: Using Android Studio

1. Open the project
2. Connect your device via USB
3. Select your device from the device dropdown
4. Click **"Run"** button (green triangle)
5. The app will build and install on your device

#### Method 2: Using Command Line

```bash
# Check device is connected
adb devices

# Build and install
./gradlew installDebug

# Or build APK and install manually
./gradlew assembleDebug
adb install -r app/build/outputs/apk/debug/app-debug.apk

# Launch the app
adb shell am start -n com.raichess/.MainActivity
```

---

## Building the APK

### Debug APK (for testing)

```bash
# Build debug APK
./gradlew assembleDebug

# Output location:
# app/build/outputs/apk/debug/app-debug.apk
```

### Release APK (for distribution)

```bash
# Build release APK (unsigned)
./gradlew assembleRelease

# Output location:
# app/build/outputs/apk/release/app-release-unsigned.apk
```

**Note:** Release APKs need to be signed before distribution. For now, use debug builds for testing.

### Sharing APK for Testing

After building, you can share the APK:

```bash
# Find the APK
ls -lh app/build/outputs/apk/debug/app-debug.apk

# Transfer to device via adb
adb install app/build/outputs/apk/debug/app-debug.apk

# Or copy to computer to share
cp app/build/outputs/apk/debug/app-debug.apk ~/Desktop/RaiChess.apk
```

---

## Running Tests

### Unit Tests

Run unit tests on your development machine:

```bash
# Run all unit tests
./gradlew test

# Run tests for debug variant
./gradlew testDebugUnitTest

# View results in:
# app/build/reports/tests/testDebugUnitTest/index.html
```

### Instrumentation Tests (On Device/Emulator)

Run tests that require Android framework:

```bash
# Ensure device/emulator is connected
adb devices

# Run instrumentation tests
./gradlew connectedAndroidTest

# View results in:
# app/build/reports/androidTests/connected/index.html
```

### Running Specific Test Classes

```bash
# Run specific test class
./gradlew test --tests com.raichess.domain.model.EloCalculatorTest

# Run specific test method
./gradlew test --tests com.raichess.domain.model.EloCalculatorTest.testCalculateNewElo
```

---

## Troubleshooting

### Issue: Gradle Sync Failed

**Solution:**
```bash
# Invalidate caches and restart
# In Android Studio: File → Invalidate Caches → Invalidate and Restart

# Or use command line
./gradlew clean
rm -rf .gradle
./gradlew build --refresh-dependencies
```

### Issue: Device Not Detected

**Solution:**

1. **Check USB cable:** Use a data cable, not charge-only
2. **Enable USB Debugging:** Settings → Developer Options → USB Debugging
3. **Revoke USB Debugging authorizations:**
   - Settings → Developer Options → Revoke USB Debugging Authorizations
   - Reconnect device and accept prompt
4. **Restart ADB:**
   ```bash
   adb kill-server
   adb start-server
   adb devices
   ```

### Issue: App Crashes on Launch

**Solution:**

1. **Check LogCat:**
   ```bash
   adb logcat | grep -i raichess
   ```
2. **Clear app data:**
   ```bash
   adb shell pm clear com.raichess
   ```
3. **Reinstall:**
   ```bash
   adb uninstall com.raichess
   ./gradlew installDebug
   ```

### Issue: Build Failed - Kotlin Version Mismatch

**Solution:**
```bash
# Clean and rebuild
./gradlew clean
./gradlew build
```

### Issue: NDK Not Found (Future)

**Solution:**
1. Open Android Studio
2. Go to **Tools → SDK Manager → SDK Tools**
3. Check **"NDK (Side by side)"**
4. Click **"Apply"**

### Issue: Emulator is Slow

**Solution:**

1. **Allocate more RAM:**
   - In AVD Manager, edit your device
   - Increase RAM to 2048 MB or more
2. **Enable Hardware Acceleration:**
   - Use x86_64 system images (not ARM)
   - Ensure "Hardware - GLES 2.0" is selected for graphics
3. **Use a physical device** for better performance

### Issue: "Installation failed with message INSTALL_FAILED_INSUFFICIENT_STORAGE"

**Solution:**

1. **Free up device storage**
2. **Clear app cache:**
   ```bash
   adb shell pm clear com.raichess
   ```
3. **Uninstall old version:**
   ```bash
   adb uninstall com.raichess
   ```

---

## Testing Checklist

Use this checklist when testing the app:

### Basic Functionality
- [ ] App launches successfully
- [ ] No crash on startup
- [ ] UI displays correctly (black background, white text)
- [ ] App name and tagline are visible
- [ ] Japanese kanji (来) displays correctly

### Display Testing
- [ ] Pure black background on OLED devices
- [ ] High contrast white text is readable
- [ ] Layout adapts to different screen sizes
- [ ] Portrait orientation is enforced

### Performance Testing
- [ ] App starts within 2 seconds
- [ ] No lag in UI interactions
- [ ] Battery usage is minimal
- [ ] App size is under 10 MB (current stage)

### Device Compatibility
- [ ] Works on Android 7.0 (API 24)
- [ ] Works on Android 14 (API 34)
- [ ] Works on devices with different screen densities
- [ ] Works on different manufacturers (Samsung, Google, etc.)

---

## Next Steps for Development

### Current Status (MVP Phase 1 - Week 0)

✅ **Completed:**
- Project structure setup
- Build configuration
- Basic UI theme (Black & White OLED)
- ELO calculator implementation
- MainActivity with welcome screen

⏳ **Next Steps (See README.md roadmap):**
- Chess board UI component
- Stockfish integration
- Game logic implementation
- Database setup (Room)

### Future Testing Requirements

As development progresses, testing will include:

1. **Chess Board UI:**
   - Piece rendering
   - Move validation
   - Drag and drop functionality

2. **Stockfish Engine:**
   - AI move generation
   - Response time < 1 second
   - ELO-based difficulty levels

3. **Database:**
   - Game saving/loading
   - Performance with 1000+ games
   - Backup and restore

4. **Analysis:**
   - Post-game analysis accuracy
   - Move classification
   - Practice position generation

---

## Useful ADB Commands

```bash
# Check connected devices
adb devices

# Install APK
adb install -r app.apk

# Uninstall app
adb uninstall com.raichess

# Clear app data
adb shell pm clear com.raichess

# View app logs
adb logcat | grep -i raichess

# Take screenshot
adb shell screencap /sdcard/screenshot.png
adb pull /sdcard/screenshot.png

# Record video
adb shell screenrecord /sdcard/demo.mp4
# Press Ctrl+C to stop
adb pull /sdcard/demo.mp4

# Check app size
adb shell pm list packages | grep raichess
adb shell pm path com.raichess
adb shell ls -lh /data/app/com.raichess*/base.apk

# Get device info
adb shell getprop ro.build.version.release  # Android version
adb shell getprop ro.product.model           # Device model
adb shell wm size                             # Screen resolution
adb shell wm density                          # Screen density
```

---

## Contact & Support

For issues or questions:
- **GitHub Issues:** https://github.com/stringhamc/RaiChess/issues
- **Documentation:** See README.md, QUICKSTART.md, and ARCHITECTURE.md

---

**RaiChess (来Chess)** - The Next Chess App | Righteous! ♟️

*Rai (来) = "Next" in Japanese*
