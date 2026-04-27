package com.forenserecovery.android

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import com.forenserecovery.android.ui.ForenseRecoveryApp
import com.forenserecovery.android.ui.theme.ForenseRecoveryTheme

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            ForenseRecoveryTheme {
                ForenseRecoveryApp()
            }
        }
    }
}
