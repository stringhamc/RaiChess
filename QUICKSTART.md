# RaiChess (来Chess) - Quick Start Guide

**Rai (来)** = "Next" in Japanese | **RaiChess** = Righteous Chess!

This guide will help you set up the RaiChess project and start development.

## Prerequisites Checklist

- [ ] Android Studio Hedgehog (2023.1.1) or newer installed
- [ ] JDK 17 configured in Android Studio
- [ ] Android SDK with API 24+ installed
- [ ] Android NDK installed (for Stockfish compilation)
- [ ] Git installed and configured

## Initial Setup (5 minutes)

### 1. Clone the Repository

```bash
git clone https://github.com/yourusername/raichess.git
cd raichess
```

### 2. Open in Android Studio

1. Launch Android Studio
2. Click "Open" (or File → Open)
3. Navigate to the `raichess` directory
4. Click "OK"
5. Wait for Gradle sync to complete (1-2 minutes)

### 3. Configure NDK (for Stockfish)

In Android Studio:
1. Go to File → Project Structure → SDK Location
2. Under "Android NDK location", ensure NDK is installed
3. If not installed: Tools → SDK Manager → SDK Tools → NDK (Side by side)

## Building Stockfish (Required)

Stockfish must be compiled for Android before running the app.

### Option A: Using Build Script (Recommended)

```bash
cd app/src/main/cpp/stockfish
chmod +x build-android.sh
./build-android.sh
```

This will build for all architectures (arm64-v8a, armeabi-v7a, x86_64, x86).

### Option B: Manual Build

If the script doesn't work:

```bash
export ANDROID_NDK=/path/to/your/ndk
cd app/src/main/cpp/stockfish

# Build for ARM64 (most modern devices)
$ANDROID_NDK/ndk-build \
    APP_ABI=arm64-v8a \
    APP_PLATFORM=android-24 \
    -C jni/
```

The compiled libraries will be placed in `app/src/main/jniLibs/`.

### Verify Build

Check that these files exist:
```
app/src/main/jniLibs/
├── arm64-v8a/
│   └── libstockfish.so
├── armeabi-v7a/
│   └── libstockfish.so
├── x86_64/
│   └── libstockfish.so
└── x86/
    └── libstockfish.so
```

## Running the App

### On Emulator

1. Create an AVD if you don't have one:
   - Tools → Device Manager → Create Device
   - Select Pixel 5 or similar (API 24+)
   - Download system image if needed

2. Run the app:
   - Click the "Run" button (green triangle)
   - Or press Shift+F10
   - Select your emulator from the device list

### On Physical Device

1. Enable Developer Options on your device:
   - Go to Settings → About Phone
   - Tap "Build Number" 7 times

2. Enable USB Debugging:
   - Settings → Developer Options → USB Debugging

3. Connect device via USB

4. Run the app:
   - Click "Run"
   - Select your device from the list

## Project Structure Overview

```
raichess/
├── app/
│   ├── src/main/
│   │   ├── java/com/raichess/
│   │   │   ├── data/          # Database, repositories, Stockfish
│   │   │   ├── domain/        # Business logic, use cases
│   │   │   └── ui/            # Screens, ViewModels, theme
│   │   ├── cpp/               # Stockfish native code
│   │   ├── res/               # Resources (strings, drawables)
│   │   └── AndroidManifest.xml
│   └── build.gradle.kts       # App dependencies
├── README.md                   # Project documentation
├── ARCHITECTURE.md             # Architecture details
├── CONTRIBUTING.md             # Contribution guidelines
├── TECHNICAL_PLAN.md           # Complete technical specification
├── LICENSE                     # GPL-3.0 (required for Stockfish)
└── build.gradle.kts           # Root build file
```

## First Steps for Development

### 1. Explore the Codebase

Start with these files to understand the project:

1. **UI Theme:** `app/src/main/java/com/raichess/ui/theme/Theme.kt`
   - Minimal black & white theme for OLED

2. **ELO System:** `app/src/main/java/com/raichess/domain/model/EloCalculator.kt`
   - Rating calculation logic

3. **Architecture:** Read `ARCHITECTURE.md` for the big picture

### 2. Run Tests

```bash
# Unit tests
./gradlew test

# Integration tests (requires device/emulator)
./gradlew connectedAndroidTest
```

### 3. Check Current Implementation Status

See the checklist in `README.md` for what's been implemented and what needs work.

## Common Issues and Solutions

### Issue: Gradle Sync Failed

**Solution:**
1. File → Invalidate Caches → Invalidate and Restart
2. Ensure you have JDK 17: File → Project Structure → SDK Location

### Issue: NDK Not Found

**Solution:**
1. Tools → SDK Manager → SDK Tools
2. Check "NDK (Side by side)"
3. Click "Apply"

### Issue: Stockfish Build Fails

**Solution:**
1. Check NDK path: `echo $ANDROID_NDK`
2. Try building for just arm64-v8a first
3. Check build logs in `app/src/main/cpp/stockfish/obj/`

### Issue: App Crashes on Launch

**Solution:**
1. Check LogCat for error messages
2. Verify Stockfish library is in `jniLibs/`
3. Clean and rebuild: Build → Clean Project, then Build → Rebuild Project

### Issue: Slow Emulator

**Solution:**
1. Use ARM64 system image with Google Play
2. Allocate more RAM to emulator (AVD settings)
3. Use physical device for better performance

## Development Workflow

1. **Pick a task:**
   - Check GitHub Issues for `good first issue` label
   - Or see roadmap in `README.md`

2. **Create a branch:**
   ```bash
   git checkout -b feature/your-feature-name
   ```

3. **Make changes:**
   - Write code
   - Add tests
   - Update documentation

4. **Test thoroughly:**
   ```bash
   ./gradlew test
   ./gradlew connectedAndroidTest
   ```

5. **Commit and push:**
   ```bash
   git add .
   git commit -m "feat: add feature description"
   git push origin feature/your-feature-name
   ```

6. **Create Pull Request:**
   - Go to GitHub
   - Click "New Pull Request"
   - Fill in description using PR template

## Key Dependencies

These are automatically downloaded by Gradle:

- **Jetpack Compose:** UI framework
- **Room:** Database (SQLite wrapper)
- **chesslib:** Chess move validation and PGN parsing
- **Coroutines:** Async operations
- **Material 3:** UI components

## Next Steps

After setup is complete:

1. **Read the documentation:**
   - `ARCHITECTURE.md` for design patterns
   - `CONTRIBUTING.md` for workflow
   - `TECHNICAL_PLAN.md` for detailed specs

2. **Explore the UI:**
   - Run the app and explore existing screens
   - Check `ui/theme/Theme.kt` for the black & white theme

3. **Start contributing:**
   - Pick an issue from GitHub
   - Read `CONTRIBUTING.md`
   - Submit your first PR!

## Getting Help

- **Issues:** Check [GitHub Issues](https://github.com/yourusername/raichess/issues)
- **Discussions:** [GitHub Discussions](https://github.com/yourusername/raichess/discussions)
- **Email:** your.email@example.com

## Useful Commands

```bash
# Clean build
./gradlew clean

# Build debug APK
./gradlew assembleDebug

# Build release APK
./gradlew assembleRelease

# Run tests
./gradlew test

# Check code style
./gradlew ktlintCheck

# Format code
./gradlew ktlintFormat

# Generate coverage report
./gradlew jacocoTestReport
```

---

Happy coding! 🎉♟️
