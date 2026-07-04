package com.alyaqdhan.riyal

import android.app.Application
import com.alyaqdhan.riyal.core.Prefs
import com.alyaqdhan.riyal.core.Verbose
import com.alyaqdhan.riyal.data.Store
import com.google.android.material.color.DynamicColors

class RiyalApp : Application() {

    lateinit var prefs: Prefs
        private set
    lateinit var store: Store
        private set

    override fun onCreate() {
        super.onCreate()
        prefs = Prefs(this)
        store = Store(this)
        DynamicColors.applyToActivitiesIfAvailable(this)
        Verbose.info("Riyal started · verbose processing log is live")
        Verbose.info("permissions declared: READ_SMS only — no INTERNET, no RECEIVE_SMS, no background work")
        Verbose.flush()
    }
}
