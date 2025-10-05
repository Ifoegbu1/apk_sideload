package com.ifoegbu1.install_apk

import android.content.ActivityNotFoundException
import android.content.ClipData
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.provider.Settings
import androidx.core.content.FileProvider
import io.flutter.embedding.engine.plugins.FlutterPlugin
import io.flutter.embedding.engine.plugins.activity.ActivityAware
import io.flutter.embedding.engine.plugins.activity.ActivityPluginBinding
import io.flutter.plugin.common.MethodCall
import io.flutter.plugin.common.MethodChannel
import io.flutter.plugin.common.PluginRegistry
import java.io.File

class InstallApkPlugin : FlutterPlugin, MethodChannel.MethodCallHandler, ActivityAware, PluginRegistry.ActivityResultListener {
  private lateinit var channel: MethodChannel
  private var applicationContext: Context? = null
  private var activityBinding: ActivityPluginBinding? = null
  private var pendingResult: MethodChannel.Result? = null
  private var pendingFilePath: String? = null
  private val requestManageUnknownSources = 10001

  override fun onAttachedToEngine(binding: FlutterPlugin.FlutterPluginBinding) {
    applicationContext = binding.applicationContext
    channel = MethodChannel(binding.binaryMessenger, "install_apk")
    channel.setMethodCallHandler(this)
  }

  override fun onMethodCall(call: MethodCall, result: MethodChannel.Result) {
    when (call.method) {
      "getPlatformVersion" -> result.success("Android ${Build.VERSION.RELEASE}")
      "installApk" -> {
        val path = call.argument<String>("filePath")
        if (path.isNullOrBlank()) {
          result.error("ARG_ERROR", "filePath is required", null)
          return
        }
        if (pendingResult != null) {
          result.error("INSTALL_IN_PROGRESS", "Another install flow is in progress", null)
          return
        }
        try {
          val started = installApk(path, result)
          if (started) {
            // If we launched Settings to request permission, we will finish the result later.
            return
          }
          // Otherwise, install intent started successfully; complete now.
          result.success(null)
        } catch (e: Exception) {
          result.error("INSTALL_ERROR", e.message, null)
        }
      }
      else -> result.notImplemented()
    }
  }

  private fun installApk(filePath: String, methodResult: MethodChannel.Result? = null): Boolean {
    val ctx = activityBinding?.activity ?: applicationContext
      ?: throw IllegalStateException("No context available")

    val apkFile = File(filePath)
    if (!apkFile.exists()) throw IllegalArgumentException("APK file does not exist: $filePath")

    val uri: Uri = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
      val authority = ctx.packageName + ".install_apk.fileprovider"
      FileProvider.getUriForFile(ctx, authority, apkFile)
    } else {
      Uri.fromFile(apkFile)
    }

    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
      val canInstall = ctx.packageManager.canRequestPackageInstalls()
      if (!canInstall) {
        val activity = activityBinding?.activity
        if (activity != null) {
          val settingsIntent = Intent(Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES).apply {
            data = Uri.parse("package:" + activity.packageName)
          }
          // Store pending state and launch Settings to allow user to grant permission
          pendingResult = methodResult
          pendingFilePath = filePath
          activity.startActivityForResult(settingsIntent, requestManageUnknownSources)
          return true // indicate we launched settings and will resume later
        } else {
          val settingsIntent = Intent(Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES).apply {
            data = Uri.parse("package:" + ctx.packageName)
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
          }
          ctx.startActivity(settingsIntent)
          throw IllegalStateException("Permission required: allow installs from unknown sources for ${ctx.packageName}")
        }
      }
    }

    val intent = Intent(Intent.ACTION_INSTALL_PACKAGE).apply {
      setDataAndType(uri, "application/vnd.android.package-archive")
      addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
      addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
      putExtra(Intent.EXTRA_NOT_UNKNOWN_SOURCE, true)
      putExtra(Intent.EXTRA_RETURN_RESULT, false)
      clipData = ClipData.newRawUri("APK", uri)
    }

    val resolveInfo = ctx.packageManager.queryIntentActivities(intent, 0)
    if (resolveInfo.isNullOrEmpty()) {
      throw IllegalStateException("No activity found to handle APK install")
    }

    // Proactively grant read permission to all potential installer activities
    for (ri in resolveInfo) {
      val pkg = ri.activityInfo?.packageName ?: continue
      try {
        ctx.grantUriPermission(pkg, uri, Intent.FLAG_GRANT_READ_URI_PERMISSION)
      } catch (_: Exception) { /* ignore */ }
    }

    try {
      ctx.startActivity(intent)
    } catch (e: ActivityNotFoundException) {
      throw IllegalStateException("No activity found to handle APK install")
    }

    return false
  }

  override fun onDetachedFromEngine(binding: FlutterPlugin.FlutterPluginBinding) {
    channel.setMethodCallHandler(null)
  }

  override fun onAttachedToActivity(binding: ActivityPluginBinding) {
    activityBinding = binding
    binding.addActivityResultListener(this)
  }

  override fun onDetachedFromActivityForConfigChanges() {
    activityBinding?.removeActivityResultListener(this)
    activityBinding = null
  }

  override fun onReattachedToActivityForConfigChanges(binding: ActivityPluginBinding) {
    activityBinding = binding
    binding.addActivityResultListener(this)
  }

  override fun onDetachedFromActivity() {
    activityBinding?.removeActivityResultListener(this)
    activityBinding = null
  }

  override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?): Boolean {
    if (requestCode != requestManageUnknownSources) return false

    val activity = activityBinding?.activity ?: return false
    val granted = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
      activity.packageManager.canRequestPackageInstalls()
    } else true

    val result = pendingResult
    val filePath = pendingFilePath
    pendingResult = null
    pendingFilePath = null

    if (result == null || filePath.isNullOrEmpty()) return true

    if (!granted) {
      result.error(
        "PERMISSION_DENIED",
        "Install unknown apps permission not granted",
        null
      )
      return true
    }

    try {
      // Now permission is granted; start install intent
      installApk(filePath, null)
      result.success(null)
    } catch (e: Exception) {
      result.error("INSTALL_ERROR", e.message, null)
    }
    return true
  }
}
