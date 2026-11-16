package com.pocketpilot.app.service

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Intent
import android.os.IBinder
import androidx.core.app.NotificationCompat
import com.pocketpilot.app.MainActivity
import com.pocketpilot.app.a11y.ScreenController
import com.pocketpilot.app.storage.NotebookStore
import com.pocketpilot.app.storage.VaultStore
import com.pocketpilot.core.brain.AgentLoop
import com.pocketpilot.core.brain.CloudModelProvider
import com.pocketpilot.core.brain.ModelRouter
import com.pocketpilot.core.brain.Prompt
import com.pocketpilot.core.config.BrainConfig
import com.pocketpilot.core.memory.Notebook
import com.pocketpilot.core.tools.ToolRegistry
import com.pocketpilot.core.tools.ToolSchemas

/**
 * Foreground service: hosts the agent loop, the tool handlers, memory and
 * voice. One service, one loop thread. The loop only talks to the
 * platform-neutral :core types; this service binds them to Android.
 */
class PilotCoreService : Service() {

    companion object {
        const val CHANNEL_ID = "pocketpilot"
        const val NOTIFICATION_ID = 1
        const val ACTION_RUN_TASK = "com.pocketpilot.app.RUN_TASK"
        const val EXTRA_TASK = "task"

        @Volatile
        var isRunning: Boolean = false
            private set
    }

    private lateinit var config: BrainConfig
    private lateinit var notebook: Notebook
    private lateinit var vault: VaultStore
    private lateinit var tools: ToolRegistry
    private var loop: AgentLoop? = null

    override fun onCreate() {
        super.onCreate()
        isRunning = true
        createChannel()
        startForeground(NOTIFICATION_ID, notification("Pilot ready."))

        config = BrainConfig() // TODO: load from Settings (mode, endpoint, keys).
        notebook = NotebookStore(this).notebook()
        vault = VaultStore(this)
        tools = buildTools()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if (intent?.action == ACTION_RUN_TASK) {
            val task = intent.getStringExtra(EXTRA_TASK) ?: return START_STICKY
            runTask(task)
        }
        return START_STICKY
    }

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onDestroy() {
        loop?.shutdown()
        isRunning = false
        super.onDestroy()
    }

    private fun runTask(task: String) {
        Thread {
            val router = ModelRouter(config, onDevice = null, cloud = CloudModelProvider(config.cloud))
            val model = router.providerFor()
            loop = AgentLoop(
                config = config,
                model = model,
                tools = tools,
                onStep = { line -> updateNotification(line) },
            )
            val outcome = loop?.run(
                task,
                Prompt.systemPrompt(deviceInfo = "Android", notebookSummary = notebook.summaryForPrompt()),
            )
            updateNotification(outcome?.summary ?: "Done.")
        }.start()
    }

