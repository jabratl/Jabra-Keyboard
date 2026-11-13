package com.mgb.keyboard

import android.content.ClipData
import android.content.ContentValues
import android.content.Context
import android.content.Intent
import android.os.Build
import android.os.Environment
import android.provider.MediaStore
import androidx.core.content.FileProvider
import java.io.File

/**
 * v0.17.57 (C2): the Android side of clip export (the file content comes from clip/ClipExport.java).
 * share() = file in cacheDir/exports + Android share sheet through the existing FileProvider (no permission).
 * saveToDownloads() = MediaStore Downloads (Android 10+, no permission). Both return one short message for the panel. Never throw.
 */
object ClipExporter {
    private const val DIR = "exports"
    private const val KEEP_FILES = 10
    private const val KEEP_MS = 2L * 24L * 60L * 60L * 1000L

    fun canSave(): Boolean = Build.VERSION.SDK_INT >= 29
    fun canSave(ctx: Context): Boolean = canSave() || ClipFolder.accessOk(ctx)   // v0.17.66 (R6): a chosen folder works on any Android version

    fun share(ctx: Context, name: String, mime: String, bytes: ByteArray): String = try {
        val dir = File(ctx.cacheDir, DIR)
        dir.mkdirs()
        cleanup(dir)
        val f = File(dir, name)
        f.writeBytes(bytes)
        val uri = FileProvider.getUriForFile(ctx, ctx.packageName + ".fileprovider", f)
        val send = Intent(Intent.ACTION_SEND)
        send.type = mime
        send.putExtra(Intent.EXTRA_STREAM, uri)
        send.putExtra(Intent.EXTRA_SUBJECT, name)
        send.clipData = ClipData.newRawUri(name, uri)
        send.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        val chooser = Intent.createChooser(send, "Export clips")
        chooser.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        ctx.startActivity(chooser)
        "Share sheet opened: $name"
    } catch (_: Throwable) { "Could not open the share sheet" + (if (canSave()) " - try Save" else "") }

    fun saveToDownloads(ctx: Context, name: String, mime: String, bytes: ByteArray): String {
        try { ClipFolder.save(ctx, name, mime, bytes)?.let { return it } } catch (_: Throwable) { }   // v0.17.66 (R6): chosen folder first
        val warn = if (ClipFolder.isSet(ctx)) "Chosen folder not reachable - " else ""
        return warn + (if (Build.VERSION.SDK_INT >= 29) save29(ctx, name, mime, bytes) else "Save needs Android 10+ - use Share")
    }

    @androidx.annotation.RequiresApi(29)
    private fun save29(ctx: Context, name: String, mime: String, bytes: ByteArray): String = try {
        val res = ctx.contentResolver
        val cv = ContentValues()
        cv.put(MediaStore.MediaColumns.DISPLAY_NAME, name)
        cv.put(MediaStore.MediaColumns.MIME_TYPE, mime)
        cv.put(MediaStore.MediaColumns.RELATIVE_PATH, Environment.DIRECTORY_DOWNLOADS)
        cv.put(MediaStore.MediaColumns.IS_PENDING, 1)
        val uri = res.insert(MediaStore.Downloads.EXTERNAL_CONTENT_URI, cv)
        if (uri == null) "Could not save the file"
        else {
            val out = res.openOutputStream(uri)
            if (out == null) { try { res.delete(uri, null, null) } catch (_: Throwable) { }; "Could not save the file" }
            else {
                out.use { it.write(bytes) }
                val done = ContentValues()
                done.put(MediaStore.MediaColumns.IS_PENDING, 0)
                res.update(uri, done, null, null)
                "Saved in Downloads: $name"
            }
        }
    } catch (_: Throwable) { "Could not save the file - use Share" }

    /** Old export files are not needed after sharing: keep the newest few (and nothing older than 2 days). */
    private fun cleanup(dir: File) {
        try {
            val now = System.currentTimeMillis()
            val fs = dir.listFiles()?.sortedByDescending { it.lastModified() } ?: return
            fs.forEachIndexed { i, f -> if (i >= KEEP_FILES || now - f.lastModified() > KEEP_MS) f.delete() }
        } catch (_: Throwable) { }
    }
}
