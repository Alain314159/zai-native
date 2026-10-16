package com.zai.native_app

import android.app.Application
import com.zai.native_app.data.db.AppDatabase

class ZaiApp : Application() {
    override fun onCreate() {
        super.onCreate()
        // Initialize database in background
        Thread { AppDatabase.getInstance(this) }.start()
    }
}
