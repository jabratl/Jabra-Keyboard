package com.mgb.keyboard

import android.app.Activity
import android.content.Intent
import android.os.Bundle
import android.provider.Settings
import android.view.inputmethod.InputMethodManager
import android.widget.Button
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView

class SettingsActivity : Activity() {

    private lateinit var status: TextView
    private lateinit var updBtn: Button
    private var pending: Updater.Info? = null

    private fun dp(v: Int): Int = (v * resources.displayMetrics.density).toInt()

    private fun btn(label: String, onClick: () -> Unit): Button {
        val b = Button(this)
        b.text = label
        b.isAllCaps = false
        b.setOnClickListener { try { onClick() } catch (_: Throwable) { } }
        return b
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val col = LinearLayout(this)
        col.orientation = LinearLayout.VERTICAL
        col.setPadding(dp(16), dp(16), dp(16), dp(16))

        val title = TextView(this)
        title.text = "Jabra Keyboard"
        title.textSize = 22f
        col.addView(title)

        val ver = TextView(this)
        ver.text = "Version ${BuildConfig.VERSION_NAME} (build ${BuildConfig.VERSION_CODE})"
        col.addView(ver)

        col.addView(btn("1. Enable keyboard") {
            startActivity(Intent(Settings.ACTION_INPUT_METHOD_SETTINGS))
        })
        col.addView(btn("2. Choose Jabra Keyboard as current keyboard") {
            (getSystemService(INPUT_METHOD_SERVICE) as InputMethodManager).showInputMethodPicker()
        })

        val test = EditText(this)
        test.hint = "Type here to test the keyboard"
        col.addView(test)

        col.addView(btn("Check for updates") { checkUpdate() })

        updBtn = btn("Download & install") { doUpdate() }
        updBtn.visibility = android.view.View.GONE
        col.addView(updBtn)

        status = TextView(this)
        col.addView(status)

        val sv = ScrollView(this)
        sv.addView(col)
        setContentView(sv)
    }

    private fun checkUpdate() {
        status.text = "Checking..."
        updBtn.visibility = android.view.View.GONE
        Thread {
            var info: Updater.Info? = null
            try { info = Updater.fetchLatest() } catch (_: Throwable) { }
            runOnUiThread {
                try {
                    if (info == null) {
                        status.text = "Could not check (offline, or the GitHub repo is private)."
                    } else if (Updater.isNewer(info)) {
                        pending = info
                        status.text = "New version: ${info.tag}\n${info.notes}"
                        updBtn.visibility = android.view.View.VISIBLE
                    } else {
                        status.text = "You are up to date (${info.tag})."
                    }
                } catch (_: Throwable) { }
            }
        }.start()
    }

    private fun doUpdate() {
        val info = pending ?: return
        status.text = "Downloading..."
        Thread {
            var file: java.io.File? = null
            try {
                val url = info.apkUrl
                if (url != null) file = Updater.download(this, url)
            } catch (e: Throwable) {
                android.util.Log.w("MGB", "update stage 1 failed: " + e.message)
            }
            val f = file
            runOnUiThread {
                var done = false
                try {
                    if (f != null) {
                        val r = Updater.install(this, f)
                        status.text = if (r == Updater.InstallResult.NEED_PERMISSION)
                            "Allow 'Install unknown apps' for Jabra Keyboard, come back and tap Download & install again."
                        else "Installer opened. Tap Install."
                        done = true
                    }
                } catch (e: Throwable) {
                    android.util.Log.w("MGB", "install failed: " + e.message)
                }
                if (!done) {
                    try {
                        status.text = "Could not install here. Opening the release page..."
                        Updater.openPage(this, info.pageUrl) // stage 2 fallback
                    } catch (_: Throwable) { }
                }
            }
        }.start()
    }
}
