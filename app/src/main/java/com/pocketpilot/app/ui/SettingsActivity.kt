package com.pocketpilot.app.ui

import android.os.Bundle
import androidx.appcompat.app.AppCompatActivity
import com.pocketpilot.app.R

/**
 * Settings: brain mode (on-device / cloud / hub), LLM endpoint + API key,
 * on-device model picker + download, voice engine, wake phrase enrolment,
 * memory management, and the refusal list. v0.1 ships the screen shell;
 * each section is wired as its storage lands (see docs/ARCHITECTURE.md).
 */
class SettingsActivity : AppCompatActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_settings)
        title = getString(R.string.settings_title)
    }
}
