package com.alyaqdhan.riyal

import android.app.Application
import android.content.pm.ApplicationInfo
import com.alyaqdhan.riyal.core.Dates
import com.alyaqdhan.riyal.core.Prefs
import com.alyaqdhan.riyal.core.Verbose
import com.alyaqdhan.riyal.data.Store
import com.alyaqdhan.riyal.ui.CategoryNames
import com.google.android.material.color.DynamicColors

class RiyalApp : Application() {

    lateinit var prefs: Prefs
        private set
    lateinit var store: Store
        private set

    override fun onCreate() {
        super.onCreate()
        // The in-app log always fills; this only mirrors it to logcat, which is a
        // debugging aid and cost real time on every one of the ~68,000 lines a scan
        // over a full inbox writes.
        Verbose.mirrorToLogcat =
            (applicationInfo.flags and ApplicationInfo.FLAG_DEBUGGABLE) != 0
        Verbose.attach(this)
        Dates.use(resources.configuration.locales[0])
        prefs = Prefs(this)
        CategoryNames.apply(this)
        store = Store(this, prefs.autoConfirmTransfers)
        DynamicColors.applyToActivitiesIfAvailable(this)
        Verbose.info(R.string.log_started)
        // Says what the manifest actually declares. It claimed "no INTERNET" for a
        // whole release after INTERNET was added for the update check, which is the
        // one kind of wrong a privacy claim must never be.
        Verbose.info(R.string.log_permissions_declared)
        Verbose.info(R.string.log_internet_use)
        Verbose.flush()
    }
}
