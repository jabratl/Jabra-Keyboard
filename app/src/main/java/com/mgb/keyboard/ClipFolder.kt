package com.mgb.keyboard

import android.content.Context
import android.net.Uri
import android.provider.DocumentsContract
import com.mgb.keyboard.typing.BackupPlan

/**
 * v0.17.66 (R6): the folder where Clipboard > Export > Save puts .txt / .docx / .zip files.
 * Folder = Storage Access Framework tree URI (system folder picker in Settings > Data, no storage permission), same idea as the backup folder.
 * Not set (default) or not reachable = the old behaviour (Downloads). Never throws.
 */
object ClipFolder {
    private const val KEY = "clip_exp_uri"
    private fun sp(ctx: Context) = ctx.getSharedPreferences("jabra", Context.MODE_PRIVATE)

    fun uri(ctx: Context): Uri? = try {
        val s = sp(ctx).getString(KEY, "") ?: ""
        if (s.isEmpty()) null else Uri.parse(s)
    } catch (_: Throwable) { null }

    fun isSet(ctx: Context): Boolean = uri(ctx) != null

    /** True while the app still holds read + write permission for the chosen folder. */
    fun accessOk(ctx: Context): Boolean = try {
        val u = uri(ctx)
        u != null && ctx.contentResolver.persistedUriPermissions.any { it.uri == u && it.isReadPermission && it.isWritePermission }
    } catch (_: Throwable) { false }

    /** "Download/Jabra" style label, or "Downloads" when no folder is chosen. */
    fun label(ctx: Context): String = try {
        val u = uri(ctx)
        if (u == null) "Downloads" else BackupPlan.label(DocumentsContract.getTreeDocumentId(u))
    } catch (_: Throwable) { "chosen folder" }

    /** Caller already called takePersistableUriPermission. Old permission is NOT released (it may be the backup folder too). */
    fun set(ctx: Context, uri: Uri) { try { sp(ctx).edit().putString(KEY, uri.toString()).apply() } catch (_: Throwable) { } }

    fun clear(ctx: Context) { try { sp(ctx).edit().putString(KEY, "").apply() } catch (_: Throwable) { } }

    /** Writes the file into the chosen folder. Returns the message, or null when no folder is set / it is not reachable / writing failed (caller falls back to Downloads). */
    fun save(ctx: Context, name: String, mime: String, bytes: ByteArray): String? = try {
        val tree = uri(ctx)
        if (tree == null || !accessOk(ctx)) null
        else {
            val cr = ctx.contentResolver
            val parent = DocumentsContract.buildDocumentUriUsingTree(tree, DocumentsContract.getTreeDocumentId(tree))
            val doc = DocumentsContract.createDocument(cr, parent, mime, name)
            if (doc == null) null
            else {
                val out = cr.openOutputStream(doc)
                if (out == null) { try { DocumentsContract.deleteDocument(cr, doc) } catch (_: Throwable) { }; null }
                else { out.use { it.write(bytes) }; "Saved in " + label(ctx) + ": " + name }
            }
        }
    } catch (_: Throwable) { null }
}
