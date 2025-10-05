import 'package:flutter/foundation.dart';
import 'package:flutter/services.dart';

import 'install_apk_platform_interface.dart';

/// An implementation of [InstallApkPlatform] that uses method channels.
class MethodChannelInstallApk extends InstallApkPlatform {
  /// The method channel used to interact with the native platform.
  @visibleForTesting
  final methodChannel = const MethodChannel('install_apk');

  @override
  Future<String?> getPlatformVersion() async {
    final version = await methodChannel.invokeMethod<String>(
      'getPlatformVersion',
    );
    return version;
  }

  @override
  Future<void> installApk(String filePath) async {
    await methodChannel.invokeMethod<void>('installApk', {
      'filePath': filePath,
    });
  }
}
