package com.oddf0x.halynmotion

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import android.text.Html
import android.util.LruCache
import org.json.JSONObject
import java.io.IOException
import java.net.HttpURLConnection
import java.net.URL
import java.util.Locale

/**
 * Real online 4K+ images with verified reusable Commons licenses.
 * No artwork is generated or repackaged into the APK.
 */
class Commons4kCatalog {
    data class Image(
        val name: String,
        val artist: String,
        val license: String,
        val licenseUrl: String,
        val sourcePage: String,
        val thumbnailUrl: String,
        val originalUrl: String,
        val width: Int,
        val height: Int
    ) {
        val is4k get() = maxOf(width, height) >= 3840 && minOf(width, height) >= 2160
        val dimensionLabel get() = "$width × $height"
        val creditLine get() = "$artist · $license"
    }

    data class Page(val images: List<Image>, val nextOffset: Int?)

    private val thumbnails = object : LruCache<String, Bitmap>(12 * 1024 * 1024) {
        override fun sizeOf(key: String, value: Bitmap) = value.byteCount
    }

    fun search(term: String, offset: Int = 0): Page {
        val uri = Uri.Builder()
            .scheme("https").authority("commons.wikimedia.org")
            .appendPath("w").appendPath("api.php")
            .appendQueryParameter("action", "query")
            .appendQueryParameter("generator", "search")
            .appendQueryParameter("gsrsearch", term.trim().take(90).ifBlank { "landscape photography" })
            .appendQueryParameter("gsrnamespace", "6")
            .appendQueryParameter("gsrlimit", "50")
            .appendQueryParameter("gsroffset", offset.coerceAtLeast(0).toString())
            .appendQueryParameter("prop", "imageinfo")
            .appendQueryParameter("iiprop", "url|size|mime|extmetadata")
            .appendQueryParameter("iiurlwidth", "500")
            .appendQueryParameter("iiextmetadatafilter", "LicenseShortName|LicenseUrl|Artist|AttributionRequired")
            .appendQueryParameter("format", "json")
            .appendQueryParameter("formatversion", "2")
            .build().toString()
        val connection = openHttps(uri)
        return try {
            if (connection.responseCode != 200) {
                throw IOException("Commons returned HTTP " + connection.responseCode)
            }
            val json = connection.inputStream.bufferedReader(Charsets.UTF_8).use { it.readText() }
            val root = JSONObject(json)
            val apiError = root.optJSONObject("error")?.optString("info")
            if (!apiError.isNullOrBlank()) throw IOException(apiError.take(140))
            val pages = root.optJSONObject("query")?.optJSONArray("pages")
            val results = ArrayList<Image>()
            if (pages != null) for (i in 0 until pages.length()) {
                val page = pages.optJSONObject(i) ?: continue
                val info = page.optJSONArray("imageinfo")?.optJSONObject(0) ?: continue
                val w = info.optInt("width")
                val h = info.optInt("height")
                if (minOf(w, h) < 2160 || maxOf(w, h) < 3840) continue
                if (info.optString("mime") !in setOf("image/jpeg","image/png","image/webp")) continue
                val meta = info.optJSONObject("extmetadata") ?: continue
                val license = metadata(meta, "LicenseShortName")
                if (!acceptedLicense(license)) continue
                val original = info.optString("url")
                val thumb = info.optString("thumburl")
                val source = info.optString("descriptionurl")
                if (!trustedUpload(original) || !trustedUpload(thumb) ||
                    !source.startsWith("https://commons.wikimedia.org/wiki/File:")) continue
                val title = page.optString("title").removePrefix("File:")
                    .substringBeforeLast(".").replace('_', ' ').take(100)
                val url = metadata(meta, "LicenseUrl")
                results.add(Image(
                    title, metadata(meta, "Artist").ifBlank { "See source for author" }.take(140),
                    license, if (url.startsWith("https://")) url else "",
                    source, thumb, original, w, h
                ))
            }
            val next = root.optJSONObject("continue")?.optInt("gsroffset", -1)
                ?.takeIf { it >= 0 }
            Page(results.distinctBy { it.originalUrl }, next)
        } finally {
            connection.disconnect()
        }
    }

    fun thumbnail(image: Image): Bitmap? {
        thumbnails.get(image.thumbnailUrl)?.let { return it }
        val connection = openHttps(image.thumbnailUrl)
        return try {
            if (connection.responseCode != 200) return null
            val bitmap = connection.inputStream.use {
                BitmapFactory.decodeStream(it, null, BitmapFactory.Options().apply {
                    inPreferredConfig = Bitmap.Config.RGB_565
                })
            } ?: return null
            thumbnails.put(image.thumbnailUrl, bitmap)
            bitmap
        } catch (_: Exception) {
            null
        } finally {
            connection.disconnect()
        }
    }

    fun download(image: Image, store: PhotoWallpaperStore): Boolean {
        if (!image.is4k || !trustedUpload(image.originalUrl) ||
            !acceptedLicense(image.license)) return false
        val connection = openHttps(image.originalUrl, 60000)
        return try {
            if (connection.responseCode != 200 ||
                connection.contentLengthLong > 48L * 1024L * 1024L) return false
            connection.inputStream.use { store.installStream(it, 48L * 1024L * 1024L) }
        } finally {
            connection.disconnect()
        }
    }

    private fun acceptedLicense(value: String): Boolean {
        val license = value.trim().lowercase(Locale.ROOT)
        return license == "cc0" || license.startsWith("cc0 ") ||
            license.startsWith("public domain") ||
            license.startsWith("cc by ") || license.startsWith("cc by-sa ")
    }

    private fun trustedUpload(s: String): Boolean = try {
        val u = Uri.parse(s)
        u.scheme == "https" && u.host == "upload.wikimedia.org"
    } catch (_: Exception) { false }

    private fun metadata(json: JSONObject, key: String): String {
        val html = json.optJSONObject(key)?.optString("value").orEmpty()
        return if (html.isBlank()) "" else Html.fromHtml(html, Html.FROM_HTML_MODE_LEGACY)
            .toString().replace(Regex("\\s+"), " ").trim()
    }

    private fun openHttps(s: String, readTimeoutMs: Int = 18000): HttpURLConnection {
        val u = URL(s)
        if (u.protocol != "https" ||
            u.host !in setOf("commons.wikimedia.org", "upload.wikimedia.org"))
            throw IOException("Untrusted host")
        return (u.openConnection() as HttpURLConnection).apply {
            connectTimeout = 12000
            readTimeout = readTimeoutMs
            instanceFollowRedirects = true
            setRequestProperty("User-Agent",
                "HALYNMotion/0.9.0 (https://github.com/oddfoxerr-real/HALYN-motion) Android")
        }
    }
}
