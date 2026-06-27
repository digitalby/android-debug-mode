# Debug Mode Widget

[<img src="https://fdroid.gitlab.io/artwork/badge/get-it-on.png" alt="Get it on F-Droid" height="80">](https://f-droid.org/packages/info.yuryv.androiddebugmode/)


A minimal Android home screen widget that shows USB and wireless ADB debugging status at a glance and taps through to Developer Options.

![CI](https://github.com/digitalby/android-debug-mode/actions/workflows/ci.yml/badge.svg)

## What it does

- Shows whether **USB Debugging** is on or off
- Shows whether **Wireless Debugging** is on or off (Android 11+ only)
- Tapping either row opens **Developer Options** directly
- **Running Services** shortcut opens the Running Services screen directly
- Refresh button re-reads current state without navigating away

The app has no launcher activity. It lives entirely as a home screen widget.

## Requirements

- Android 6.0+ (API 23)
- Wireless debugging row requires Android 11+ (API 30)

> The app reads `Settings.Global` to display state but does not modify any settings. Toggling requires navigating to Developer Options manually.

## Installation

**From F-Droid:** coming soon.

**From a release:**

Download `app-release.apk` from the [latest release](../../releases/latest), transfer to your device, and install it (enable "Install from unknown sources" if prompted).

**From source:**

```bash
./gradlew installDebug
```

Then long-press your home screen → **Widgets** → **Debug Mode** → drag to place.

## Tech

- [Jetpack Glance](https://developer.android.com/jetpack/compose/glance) — widget UI
- Kotlin, Gradle KTS
- minSdk 23 · compileSdk 36

## License

[MIT](LICENSE)
