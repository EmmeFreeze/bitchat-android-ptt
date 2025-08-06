package com.bitchat.ptt

import android.app.Application
import android.util.Log

class PttApplication : Application() {
    
    companion object {
        private const val TAG = "PttApplication"
        lateinit var instance: PttApplication
            private set
    }
    
    override fun onCreate() {
        super.onCreate()
        instance = this
        Log.d(TAG, "PTT Application initialized")
    }
}
