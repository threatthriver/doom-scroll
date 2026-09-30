package com.securemessage.app

import android.app.Application
import com.securemessage.app.di.AppContainer

class SecureMessageApp : Application() {
    lateinit var container: AppContainer

    override fun onCreate() {
        super.onCreate()
        container = AppContainer()
    }
}
