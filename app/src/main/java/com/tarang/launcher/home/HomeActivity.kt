package com.tarang.launcher.home

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import com.tarang.launcher.TarangApp
import com.tarang.launcher.ui.LauncherScreen
import com.tarang.launcher.ui.theme.TarangTheme

/**
 * The launcher home screen. Hosts the launcher UI (see [LauncherScreen]) under [TarangTheme].
 */
class HomeActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val container = (application as TarangApp).container
        setContent {
            TarangTheme {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .background(Color.Black),
                ) {
                    LauncherScreen(container = container)
                }
            }
        }
    }
}
