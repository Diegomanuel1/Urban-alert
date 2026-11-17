package com.example

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import com.example.core.firebase.configureAppCheck
import com.example.ui.navigation.UrbanAlertApp
import com.example.ui.theme.UrbanAlertTheme

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // Configura Firebase App Check antes de qualquer UI, ViewModel ou operação Firebase
        configureAppCheck(this, intent)

        enableEdgeToEdge()
        setContent {
            UrbanAlertTheme {
                UrbanAlertApp()
            }
        }
    }
}
