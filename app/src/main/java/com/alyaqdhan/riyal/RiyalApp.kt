package com.alyaqdhan.riyal

import android.app.Application
import android.content.Context
import android.content.pm.ApplicationInfo
import com.alyaqdhan.riyal.core.Currencies
import com.alyaqdhan.riyal.core.Dates
import com.alyaqdhan.riyal.core.Language
import com.alyaqdhan.riyal.core.Prefs
import com.alyaqdhan.riyal.core.Verbose
import com.alyaqdhan.riyal.data.Store
import com.alyaqdhan.riyal.ui.MainViewModel
import com.alyaqdhan.riyal.ui.Words
import com.google.android.material.color.DynamicColors
import androidx.lifecycle.ViewModelProvider

class RiyalApp : Application() {

    lateinit var prefs: Prefs
        private set
    lateinit var store: Store
        private set

    /**
     * The one view model every screen shares. Each page is an activity of its own - so
     * the phone moves between them with its own transition - and an activity's view
     * model would end with it, so this one belongs to the app instead.
     */
    val viewModel: MainViewModel by lazy {
        ViewModelProvider.AndroidViewModelFactory.getInstance(this).create(MainViewModel::class.java)
    }

    override fun onCreate() {
        super.onCreate()
        // The in-app log always fills; this only mirrors it to logcat, which is a
        // debugging aid and cost real time on every one of the ~68,000 lines a scan
        // over a full inbox writes.
        Verbose.mirrorToLogcat =
            (applicationInfo.flags and ApplicationInfo.FLAG_DEBUGGABLE) != 0
        Verbose.attach(Language.latinDigits(this))
        Dates.use(resources.configuration.locales[0])
        prefs = Prefs(this)
        Currencies.setOverrides(Currencies.decode(prefs.fxRates))
        Words.apply(this)
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

/** The shared view model, from any screen of the app. */
val Context.riyalViewModel: MainViewModel
    get() = (applicationContext as RiyalApp).viewModel
