package com.mahin.hytune

import android.app.Application
import com.mahin.hytune.core.privilege.PrivilegeManager
import dagger.hilt.android.HiltAndroidApp
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltAndroidApp
class HyTuneApp : Application() {

    @Inject
    lateinit var privilegeManager: PrivilegeManager

    override fun onCreate() {
        super.onCreate()
        CoroutineScope(Dispatchers.IO).launch {
            privilegeManager.requestRootAccess()
        }
    }
}
