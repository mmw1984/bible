package com.marcow.bible

import android.content.Intent
import android.os.Bundle
import io.flutter.embedding.engine.FlutterEngine
import io.flutter.plugin.common.MethodChannel

class AiChatActivity : MainActivity() {
    private var launchChannel: MethodChannel? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        isAlive = true
        super.onCreate(savedInstanceState)
    }

    override fun onDestroy() {
        isAlive = false
        super.onDestroy()
    }

    override fun getInitialRoute(): String =
        intent?.getStringExtra(EXTRA_INITIAL_ROUTE)?.takeIf { it.startsWith("/ai-chat") }
            ?: "/ai-chat"

    override fun configureFlutterEngine(flutterEngine: FlutterEngine) {
        super.configureFlutterEngine(flutterEngine)
        launchChannel = MethodChannel(
            flutterEngine.dartExecutor.binaryMessenger,
            LAUNCH_CHANNEL,
        ).apply {
            setMethodCallHandler { call, result ->
                if (call.method == "getLaunchPayload") {
                    result.success(intent?.getStringExtra(EXTRA_LAUNCH_PAYLOAD))
                } else {
                    result.notImplemented()
                }
            }
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        intent.getStringExtra(EXTRA_LAUNCH_PAYLOAD)?.let { payload ->
            launchChannel?.invokeMethod("deliverLaunchPayload", payload)
        }
        val route = intent.getStringExtra(EXTRA_INITIAL_ROUTE)
            ?.takeIf { it.startsWith("/ai-chat") }
            ?: return
        flutterEngine?.navigationChannel?.pushRoute(route)
    }

    companion object {
        const val EXTRA_INITIAL_ROUTE = "com.marcow.bible.extra.INITIAL_ROUTE"
        const val EXTRA_LAUNCH_PAYLOAD = "com.marcow.bible.extra.LAUNCH_PAYLOAD"
        private const val LAUNCH_CHANNEL = "bible/ai_chat_launch"
        @Volatile var isAlive: Boolean = false
    }
}
