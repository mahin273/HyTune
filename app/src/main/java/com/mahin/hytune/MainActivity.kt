package com.mahin.hytune

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import com.mahin.hytune.ui.navigation.HyTuneAppShell
import com.mahin.hytune.ui.theme.HyTuneTheme
import dagger.hilt.android.AndroidEntryPoint

@AndroidEntryPoint
class MainActivity : ComponentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            HyTuneTheme {
                HyTuneAppShell()
            }
        }
    }
}
