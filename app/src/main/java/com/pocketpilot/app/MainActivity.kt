package com.pocketpilot.app

import android.content.Intent
import android.os.Bundle
import android.provider.Settings
import android.widget.Button
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import com.pocketpilot.app.a11y.PilotAccessibilityService
import com.pocketpilot.app.service.PilotCoreService

/**
 * Launcher + self-check. The first screen explains what the app needs and
 * why, one permission at a time — an agent that reads your screen must earn
 * each access explicitly.
 */
class MainActivity : AppCompatActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        val status = findViewById<TextView>(R.id.status_text)
        val enableA11y = findViewById<Button>(R.id.enable_a11y_button)
        val startService = findViewById<Button>(R.id.start_service_button)

        refreshStatus(status)

        enableA11y.setOnClickListener {
            startActivity(Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS))
        }
        startService.setOnClickListener {
            startForegroundService(Intent(this, PilotCoreService::class.java))
            refreshStatus(status)
        }
    }

    override fun onResume() {
        super.onResume()
        findViewById<TextView>(R.id.status_text)?.let { refreshStatus(it) }
    }

    private fun refreshStatus(status: TextView) {
        val a11yOn = PilotAccessibilityService.isRunning
        val serviceOn = PilotCoreService.isRunning
        status.text = buildString {
            append("Accessibility service: ${if (a11yOn) "ON" else "OFF — needed to operate apps"}\n")
            append("Pilot service: ${if (serviceOn) "ON" else "OFF"}\n")
            append("Memory: on-device only, encrypted\n")
            append("No account required. Bring your own model or API key.")
        }
    }
}
