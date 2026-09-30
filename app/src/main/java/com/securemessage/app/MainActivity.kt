package com.securemessage.app

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import com.securemessage.app.ui.nav.AppNavHost
import com.securemessage.app.ui.theme.SecureMessageTheme

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        val container = (application as SecureMessageApp).container
        setContent {
            SecureMessageTheme {
                AppNavHost(container)
            }
        }
    }
}
