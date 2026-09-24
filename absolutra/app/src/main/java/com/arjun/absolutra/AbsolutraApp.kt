package com.arjun.absolutra

import android.app.Application
import android.content.Context

class AbsolutraApp : Application() {
    companion object {
        lateinit var appContext: Context
        var isInitialized = false
    }

    override fun onCreate() {
        super.onCreate()
        appContext = applicationContext
        isInitialized = true
    }
}