    /** Binds every built-in tool schema to a real Android implementation. */
    private fun buildTools(): ToolRegistry {
        val registry = ToolRegistry()
        for (def in ToolSchemas.definitions()) {
            registry.register(def) { args ->
                when (def.name) {
                    "get_screen" -> com.pocketpilot.core.tools.ToolResult(
                        ScreenController.dumpScreen().toTextDump(),
                    )
                    "tap" -> {
                        val state = ScreenController.dumpScreen()
                        val node = args.optString("element_id", "").takeIf { it.isNotBlank() }
                            ?.let { id -> state.nodes.firstOrNull { n -> n.id == id } }
                        val (x, y) = when {
                            node != null -> node.centerX to node.centerY
                            args.has("x") -> args.optInt("x") to args.optInt("y")
                            args.has("text") -> {
                                val target = state.findByText(args.optString("text"))
                                    ?: return@register com.pocketpilot.core.tools.ToolResult(
                                        "No element with that text.", isError = true,
                                    )
                                target.centerX to target.centerY
                            }
                            else -> return@register com.pocketpilot.core.tools.ToolResult(
                                "tap needs element_id, x/y, or text.", isError = true,
                            )
                        }
                        val ok = ScreenController.tap(x, y)
                        com.pocketpilot.core.tools.ToolResult(if (ok) "Tapped ($x,$y)." else "Tap failed — is the accessibility service on?")
                    }
                    "type_text" -> {
                        val ok = ScreenController.typeText(args.optString("text"))
                        com.pocketpilot.core.tools.ToolResult(if (ok) "Typed." else "No focused field to type into.")
                    }
                    "swipe" -> {
                        val ok = ScreenController.swipe(args.optString("direction"), args.optInt("distance", 600))
                        com.pocketpilot.core.tools.ToolResult(if (ok) "Swiped ${args.optString("direction")}." else "Swipe failed.")
                    }
                    "open_app" -> {
                        val name = args.optString("app")
                        val pm = packageManager
                        val launch = pm.getLaunchIntentForPackage(name)
                            ?: pm.queryIntentActivities(
                                Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_LAUNCHER),
                                0,
                            ).firstOrNull { it.loadLabel(pm).toString().equals(name, ignoreCase = true) }
                                ?.activityInfo?.packageName?.let { pm.getLaunchIntentForPackage(it) }
                        if (launch == null) {
                            com.pocketpilot.core.tools.ToolResult("App not found: $name", isError = true)
                        } else {
                            launch.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                            startActivity(launch)
                            com.pocketpilot.core.tools.ToolResult("Opened $name.")
                        }
                    }
                    "press_key" -> {
                        val action = when (args.optString("key")) {
                            "back" -> android.accessibilityservice.AccessibilityService.GLOBAL_ACTION_BACK
                            "home" -> android.accessibilityservice.AccessibilityService.GLOBAL_ACTION_HOME
                            "recents" -> android.accessibilityservice.AccessibilityService.GLOBAL_ACTION_RECENTS
                            else -> -1
                        }
                        if (action == -1) {
                            com.pocketpilot.core.tools.ToolResult(
                                "Key '${args.optString("key")}' is not supported (back/home/recents only).",
                                isError = true,
                            )
                        } else {
                            val ok = ScreenController.globalAction(action)
                            com.pocketpilot.core.tools.ToolResult(if (ok) "Pressed ${args.optString("key")}." else "Key press failed.")
                        }
                    }
                    "wait" -> {
                        val seconds = args.optInt("seconds", 2).coerceIn(1, 10)
                        Thread.sleep(seconds * 1000L)
                        com.pocketpilot.core.tools.ToolResult("Waited $seconds s.")
                    }
                    "take_screenshot" -> {
                        val latch = java.util.concurrent.CountDownLatch(1)
                        var bytes: ByteArray? = null
                        ScreenController.takeScreenshot { data ->
                            bytes = data
                            latch.countDown()
                        }
                        latch.await(5, java.util.concurrent.TimeUnit.SECONDS)
                        if (bytes == null) {
                            com.pocketpilot.core.tools.ToolResult("Screenshot unavailable (needs API 30+ and the accessibility service).", isError = true)
                        } else {
                            com.pocketpilot.core.tools.ToolResult(
                                "[screenshot attached] Screenshot taken.",
                                attachment = bytes,
                                attachmentMime = "image/jpeg",
                            )
                        }
                    }
                    "read_notifications" -> {
                        val notes = com.pocketpilot.app.service.NotificationRelayService.latest
                        com.pocketpilot.core.tools.ToolResult(
                            if (notes.isEmpty()) "No recent notifications."
                            else notes.joinToString("\n"),
                        )
                    }
                    "get_device_info" -> {
                        val battery = (getSystemService(BATTERY_SERVICE) as android.os.BatteryManager)
                            .getIntProperty(android.os.BatteryManager.BATTERY_PROPERTY_CAPACITY)
                        val cm = getSystemService(CONNECTIVITY_SERVICE) as android.net.ConnectivityManager
                        val network = cm.activeNetworkInfo?.typeName ?: "unknown"
                        com.pocketpilot.core.tools.ToolResult(
                            "Battery: $battery%. Network: $network. Time: ${java.text.SimpleDateFormat("HH:mm", java.util.Locale.getDefault()).format(java.util.Date())}. Foreground app: ${ScreenController.dumpScreen().packageName}",
                        )
                    }
                    "remember" -> {
                        notebook.remember(args.optString("section"), args.optString("text"))
                        com.pocketpilot.core.tools.ToolResult("Remembered.")
                    }
                    "recall" -> {
                        val hits = notebook.search(args.optString("query"))
                        com.pocketpilot.core.tools.ToolResult(
                            if (hits.isEmpty()) "Nothing in memory about that."
                            else hits.joinToString("; ") { "[${it.first}] ${it.second}" },
                        )
                    }
                    "fill_secret" -> {
                        val ok = vault.typeIntoFocusedField(args.optString("label"))
                        com.pocketpilot.core.tools.ToolResult(
                            if (ok) "Secret typed locally." else "No secret saved under that label.",
                        )
                    }
                    "finish" -> com.pocketpilot.core.tools.ToolResult("Finished.")
                    else -> com.pocketpilot.core.tools.ToolResult("Tool ${def.name} has no Android binding yet.", isError = true)
                }
            }
        }
        return registry
    }

    private fun createChannel() {
        val manager = getSystemService(NOTIFICATION_SERVICE) as NotificationManager
        val channel = NotificationChannel(CHANNEL_ID, "PocketPilot", NotificationManager.IMPORTANCE_LOW)
        manager.createNotificationChannel(channel)
    }

    private fun notification(text: String): Notification {
        val intent = PendingIntent.getActivity(
            this, 0, Intent(this, MainActivity::class.java),
            PendingIntent.FLAG_IMMUTABLE,
        )
        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setSmallIcon(android.R.drawable.ic_dialog_info)
            .setContentTitle("PocketPilot")
            .setContentText(text.take(120))
            .setContentIntent(intent)
            .setOngoing(true)
            .build()
    }

    private fun updateNotification(text: String) {
        val manager = getSystemService(NOTIFICATION_SERVICE) as NotificationManager
        manager.notify(NOTIFICATION_ID, notification(text))
    }
}
