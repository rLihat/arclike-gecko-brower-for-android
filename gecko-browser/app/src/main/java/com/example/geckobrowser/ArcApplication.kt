package com.example.geckobrowser

import android.app.Application
import android.content.Context
import org.mozilla.geckoview.GeckoRuntime
import org.mozilla.geckoview.GeckoRuntimeSettings

/**
 * One GeckoRuntime for the whole process, created lazily on first use and kept
 * until the process dies. Every tab's GeckoSession is opened against it.
 */
class ArcApplication : Application() {

    /** Set by MainActivity so notification actions can reach the live VM. */
    var browserVm: BrowserViewModel? = null

    override fun onCreate() {
        super.onCreate()
        // Firefox Accounts' Rust client requires these before any account call;
        // without them beginOauthFlow() fails and sign-in reports "unavailable".
        mozilla.appservices.init_rust_components.initialize()
        mozilla.components.support.rustlog.RustLog.enable()
        mozilla.components.support.rusthttp.RustHttpConfig.setClient(lazy {
            mozilla.components.lib.fetch.httpurlconnection.HttpURLConnectionClient()
        })
    }

    val runtime: GeckoRuntime by lazy(LazyThreadSafetyMode.SYNCHRONIZED) {
        GeckoRuntime.create(
            this,
            GeckoRuntimeSettings.Builder()
                .aboutConfigEnabled(true)
                .javaScriptEnabled(true)
                .build(),
        )
    }

    companion object {
        fun runtimeOf(context: Context): GeckoRuntime =
            (context.applicationContext as ArcApplication).runtime
    }
}
