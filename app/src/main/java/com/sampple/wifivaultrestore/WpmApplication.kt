package com.sampple.wifivaultrestore

import android.app.Application
import com.sampple.wifivaultrestore.diagnostics.CrashReporter

class WpmApplication : Application() {
    override fun onCreate() {
        super.onCreate()
        CrashReporter.install(this)
    }
}
