package com.amrg.connectly

import android.app.Application
import org.webrtc.BuildConfig
import timber.log.Timber

class ConnectlyApp : Application() {

    override fun onCreate() {
        super.onCreate()
        if (BuildConfig.DEBUG) Timber.plant()
    }
}