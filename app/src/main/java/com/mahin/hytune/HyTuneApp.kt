package com.mahin.hytune

import android.app.Application
import dagger.hilt.android.HiltAndroidApp

@HiltAndroidApp
class HyTuneApp : Application() {

    override fun onCreate() {
        super.onCreate()
    }
}
