package com.mahin.hytune.receiver

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import com.mahin.hytune.domain.boot.BootApplyEngine
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import javax.inject.Inject

@AndroidEntryPoint
class BootCompletedReceiver : BroadcastReceiver() {

    @Inject
    lateinit var bootApplyEngine: BootApplyEngine

    var coroutineDispatcher: CoroutineDispatcher = Dispatchers.IO

    override fun onReceive(context: Context, intent: Intent?) {
        handleBootIntent(intent)
    }

    fun handleBootIntent(intent: Intent?): kotlinx.coroutines.Job? {
        val action = intent?.action ?: return null
        if (action != Intent.ACTION_BOOT_COMPLETED &&
            action != Intent.ACTION_LOCKED_BOOT_COMPLETED &&
            action != "android.intent.action.QUICKBOOT_POWERON" &&
            action != "com.htc.intent.action.QUICKBOOT_POWERON"
        ) {
            return null
        }

        val pendingResult = runCatching { goAsync() }.getOrNull()
        return CoroutineScope(coroutineDispatcher).launch {
            try {
                bootApplyEngine.startBootApply()
            } finally {
                pendingResult?.finish()
            }
        }
    }
}
