package com.reparafotos.ai

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import com.reparafotos.ai.ui.ReparaFotosApp
import com.reparafotos.ai.ui.theme.ReparaFotosTheme

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            ReparaFotosTheme {
                ReparaFotosApp()
            }
        }
    }
}
