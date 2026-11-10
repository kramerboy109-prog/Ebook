package com.lesehaus.app

import android.os.Bundle
import android.view.KeyEvent
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import com.lesehaus.app.ui.AppNavigation
import com.lesehaus.app.ui.theme.LesehausTheme

class MainActivity : ComponentActivity() {
    /** Wird vom Reader gesetzt, um z. B. mit den Lautstärketasten zu blättern. */
    var keyHandler: ((KeyEvent) -> Boolean)? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        enableEdgeToEdge()
        super.onCreate(savedInstanceState)
        val app = (application as LesehausApp).container
        setContent {
            val settings by app.settings.app.collectAsState()
            LesehausTheme(settings.themeMode) {
                Box(Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background)) {
                    AppNavigation()
                }
            }
        }
    }

    override fun dispatchKeyEvent(event: KeyEvent): Boolean {
        if (keyHandler?.invoke(event) == true) return true
        return super.dispatchKeyEvent(event)
    }
}
