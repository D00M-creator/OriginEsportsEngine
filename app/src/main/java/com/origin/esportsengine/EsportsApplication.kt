package com.origin.esportsengine

import android.app.Application
import org.lsposed.hiddenapibypass.HiddenApiBypass

class EsportsApplication : Application() {
    override fun onCreate() {
        super.onCreate()
        HiddenApiBypass.addHiddenApiExemptions("L")
    }
}