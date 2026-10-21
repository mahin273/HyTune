package com.mahin.hytune

import android.content.Intent
import android.os.Bundle
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.lifecycle.lifecycleScope
import com.mahin.hytune.data.local.datastore.AppSettings
import com.mahin.hytune.data.local.datastore.AppSettingsRepository
import com.mahin.hytune.domain.appmanager.FreezeGroupRepository
import com.mahin.hytune.ui.navigation.HyTuneAppShell
import com.mahin.hytune.ui.theme.HyTuneTheme
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.launch
import javax.inject.Inject

@AndroidEntryPoint
class MainActivity : ComponentActivity() {

    @Inject
    lateinit var appSettingsRepository: AppSettingsRepository

    @Inject
    lateinit var freezeGroupRepository: FreezeGroupRepository

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        handleIntent(intent)
        enableEdgeToEdge()
        setContent {
            val settings by appSettingsRepository.settings.collectAsState(initial = AppSettings())
            val isAmoled = settings.themeStyle == "amoled"

            HyTuneTheme(
                darkTheme = true,
                amoledMode = isAmoled
            ) {
                HyTuneAppShell()
            }
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        handleIntent(intent)
    }

    private fun handleIntent(intent: Intent?) {
        if (intent?.getStringExtra("action") == "unfreeze_group") {
            val groupId = intent.getLongExtra("group_id", -1L)
            if (groupId != -1L) {
                lifecycleScope.launch {
                    freezeGroupRepository.unfreezeGroup(groupId).onSuccess { res ->
                        Toast.makeText(
                            this@MainActivity,
                            "Unfrozen ${res.successCount} apps in ${res.groupName}",
                            Toast.LENGTH_SHORT
                        ).show()
                    }.onFailure { err ->
                        Toast.makeText(
                            this@MainActivity,
                            "Failed to unfreeze: ${err.message}",
                            Toast.LENGTH_SHORT
                        ).show()
                    }
                }
            }
        }
    }
}
