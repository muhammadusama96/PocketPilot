package com.pocketpilot.app

import android.app.Application

class PocketPilotApp : Application() {
    override fun onCreate() {
        super.onCreate()
        // No analytics, no crash reporting, no advertising ID — by design.
        // All memory stays on-device; see docs/PRIVACY.md.
    }
}
