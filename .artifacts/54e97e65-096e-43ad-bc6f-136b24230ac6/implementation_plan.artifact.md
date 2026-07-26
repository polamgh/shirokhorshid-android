# Implementation Plan - Cleanup Build & Fix Sync

This plan aims to resolve the issues causing the "Play" button to be disabled by cleaning up the `app/build.gradle` file, removing redundant blocks, and ensuring the configuration is valid for Android Studio.

## Proposed Changes

### [Component] App Build Configuration
Clean up the `android` and `defaultConfig` blocks in `app/build.gradle`.

#### [MODIFY] [build.gradle](file:///Users/alighanavati/StudioProjects/shirokhorshid-android/app/build.gradle)
- Remove the malformed and redundant `externalNativeBuild` block inside `defaultConfig`.
- Move the 16 KB alignment flags (`-Wl,-z,max-page-size=16384` and `-Wl,-z,common-page-size=16384`) to the main `externalNativeBuild.ndkBuild` block using the correct syntax (`arguments`).
- Consolidate all NDK/Native build settings into one place.
- Ensure proper closing braces for all blocks.

## Verification Plan

### Automated Tests
- Run `gradlew clean assembleDebug` from the terminal to ensure the build script is logically correct.
- Perform a "Sync Project with Gradle Files" in Android Studio.

### Manual Verification
- Verify that the "app" configuration is selectable in the toolbar.
- Verify that the "Play" (Run) button becomes active (green).
