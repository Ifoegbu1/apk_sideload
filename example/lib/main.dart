import 'dart:async';
import 'dart:developer';
import 'dart:io';

import 'package:flutter/material.dart';
import 'package:flutter/services.dart';
import 'package:apk_sideload/install_apk.dart';
import 'package:path/path.dart' as p;
import 'package:path_provider/path_provider.dart';
import 'package:permission_handler/permission_handler.dart';

void main() {
  runApp(const MyApp());
}

class MyApp extends StatefulWidget {
  const MyApp({super.key});

  @override
  State<MyApp> createState() => _MyAppState();
}

class _MyAppState extends State<MyApp> {
  String _platformVersion = 'Unknown';
  final _installApkPlugin = InstallApk();
  final TextEditingController _pathController = TextEditingController(
    text: '/sdcard/Download/app.apk',
  );

  @override
  void initState() {
    super.initState();
    initPlatformState();
  }

  // Platform messages are asynchronous, so we initialize in an async method.
  Future<void> initPlatformState() async {
    String platformVersion;
    // Platform messages may fail, so we use a try/catch PlatformException.
    // We also handle the message potentially returning null.
    try {
      platformVersion =
          await _installApkPlugin.getPlatformVersion() ??
          'Unknown platform version';
    } on PlatformException {
      platformVersion = 'Failed to get platform version.';
    }

    // If the widget was removed from the tree while the asynchronous platform
    // message was in flight, we want to discard the reply rather than calling
    // setState to update our non-existent appearance.
    if (!mounted) return;

    setState(() {
      _platformVersion = platformVersion;
    });
  }

  Future<File> startDownloadAndInstall({required String url}) async {
    final Directory tempDir = await getTemporaryDirectory();
    final filePath = '${tempDir.path}/app_update.apk';

    if (File(filePath).existsSync()) {
      InstallApk().installApk(filePath);
      return File(filePath);
    }
    double progress = 0.0;

    final HttpClient httpClient = HttpClient();
    File? outFile;
    try {
      final Uri uri = Uri.parse(url);
      final HttpClientRequest request = await httpClient.getUrl(uri);
      final HttpClientResponse response = await request.close();

      if (response.statusCode != HttpStatus.ok) {
        throw HttpException(
          'Failed to download. Status: ${response.statusCode}',
          uri: uri,
        );
      }

      final int? contentLength =
          response.contentLength > 0 ? response.contentLength : null;
      int bytesReceived = 0;

      outFile = File(filePath);
      if (!outFile.parent.existsSync()) {
        outFile.parent.createSync(recursive: true);
      }
      final IOSink sink = outFile.openWrite();
      await for (final List<int> chunk in response) {
        log('progress: $progress');
        bytesReceived += chunk.length;
        sink.add(chunk);
        if (contentLength != null && contentLength > 0) {
          progress = (bytesReceived / contentLength).clamp(0.0, 1.0);
        }
      }
      await sink.flush();
      await sink.close();

      // Ensure 100% at the end
      progress = 1.0;
      return outFile;
    } catch (e) {
      rethrow;
    } finally {
      httpClient.close(force: true);

      await InstallApk().installApk(filePath);
      // await AndroidPackageInstaller.installApk(apkFilePath: filePath);
    }
  }

  Future<String> moveApkToCache(String sourcePath) async {
    final cacheDir = await getTemporaryDirectory();
    final target = File(p.join(cacheDir.path, p.basename(sourcePath)));
    await File(sourcePath).copy(target.path);
    return target.path;
  }

  @override
  Widget build(BuildContext context) {
    return MaterialApp(
      home: Scaffold(
        appBar: AppBar(title: const Text('Plugin example app')),
        body: Center(
          child: Column(
            mainAxisSize: MainAxisSize.min,
            children: [
              Text('Running on: $_platformVersion\n'),
              const SizedBox(height: 8),
              Padding(
                padding: const EdgeInsets.symmetric(horizontal: 16),
                child: TextField(
                  controller: _pathController,
                  decoration: const InputDecoration(
                    labelText: 'APK file path',
                    hintText: '/sdcard/Download/app.apk',
                  ),
                ),
              ),
              const SizedBox(height: 16),
              ElevatedButton(
                onPressed: () async {
                  final apkPath = _pathController.text.trim();
                  try {
                    const storage = Permission.manageExternalStorage;
                    await storage.request();
                    final cachedPath = await moveApkToCache(apkPath);
                    await InstallApk().installApk(cachedPath);
                    // await _installApkPlugin.installApk(apkPath);
                  } on PlatformException catch (e, s) {
                    log('installError', error: e, stackTrace: s);
                    if (!mounted) return;
                  }
                },
                child: const Text('Install APK from path'),
              ),
            ],
          ),
        ),
      ),
    );
  }
}
