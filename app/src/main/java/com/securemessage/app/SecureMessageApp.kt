package com.securemessage.app

import android.app.Application
import com.securemessage.app.di.AppContainer
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch

class SecureMessageApp : Application() {
    lateinit var container: AppContainer

    private val appScope = CoroutineScope(SupervisorJob() + Dispatchers.Default)

    override fun onCreate() {
        super.onCreate()
        container = AppContainer(applicationContext)

        // Whenever a user is signed in (at launch or right after sign-in/sign-up), make sure
        // this device's E2EE public key is published so the other party can decrypt our
        // messages. This is what turns the "Secure chat" indicator from a claim into reality.
        container.firebaseAuth.addAuthStateListener { auth ->
            if (auth.currentUser != null) {
                appScope.launch { container.publishMyPublicKey() }
            }
        }
    }
}
