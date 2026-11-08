package com.oddf0x.halynmotion

import android.content.Context
import android.graphics.Typeface
import android.net.Uri
import android.provider.OpenableColumns
import android.util.Log
import java.io.File
import java.io.FileInputStream
import java.io.FileOutputStream
import java.security.MessageDigest
import java.util.Locale
import java.util.UUID

/**
 * Unlimited-count local font library. No arbitrary import size quota.
 *
 * Font copies stream to private app storage; only the device's available
 * disk space and Android's own font parser impose natural limits.
 * Imports never overwrite previously installed fonts.
 */
object ClockFontStore {
    private const val DIRECTORY = "clock_fonts"
    private const val OLD_FONT_FILE = "halyn_clock_custom.font"
    private const val LEGACY_ID = "legacy-font"
    private const val PREFS = "halyn_clock_font_library"
    private const val LOG = "HALYN_FONTS"

    data class ImportedFont(val id: String, val name: String, val bytes: Long)

    // Explicit Android families with visibly different shapes and widths.
    // This list excludes imports; those are browsed independently.
    val names = listOf("Default", "Casual", "Serif", "Monospace", "Condensed", "Heavy")

    fun presetTypeface(style: Int): Typeface? = when (style) {
        1 -> Typeface.create("casual", Typeface.NORMAL)
        2 -> Typeface.create(Typeface.SERIF, Typeface.NORMAL)
        3 -> Typeface.create(Typeface.MONOSPACE, Typeface.NORMAL)
        4 -> Typeface.create("sans-serif-condensed", Typeface.NORMAL)
        5 -> Typeface.create("sans-serif-black", Typeface.BOLD)
        else -> null // Default preserves the clock face's original typography.
    }

    private fun directory(context: Context): File =
        File(context.applicationContext.filesDir, DIRECTORY).apply { if (!exists()) mkdirs() }

    private fun fileFor(context: Context, id: String): File? {
        // Only importer-generated names may become filesystem paths.
        if (id != LEGACY_ID && !Regex("[0-9a-fA-F-]{36}|[0-9a-f]{64}").matches(id)) return null
        return File(directory(context), "$id.font")
    }

    private fun libraryPrefs(context: Context) =
        context.applicationContext.getSharedPreferences(PREFS, Context.MODE_PRIVATE)

    @Synchronized
    private fun migrateLegacy(context: Context) {
        val old = File(context.filesDir, OLD_FONT_FILE)
        if (!old.isFile) return
        val dest = fileFor(context, LEGACY_ID) ?: return
        if (dest.exists()) return
        try {
            old.copyTo(dest, overwrite = false)
            Typeface.createFromFile(dest)
            libraryPrefs(context).edit().putString("name_$LEGACY_ID", "Previously imported font").apply()
        } catch (e: Exception) {
            dest.delete()
            Log.w(LOG, "Could not migrate previous clock font", e)
        }
    }

    @Synchronized
    fun list(context: Context): List<ImportedFont> {
        migrateLegacy(context)
        val prefs = libraryPrefs(context)
        return directory(context).listFiles()?.asSequence()
            ?.filter { it.isFile && it.extension == "font" && it.length() > 0L }
            ?.mapNotNull { file ->
                val id = file.name.removeSuffix(".font")
                if (fileFor(context, id) == null) null else ImportedFont(
                    id,
                    prefs.getString("name_$id", null)?.takeIf { it.isNotBlank() }
                        ?: if (id == LEGACY_ID) "Previously imported font" else "Imported font",
                    file.length()
                )
            }?.sortedWith(compareBy<ImportedFont> { it.name.lowercase(Locale.ROOT) }
                .thenBy { it.id })?.toList() ?: emptyList()
    }

    fun hasCustomFont(context: Context): Boolean = list(context).isNotEmpty()

    fun resolve(context: Context, style: Int, customId: String? = null): Typeface? {
        if (style in 0..5) return presetTypeface(style)
        if (style != 6) return null

        val selected = list(context)
        val entry = selected.firstOrNull { it.id == customId }
            ?: selected.firstOrNull { it.id == LEGACY_ID }
            ?: selected.firstOrNull()
            ?: return null
        val file = fileFor(context, entry.id) ?: return null
        return try {
            Typeface.createFromFile(file)
        } catch (e: RuntimeException) {
            Log.w(LOG, "Failed to load imported font: ${entry.name}", e)
            null
        }
    }

