package com.mgb.keyboard

import android.content.Context
import android.view.View
import android.widget.Button
import android.widget.LinearLayout
import android.widget.TextView
import com.mgb.keyboard.clip.ClipList
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * v0.4.1 "Data & Backup" page (launcher screen, group 6). Built in code, called inside try/catch.
 * Shows what is stored, backups with [Restore] [Delete], [Backup now] [Export to file] [Import from file], privacy info.
 * Restore = preview (counts + date) -> second tap "Sure?" -> BackupStore.restore (safety copy first). Nothing leaves the phone.
 * Export / Import use the system file picker through SettingsActivity (no storage permission).
 */
object DataPage {
    fun build(ctx: Context): View {
        val prefs = JabraPrefs(ctx)
        val d = ctx.resources.displayMetrics.density
        fun dp(v: Int) = (v * d).toInt()
        val root = LinearLayout(ctx); root.orientation = LinearLayout.VERTICAL
        val info = LinearLayout(ctx); info.orientation = LinearLayout.VERTICAL
        val msg = TextView(ctx); msg.setPadding(0, dp(6), 0, dp(6))
        val list = LinearLayout(ctx); list.orientation = LinearLayout.VERTICAL
        val act = ctx as? SettingsActivity

        fun btn(label: String, on: (Button) -> Unit): Button {
            val b = Button(ctx); b.text = label; b.isAllCaps = false
            b.setOnClickListener { try { on(b) } catch (_: Throwable) { } }
            return b
        }
        fun line(t: String): TextView { val v = TextView(ctx); v.text = t; v.setPadding(0, dp(2), 0, dp(2)); return v }
        fun size(n: Long) = if (n < 1024) "$n B" else if (n < 1048576) "${n / 1024} KB" else String.format(Locale.US, "%.1f MB", n / 1048576.0)
        fun fileSize(f: File): Long = try { if (f.isDirectory) (f.listFiles() ?: emptyArray()).sumOf { fileSize(it) } else f.length() } catch (_: Throwable) { 0L }
        fun nice(t: Long) = if (t <= 0) "?" else SimpleDateFormat("yyyy-MM-dd HH:mm", Locale.US).format(Date(t))

        fun fill() {
            info.removeAllViews(); list.removeAllViews()
            try {
                val cl = try { ClipList.parse(prefs.str("clips_v1", "")) } catch (_: Throwable) { ClipList() }
                info.addView(line("Clipboard: ${cl.size()} clips, ${try { cl.pinnedCount() } catch (_: Throwable) { 0 }} pinned"))
                val pc = try { val s = prefs.str("fmt_presets", ""); if (s.isBlank()) 0 else try { org.json.JSONArray(s).length() } catch (_: Throwable) { org.json.JSONObject(s).length() } } catch (_: Throwable) { 0 }
                info.addView(line("Formatter presets: $pc"))
                info.addView(line("Custom themes: 0 (later)   Vault entries: " + (try { VaultStore.countOnDisk(ctx).let { if (it < 0) "file damaged" else it.toString() } } catch (_: Throwable) { "?" })))   // v0.4.4
                val keys = try { ctx.getSharedPreferences("jabra", Context.MODE_PRIVATE).all.keys.count { !it.startsWith("bk_") && !it.startsWith("upd") } } catch (_: Throwable) { 0 }
                info.addView(line("Settings keys: $keys"))
                val used = fileSize(ctx.filesDir) + fileSize(File(ctx.applicationInfo.dataDir, "shared_prefs/jabra.xml"))
                info.addView(line("Storage used: ${size(used)}"))
                val last = prefs.str("bk_last", "")
                info.addView(line("Last backup: " + (if (last.isEmpty()) "none yet" else last)))
            } catch (_: Throwable) { }
            try {
                val files = BackupStore.list(ctx)
                if (files.isEmpty()) list.addView(line("No backup files yet. Tap Backup now."))
                for (f in files) {
                    val row = LinearLayout(ctx); row.orientation = LinearLayout.VERTICAL
                    row.addView(line(f.name + "  (" + size(f.length()) + ")"))
                    val bar = LinearLayout(ctx); bar.orientation = LinearLayout.HORIZONTAL
                    var armed = false
                    val rb = btn("Restore") { b ->
                        if (!armed) {
                            val p = BackupStore.preview(f)
                            if (p[0] < 0) { msg.text = "This file is damaged. Nothing changed."; return@btn }
                            armed = true
                            msg.text = "Backup of ${nice(p[2])}: ${p[0]} settings, ${p[1]} data files. Tap again to restore (a safety copy of now is saved first)."
                            b.text = "Sure? Restore"
                        } else {
                            armed = false; b.text = "Restore"; msg.text = "Restoring..."
                            Thread {
                                val r = try { BackupStore.restore(ctx, f) } catch (_: Throwable) { "Restore failed" }
                                act?.runOnUiThread { try { msg.text = r; fill() } catch (_: Throwable) { } }
                            }.start()
                        }
                    }
                    var delArmed = false
                    val db = btn("Delete") { b ->
                        if (!delArmed) { delArmed = true; b.text = "Sure? Delete" }
                        else { try { f.delete() } catch (_: Throwable) { }; msg.text = "Deleted ${f.name}"; fill() }
                    }
                    bar.addView(rb); bar.addView(db)
                    row.addView(bar)
                    list.addView(row)
                }
            } catch (_: Throwable) { }
        }

        root.addView(info)
        root.addView(btn("Backup now") {
            msg.text = "Saving..."
            Thread {
                val r = try { BackupStore.backupNow(ctx) } catch (_: Throwable) { "Backup failed" }
                act?.runOnUiThread { try { msg.text = r; fill() } catch (_: Throwable) { } }
            }.start()
        })
        root.addView(btn("Export to file") { act?.exportBackup() ?: run { msg.text = "Not available here" } })
        root.addView(btn("Import from file") { act?.importBackup() ?: run { msg.text = "Not available here" } })
        fun toggleAuto(b: Button) { val nv = !prefs.bool("bk_auto", true); prefs.setBool("bk_auto", nv); b.text = "Auto backup (daily): " + if (nv) "ON" else "OFF" }
        val ab = btn("") { toggleAuto(it) }; ab.text = "Auto backup (daily): " + if (prefs.bool("bk_auto", true)) "ON" else "OFF"; root.addView(ab)
        val choices = intArrayOf(3, 5, 10)
        val kb = btn("") { b ->
            val i = choices.indexOf(prefs.int("bk_keep", 5)); val nv = choices[(i + 1) % choices.size]
            prefs.setInt("bk_keep", nv); b.text = "Keep newest copies: $nv"
        }
        kb.text = "Keep newest copies: " + prefs.int("bk_keep", 5); root.addView(kb)
        root.addView(msg)
        root.addView(line("Backups"))
        root.addView(list)
        root.addView(line("Privacy"))
        root.addView(line("Everything is saved only on this phone (settings, clips, presets, backups). Nothing is sent anywhere. The only internet use is \"Check for updates\". Permissions: Internet (update check), Install packages (update), Vibrate. Export / Import use the system file picker, no storage permission."))
        act?.refreshData = { try { fill() } catch (_: Throwable) { } }
        act?.dataMsg = { m -> try { msg.text = m } catch (_: Throwable) { } }
        fill()
        return root
    }
}
