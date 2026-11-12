package com.example.geckobrowser

import android.os.Bundle
import android.view.inputmethod.EditorInfo
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import com.example.geckobrowser.databinding.ActivityMainBinding
import org.mozilla.geckoview.GeckoRuntime
import org.mozilla.geckoview.GeckoRuntimeSettings
import org.mozilla.geckoview.GeckoSession
import org.mozilla.geckoview.WebExtension

class MainActivity : AppCompatActivity() {

    private lateinit var binding: ActivityMainBinding
    private lateinit var session: GeckoSession

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)

        val runtime = GeckoRuntimeProvider.get(this)
        session = GeckoSession()
        session.open(runtime)
        binding.geckoView.setSession(session)

        session.navigationDelegate = object : GeckoSession.NavigationDelegate {
            override fun onLocationChange(session: GeckoSession, url: String?) {
                runOnUiThread { binding.urlBar.setText(url ?: "") }
            }
        }

        binding.goButton.setOnClickListener { loadFromBar() }
        binding.urlBar.setOnEditorActionListener { _, actionId, _ ->
            if (actionId == EditorInfo.IME_ACTION_GO) {
                loadFromBar(); true
            } else false
        }

        binding.installExtensionButton.setOnClickListener {
            installExampleExtension(runtime)
        }

        loadFromBar()
    }

    private fun loadFromBar() {
        val input = binding.urlBar.text.toString().trim()
        if (input.isNotEmpty()) {
            val url = if (input.contains("://")) input else "https://$input"
            session.loadUri(url)
        }
    }

    private fun installExampleExtension(runtime: GeckoRuntime) {
        // Replace with a GeckoView-compatible, signed extension XPI URL (e.g. from AMO).
        // Many desktop Firefox extensions are not compatible with GeckoView/Android.
        val extensionUri = "https://addons.mozilla.org/firefox/downloads/file/XXXX/addon.xpi"
        runtime.webExtensionController.install(extensionUri).accept(
            { ext: WebExtension? ->
                runOnUiThread {
                    Toast.makeText(this, "Installed: ${ext?.metaData?.name}", Toast.LENGTH_LONG).show()
                }
            },
            { err ->
                runOnUiThread {
                    Toast.makeText(this, "Install failed: ${err.message}", Toast.LENGTH_LONG).show()
                }
            }
        )
    }

    override fun onDestroy() {
        session.close()
        super.onDestroy()
    }
}

/** Simple app-wide GeckoRuntime holder. In production, scope this to your Application. */
object GeckoRuntimeProvider {
    @Volatile private var runtime: GeckoRuntime? = null

    fun get(activity: AppCompatActivity): GeckoRuntime {
        return runtime ?: synchronized(this) {
            runtime ?: GeckoRuntime.create(
                activity.applicationContext,
                GeckoRuntimeSettings.Builder().build()
            ).also { runtime = it }
        }
    }
}
