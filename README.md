## apk_sideload

Install an Android APK from a file path using a simple Flutter API. This plugin wraps the Android package installer intent and handles `FileProvider` URIs and read permissions for you.

- **Platforms**: Android only
- **Min SDK**: 21+
- **Android target/compile SDK**: 34

### Features

- **Install from file path**: Trigger the system installer for a local APK file.
- **FileProvider support**: Works with files stored in app cache/files and external app-specific directories.
- **Permission flow**: Detects and directs users to enable “Install unknown apps” on Android 8.0+ when required.

### Installation

Add to your `pubspec.yaml`:

```yaml
dependencies:
  apk_sideload: ^0.0.1
```

Then run:

```bash
flutter pub get
```

### Android setup

This plugin ships with a `FileProvider` declaration inside the library manifest and a default `file_paths.xml`. No extra AndroidManifest.xml edits are required in your app.

Store the APK in one of the app-accessible directories defined in the provider paths, such as the temporary directory or app files directory. The plugin will generate a content URI and grant read permissions to the installer.

Recommended locations (via `path_provider`):

- `getTemporaryDirectory()`
- `getApplicationDocumentsDirectory()` / `getApplicationSupportDirectory()` (for internal files)
- `getExternalStorageDirectory()` or `getExternalStorageDirectories()` (app-specific external files)

Note about “Install unknown apps”: On Android 8.0+ (API 26+), users must allow your app to install unknown apps. If not granted, the plugin opens the Settings screen so the user can enable it and the installation would then start.

### Usage

Basic example installing an APK that you downloaded to the temp directory:

```dart
import 'dart:io';
import 'package:flutter/material.dart';
import 'package:apk_sideload/install_apk.dart';
import 'package:path_provider/path_provider.dart';

Future<void> installDownloadedApk() async {
  final dir = await getTemporaryDirectory();
  final filePath = '${dir.path}/app_update.apk';

  // Ensure the file exists at filePath (download it beforehand)
  if (File(filePath).existsSync()) {
    await InstallApk().installApk(filePath);
  } else {
    // Download the APK to filePath first, then call install
  }
}
```

See the example app in `example/lib/main.dart` for a simple downloader that saves the APK to cache and then calls `installApk(...)`.

### API

- `Future<String?> getPlatformVersion()`
  - Returns the Android version string (utility method).
- `Future<void> installApk(String filePath)`
  - Launches the system installer for the APK at `filePath`.
  - Throws a `PlatformException` with code `ARG_ERROR` if `filePath` is empty.
  - Throws a `PlatformException` with code `INSTALL_ERROR` when installation cannot be initiated (e.g., file missing, no handler, or unknown apps permission required). On Android 8.0+, the Settings screen to allow installs may be opened.

### Best practices

- Download the APK into your app’s cache or files directory to avoid file URI issues and to benefit from the bundled `FileProvider` configuration.
- Avoid using `file://` URIs. Always pass a regular filesystem path; the plugin converts it to a content URI internally.
- Guide users through enabling “Install unknown apps” on Android 8.0+ when needed.

### Limitations

- Android only. iOS does not allow arbitrary app installation outside the App Store/TestFlight.
- The plugin starts the installer UI; it does not perform silent/background installs.

### Troubleshooting

- "APK file does not exist": Make sure the path you pass actually exists and is readable.
- "Permission required: allow installs from unknown sources": Have the user enable “Install unknown apps” for your app and retry.
- "No activity found to handle APK install": The device lacks a handler for `ACTION_INSTALL_PACKAGE` (rare, usually on heavily customized builds).

### Contributing

Issues and pull requests are welcome.

### License

This project is licensed under the terms of the MIT license. See `LICENSE`.
