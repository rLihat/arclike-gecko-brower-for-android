package com.example.geckobrowser

import android.app.Application
import android.content.Context
import org.mozilla.geckoview.GeckoRuntime
import org.mozilla.geckoview.GeckoRuntimeSettings
import java.io.File

/**
 * One GeckoRuntime for the whole process, created lazily on first use and kept
 * until the process dies. Every tab's GeckoSession is opened against it.
 */
class ArcApplication : Application() {

    /** Set by MainActivity so notification actions can reach the live VM. */
    var browserVm: BrowserViewModel? = null

    /** Gecko config file generated before the runtime starts. */
    private var geckoConfigPath: String? = null

    override fun onCreate() {
        super.onCreate()
        // Rust/NSS init is deferred to the first account call: appservices 161
        // ships its own NSS libraries, and initialising them before GeckoView
        // starts crashed the app at launch.
        geckoConfigPath = writeGeckoConfig()
    }

    /**
     * Hard-off telemetry/data submission and optional DNS-over-HTTPS, applied
     * through Gecko prefs before Gecko starts. Nothing here uploads anything.
     */
    private fun writeGeckoConfig(): String {
        val prefs = getSharedPreferences("arc_browser", Context.MODE_PRIVATE)
        val resist = prefs.getBoolean("resist_fingerprinting", false)
        val file = File(filesDir, "geckoview-config.yaml")
        file.writeText(
            """
            env: {}
            args: []
            prefs:
              toolkit.telemetry.enabled: false
              toolkit.telemetry.unified: false
              toolkit.telemetry.geckoview: false
              datareporting.healthreport.uploadEnabled: false
              datareporting.policy.dataSubmissionEnabled: false
              privacy.trackingprotection.fingerprinting.enabled: true
              privacy.trackingprotection.cryptomining.enabled: true
              privacy.fingerprintingProtection: true
              privacy.fingerprintingProtection.pbmode: true
              privacy.resistFingerprinting: $resist
              privacy.resistFingerprinting.pbmode: $resist
            """.trimIndent(),
        )
        return file.absolutePath
    }

    val runtime: GeckoRuntime by lazy(LazyThreadSafetyMode.SYNCHRONIZED) {
        val prefs = getSharedPreferences("arc_browser", Context.MODE_PRIVATE)
        val doh = prefs.getBoolean("doh_enabled", true)
        GeckoRuntime.create(
            this,
            GeckoRuntimeSettings.Builder()
                .aboutConfigEnabled(false)
                .javaScriptEnabled(true)
                .remoteDebuggingEnabled(false)
                .consoleOutput(false)
                .debugLogging(false)
                .globalPrivacyControlEnabled(true)
                // Native DoH API in GV 161; FIRST keeps a fallback if the resolver fails.
                .trustedRecursiveResolverMode(
                    if (doh) GeckoRuntimeSettings.TRR_MODE_FIRST else GeckoRuntimeSettings.TRR_MODE_OFF,
                )
                .trustedRecursiveResolverUri("https://mozilla.cloudflare-dns.com/dns-query")
                // Process isolation is intentionally off: GV 161 breaks page
                // loads with it enabled on this device class.
                .extensionsProcessEnabled(false)
                .isolatedProcessEnabled(false)
                .configFilePath(geckoConfigPath)
                .build(),
        )
    }

    companion object {
        fun runtimeOf(context: Context): GeckoRuntime =
            (context.applicationContext as ArcApplication).runtime
    }
}
