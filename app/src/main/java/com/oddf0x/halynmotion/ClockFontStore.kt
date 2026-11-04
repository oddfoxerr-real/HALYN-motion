package com.oddf0x.halynmotion

import android.content.Context
import android.graphics.Typeface
import android.net.Uri
import android.provider.OpenableColumns
import java.io.File
import java.io.FileOutputStream

/** Local-only clock typefaces. Imported files never leave the device. */
object ClockFontStore {
    private const val FONT_FILE = "halyn_clock_custom.font"
    private const val MAX_BYTES = 3_000_000L

    val names = listOf("Default", "Rounded", "Serif", "Mono", "Condensed", "Bold", "My font")

    fun resolve(context: Context, style: Int): Typeface? = when (style) {
        1 -> Typeface.create("sans-serif-rounded", Typeface.NORMAL)
        2 -> Typeface.create("serif", Typeface.NORMAL)
        3 -> Typeface.create("monospace", Typeface.NORMAL)
        4 -> Typeface.create("sans-serif-condensed", Typeface.NORMAL)
        5 -> Typeface.create("sans-serif", Typeface.BOLD)
        6 -> try {
            val file = File(context.filesDir, FONT_FILE)
            if (file.isFile) Typeface.createFromFile(file) else null
        } catch (_: RuntimeException) { null }
        else -> null
    }

    fun hasCustomFont(context: Context): Boolean = File(context.filesDir, FONT_FILE).isFile

    /**
     * Import a SAF-selected .ttf/.otf file, with a hard size ceiling.
     * Typeface validation occurs BEFORE replacing a previous working font.
     */
    fun install(context: Context, uri: Uri): Boolean {
        var filename: String? = null
        try {
            context.contentResolver.query(uri, arrayOf(OpenableColumns.DISPLAY_NAME),
                null, null, null)?.use { cursor ->
                if (cursor.moveToFirst()) filename = cursor.getString(0)
            }
        } catch (_: RuntimeException) { return false }
        val extension = filename?.lowercase(java.util.Locale.US) ?: return false
        if (!extension.endsWith(".ttf") && !extension.endsWith(".otf")) return false

        val temp = File(context.cacheDir, "halyn-clock-font-incoming.tmp")
        return try {
            context.contentResolver.openInputStream(uri)?.use { input ->
                FileOutputStream(temp).use { output ->
                    val bytes = ByteArray(32 * 1024)
                    var total = 0L
                    while (true) {
                        val read = input.read(bytes)
                        if (read < 0) break
                        total += read
                        if (total > MAX_BYTES) return false
                        output.write(bytes, 0, read)
                    }
                    output.fd.sync()
                }
            } ?: return false
            if (temp.length() < 256L || temp.length() > MAX_BYTES) return false
            Typeface.createFromFile(temp)
            val target = File(context.filesDir, FONT_FILE)
            if (!temp.renameTo(target)) {
                // Replace only after successful validation of the incoming file.
                temp.copyTo(target, overwrite = true)
                temp.delete()
            }
            true
        } catch (_: Exception) {
            false
        } finally {
            temp.delete()
        }
    }
}
