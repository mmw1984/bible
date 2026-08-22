package com.marcow.bible

import android.content.ComponentName
import android.content.Intent
import android.content.pm.ShortcutInfo
import android.content.pm.ShortcutManager
import android.graphics.drawable.Icon
import android.os.Bundle
import android.os.Build
import android.view.RoundedCorner
import android.util.Base64
import androidx.core.view.WindowCompat
import io.flutter.embedding.android.FlutterActivity
import io.flutter.embedding.engine.FlutterEngine
import io.flutter.plugin.common.MethodChannel
import org.json.JSONObject

open class MainActivity : FlutterActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        if (javaClass == MainActivity::class.java && forwardOAuthToChat(intent)) {
            super.onCreate(savedInstanceState)
            finish()
            return
        }
        super.onCreate(savedInstanceState)
        // Native opt-in for Android 9+ and Android 15+ enforcement. Flutter
        // then receives the insets, which the UI uses for its tool controls
        // and the reading list's final padding.
        WindowCompat.setDecorFitsSystemWindows(window, false)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            window.isStatusBarContrastEnforced = false
            window.isNavigationBarContrastEnforced = false
        }
    }

    override fun onNewIntent(intent: Intent) {
        if (javaClass == MainActivity::class.java && forwardOAuthToChat(intent)) return
        super.onNewIntent(intent)
        setIntent(intent)
    }

    private fun forwardOAuthToChat(intent: Intent?): Boolean {
        val callback = intent?.data ?: return false
        if (!AiChatActivity.isAlive || callback.scheme != "bible" || callback.host != "openrouter") {
            return false
        }
        startActivity(Intent(this, AiChatActivity::class.java).apply {
            action = Intent.ACTION_VIEW
            data = callback
            addFlags(Intent.FLAG_ACTIVITY_REORDER_TO_FRONT or Intent.FLAG_ACTIVITY_SINGLE_TOP)
        })
        return true
    }

    override fun configureFlutterEngine(flutterEngine: FlutterEngine) {
        super.configureFlutterEngine(flutterEngine)
        MethodChannel(flutterEngine.dartExecutor.binaryMessenger, "bible/android")
            .setMethodCallHandler { call, result ->
                when (call.method) {
                    "openAiChatActivity" -> {
                        val payload = (call.arguments as? Map<*, *>) ?: emptyMap<String, Any?>()
                        val payloadJson = JSONObject(payload).toString()
                        val route = aiChatRoute(payloadJson)
                        startActivity(Intent(this, AiChatActivity::class.java).apply {
                            action = Intent.ACTION_VIEW
                            putExtra(AiChatActivity.EXTRA_INITIAL_ROUTE, route)
                            putExtra(AiChatActivity.EXTRA_LAUNCH_PAYLOAD, payloadJson)
                        })
                        result.success(null)
                    }
                    "setAiShortcutEnabled" -> {
                        setAiShortcut(call.arguments == true)
                        result.success(null)
                    }
                    "getDeviceRoundedCorners" -> result.success(deviceRoundedCorners())
                    else -> result.notImplemented()
                }
            }
    }

    private fun deviceRoundedCorners(): Map<String, Any> {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.S) {
            return unavailableRoundedCorners()
        }
        val insets = windowManager.currentWindowMetrics.windowInsets
        val density = resources.displayMetrics.density.toDouble()
        fun radiusDp(position: Int): Double =
            (insets.getRoundedCorner(position)?.radius ?: 0) / density

        val topLeftDp = radiusDp(RoundedCorner.POSITION_TOP_LEFT)
        val topRightDp = radiusDp(RoundedCorner.POSITION_TOP_RIGHT)
        val bottomLeftDp = radiusDp(RoundedCorner.POSITION_BOTTOM_LEFT)
        val bottomRightDp = radiusDp(RoundedCorner.POSITION_BOTTOM_RIGHT)
        val available = listOf(topLeftDp, topRightDp, bottomLeftDp, bottomRightDp)
            .any { it > 0.0 }
        return mapOf(
            "available" to available,
            "topLeftDp" to topLeftDp,
            "topRightDp" to topRightDp,
            "bottomLeftDp" to bottomLeftDp,
            "bottomRightDp" to bottomRightDp,
        )
    }

    private fun unavailableRoundedCorners(): Map<String, Any> = mapOf(
        "available" to false,
        "topLeftDp" to 0.0,
        "topRightDp" to 0.0,
        "bottomLeftDp" to 0.0,
        "bottomRightDp" to 0.0,
    )

    private fun setAiShortcut(enabled: Boolean) {
        val manager = getSystemService(ShortcutManager::class.java)
        if (!enabled) {
            manager.removeDynamicShortcuts(listOf(AI_SHORTCUT_ID))
            return
        }
        val intent = Intent(this, AiChatActivity::class.java).apply {
            action = Intent.ACTION_VIEW
            putExtra(AiChatActivity.EXTRA_INITIAL_ROUTE, "/ai-chat")
        }
        val shortcut = ShortcutInfo.Builder(this, AI_SHORTCUT_ID)
            .setShortLabel(getString(R.string.ai_shortcut_short_label))
            .setLongLabel(getString(R.string.ai_shortcut_long_label))
            .setIcon(Icon.createWithResource(this, R.mipmap.ic_launcher))
            .setIntent(intent)
            .setActivity(ComponentName(this, MainActivity::class.java))
            .build()
        manager.addDynamicShortcuts(listOf(shortcut))
    }

    private fun aiChatRoute(payloadJson: String): String {
        if (payloadJson == "{}") return "/ai-chat"
        val payload = payloadJson.toByteArray(Charsets.UTF_8)
        val encoded = Base64.encodeToString(
            payload,
            Base64.URL_SAFE or Base64.NO_WRAP or Base64.NO_PADDING,
        )
        return "/ai-chat?payload=$encoded"
    }

    companion object {
        private const val AI_SHORTCUT_ID = "bible_ai_chat"
    }
}
