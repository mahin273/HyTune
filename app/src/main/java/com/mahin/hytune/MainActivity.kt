package com.mahin.hytune

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import com.mahin.hytune.data.local.datastore.AppSettings
import com.mahin.hytune.data.local.datastore.AppSettingsRepository
import com.mahin.hytune.ui.navigation.HyTuneAppShell
import com.mahin.hytune.ui.theme.HyTuneTheme
import dagger.hilt.android.AndroidEntryPoint
import javax.inject.Inject

@AndroidEntryPoint
class MainActivity : ComponentActivity() {

    @Inject
    lateinit var appSettingsRepository: AppSettingsRepository

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            val settings by appSettingsRepository.settings.collectAsState(initial = AppSettings())
            val isAmoled = settings.themeStyle == "amoled"
            val isLight = settings.themeStyle == "light"

            HyTuneTheme(
                darkTheme = !isLight,
                amoledMode = isAmoled
            ) {
                HyTuneAppShell()
            }
        }
    }
}