    fun preview(context: Context, id: String): Typeface? {
        val file = fileFor(context, id) ?: return null
        if (!file.isFile) return null
        return try { Typeface.createFromFile(file) }
        catch (_: RuntimeException) { null }
    }

    private fun queryName(context: Context, uri: Uri): String? =
        try {
            context.contentResolver.query(uri, arrayOf(OpenableColumns.DISPLAY_NAME),
                null, null, null)?.use { cursor ->
                if (cursor.moveToFirst() && !cursor.isNull(0)) cursor.getString(0)
                else null
            }
        } catch (_: Exception) { null }

    private fun isFontFile(file: File): Boolean {
        if (!file.isFile || file.length() < 4L) return false
        return FileInputStream(file).use { input ->
            val magic = ByteArray(4)
            if (input.read(magic) != 4) return@use false
            val signature = String(magic, Charsets.ISO_8859_1)
            signature == "OTTO" || signature == "ttcf" || signature == "true" ||
                magic.contentEquals(byteArrayOf(0, 1, 0, 0))
        }
    }

    /**
     * Streams an individual font directly into the private library and
     * validates before committing. No 3 MB limit and no cap on font count.
     * Returns the permanent imported font ID on success.
     */
    @Synchronized
    fun install(context: Context, uri: Uri): String? {
        val filename = queryName(context, uri) ?: uri.lastPathSegment ?: return null
        val lowered = filename.lowercase(Locale.ROOT)
        if (!(lowered.endsWith(".ttf") || lowered.endsWith(".otf") ||
                    lowered.endsWith(".ttc"))) return null

        val store = directory(context)
        val temp = File(store, "${UUID.randomUUID()}.importing")
        var incompleteTarget: File? = null

        try {
            val input = context.contentResolver.openInputStream(uri) ?: return null
            val digest = MessageDigest.getInstance("SHA-256")
            input.use { source ->
                FileOutputStream(temp).use { output ->
                    val buffer = ByteArray(64 * 1024)
                    while (true) {
                        val count = source.read(buffer)
                        if (count < 0) break
                        if (count == 0) continue
                        digest.update(buffer, 0, count)
                        output.write(buffer, 0, count)
                    }
                    output.fd.sync()
                }
            }
            if (!isFontFile(temp)) return null
            // Reject corrupted or unsupported font data without losing any
            // previously imported fonts.
            Typeface.createFromFile(temp)

            val fingerprint = digest.digest()
            // Content-addressed filenames make duplicate detection O(1),
            // even with thousands of imported fonts and no imposed quota.
            val id = fingerprint.joinToString("") { byte ->
                "%02x".format(Locale.ROOT, byte.toInt() and 0xff)
            }
            val target = fileFor(context, id) ?: return null
            if (target.isFile) {
                if (target.length() == temp.length() && isFontFile(target)) {
                    return id
                }
                // Incomplete files can remain after interrupted storage writes.
                if (!target.delete()) return null
            }
            incompleteTarget = target
            if (!temp.renameTo(target)) {
                temp.copyTo(target, overwrite = false)
                temp.delete()
            }
            val readableName = filename.substringBeforeLast('.').trim()
                .ifBlank { "Imported font" }.take(100)
            libraryPrefs(context).edit().putString("name_$id", readableName).apply()
            incompleteTarget = null
            return id
        } catch (e: Exception) {
            incompleteTarget?.delete()
            Log.w(LOG, "Font import failed", e)
            return null
        } finally {
            temp.delete()
        }
    }

    @Synchronized
    fun remove(context: Context, id: String): Boolean {
        val entry = list(context).firstOrNull { it.id == id } ?: return false
        val target = fileFor(context, entry.id) ?: return false
        if (!target.delete()) return false
        libraryPrefs(context).edit().remove("name_$id").apply()
        if (id == LEGACY_ID) File(context.filesDir, OLD_FONT_FILE).delete()
        return true
    }
}
