package com.oddf0x.halynmotion

import android.app.Activity
import android.app.AlertDialog
import android.content.Intent
import android.graphics.Color
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.net.Uri
import android.view.Gravity
import android.view.inputmethod.EditorInfo
import android.view.View
import android.widget.EditText
import android.widget.FrameLayout
import android.widget.HorizontalScrollView
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.Toast
import java.util.concurrent.Executors
import java.util.concurrent.RejectedExecutionException

/**
 * Live 4K wallpaper gallery. No generated images or bundled photo assets.
 *
 * All networking and original-resolution decoding happens off the UI thread.
 * Image attribution and licensing are displayed before download.
 */
class CommonsGalleryView(
    private val host: Activity,
    private val onInstalled: (Commons4kCatalog.Image) -> Unit
) : LinearLayout(host) {
    private val source = Commons4kCatalog()
    private val io = Executors.newSingleThreadExecutor()
    private val thumbs = Executors.newFixedThreadPool(2)
    private val query = EditText(host)
    private val results = LinearLayout(host)
    private val status = TextView(host)
    private val more = TextView(host)
    private var nextOffset: Int? = null
    private var requestId = 0
    private var displayed = 0
    private var activeQuery = "landscape photography"
    private var pending = false
    private var destroyed = false

    private val ink = Color.rgb(249, 249, 251)
    private val muted = Color.rgb(158, 162, 175)
    private val surface = Color.rgb(29, 31, 38)
    private val outline = Color.rgb(58, 60, 72)
    private val blue = Color.rgb(147, 185, 255)

    private fun dp(n: Int) = (n * resources.displayMetrics.density + .5f).toInt()
    private fun label(s: String, size: Float, color: Int = ink, bold: Boolean = false) =
        TextView(host).apply {
            text = s
            setTextColor(color)
            textSize = size
            if (bold) setTypeface(Typeface.DEFAULT, Typeface.BOLD)
            includeFontPadding = false
        }
    private fun bg(color: Int, radius: Int, border: Int? = null) =
        GradientDrawable().apply {
            setColor(color)
            cornerRadius = dp(radius).toFloat()
            if (border != null) setStroke(dp(1), border)
        }

    init {
        orientation = VERTICAL
        addView(label("Explore online · 4K+", 21f, ink, true).apply {
            setPadding(0, dp(8), 0, dp(7))
        })
        addView(label(
            "Real photographs and art from Wikimedia Commons. " +
                "4K+ originals only · Source, artist and license displayed.",
            12f, muted
        ).apply { setPadding(0, 0, 0, dp(15)) })

        val categories = listOf(
            "Nature" to "landscape photography",
            "Cars" to "sports car photograph",
            "Flowers" to "rose flower photography",
            "Space" to "nebula NASA photograph",
            "City" to "city skyline night",
            "Art" to "illustration digital art",
            "Mountains" to "mountain photography"
        )
        val chips = LinearLayout(host).apply { orientation = HORIZONTAL }
        categories.forEach { (name, term) ->
            val chip = label(name, 12f, ink, true).apply {
                background = bg(surface, 12, outline)
                gravity = Gravity.CENTER
                setPadding(dp(15), 0, dp(15), 0)
                isFocusable = true
                isClickable = true
                contentDescription = "Search 4K $name wallpapers"
                setOnClickListener {
                    query.setText(term)
                    search(reset = true)
                }
            }
            chips.addView(chip, LayoutParams(-2, dp(42)).apply { marginEnd = dp(8) })
        }
        addView(HorizontalScrollView(host).apply {
            isHorizontalScrollBarEnabled = false
            addView(chips)
        }, LayoutParams(-1, dp(47)))

        val searchRow = LinearLayout(host).apply {
            orientation = HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            setPadding(0, dp(13), 0, dp(9))
        }
        query.apply {
            setSingleLine(true)
            setText("landscape photography")
            hint = "Search real 4K images"
            setTextColor(ink)
            setHintTextColor(muted)
            setTextSize(14f)
            imeOptions = EditorInfo.IME_ACTION_SEARCH
            background = bg(surface, 13, outline)
            setPadding(dp(13), 0, dp(13), 0)
            setOnEditorActionListener { _, actionId, _ ->
                if (actionId == EditorInfo.IME_ACTION_SEARCH) {
                    search(reset = true)
                    true
                } else false
            }
        }
        searchRow.addView(query, LayoutParams(0, dp(48), 1f))
        val searchButton = label("Search", 13f, Color.rgb(10, 13, 21), true).apply {
            background = bg(blue, 13)
            gravity = Gravity.CENTER
            isClickable = true
            isFocusable = true
            contentDescription = "Search online 4K wallpapers"
            setOnClickListener { search(reset = true) }
        }
        searchRow.addView(searchButton, LayoutParams(dp(80), dp(48)).apply {
            marginStart = dp(8)
        })
        addView(searchRow)

        status.apply {
            setTextColor(muted)
            textSize = 12f
            text = "Loading real 4K images…"
            setPadding(0, dp(5), 0, dp(10))
        }
        addView(status)

        results.orientation = VERTICAL
        addView(results, LayoutParams(-1, -2))

        more.apply {
            setTextColor(blue)
            textSize = 14f
            gravity = Gravity.CENTER
            text = "Load more original 4K images"
            setPadding(dp(7), dp(15), dp(7), dp(15))
            isFocusable = true
            isClickable = true
            contentDescription = "Load more 4K wallpapers"
            setOnClickListener { search(reset = false) }
            visibility = View.GONE
        }
        addView(more, LayoutParams(-1, dp(56)))

        addView(label(
            "Online images are credited to their original creators. " +
                "Available licenses vary by image; open the source page to review rights.",
            11f, muted
        ).apply { setPadding(0, dp(15), 0, dp(12)) })
    }

    fun loadInitial() = search(reset = true)

    private fun search(reset: Boolean) {
        if (destroyed || pending) return
        val term = query.text.toString().trim().ifBlank { "landscape photography" }
        if (reset) {
            requestId++
            displayed = 0
            activeQuery = term
            nextOffset = 0
            results.removeAllViews()
        } else if (term != activeQuery) {
            search(reset = true)
            return
        }
        val offset = nextOffset ?: return
        val token = requestId
        pending = true
        more.visibility = View.GONE
        status.text = if (displayed == 0) "Finding licensed 4K images…" else "Finding more 4K images…"
        io.execute {
            try {
                val page = source.search(activeQuery, offset)
                onUi {
                    if (token != requestId) return@onUi
                    nextOffset = page.nextOffset
                    page.images.forEach { addCard(it, token) }
                    status.text = if (displayed == 0)
                        "No matching 4K photos on this page. Try another term or load more."
                    else "$displayed verified 4K images · Wikimedia Commons"
                    more.visibility = if (nextOffset != null) View.VISIBLE else View.GONE
                    pending = false
                }
            } catch (error: Exception) {
                onUi {
                    if (token != requestId) return@onUi
                    pending = false
                    more.visibility = View.VISIBLE
                    status.text = "Online images unavailable: " +
                        (error.message?.take(110) ?: "check your connection")
                }
            }
        }
    }

    private fun addCard(item: Commons4kCatalog.Image, token: Int) {
        val w = (resources.displayMetrics.widthPixels / resources.displayMetrics.density)
            .toInt()
        val cardHeight = if (w < 350) 202 else 230
        val index = displayed++
        var row: LinearLayout? = null
        if (index % 2 == 0) {
            row = LinearLayout(host).apply { orientation = HORIZONTAL }
            results.addView(row, LayoutParams(-1, -2).apply { bottomMargin = dp(10) })
        } else {
            row = results.getChildAt(results.childCount - 1) as? LinearLayout
        }
        val tile = FrameLayout(host).apply {
            background = bg(surface, 15)
            clipToOutline = true
            isClickable = true
            isFocusable = true
            contentDescription = "4K image: " + item.name + ", " + item.dimensionLabel +
                ", " + item.license + ". View image details and download."
            setOnClickListener { showDetails(item) }
        }
        val photo = ImageView(host).apply {
            scaleType = ImageView.ScaleType.CENTER_CROP
            setBackgroundColor(surface)
        }
        tile.addView(photo, FrameLayout.LayoutParams(-1, -1))
        val fade = View(host).apply {
            background = GradientDrawable(
                GradientDrawable.Orientation.TOP_BOTTOM,
                intArrayOf(Color.TRANSPARENT, 0xE9000000.toInt())
            )
        }
        tile.addView(fade, FrameLayout.LayoutParams(-1, dp(100), Gravity.BOTTOM))
        val textGroup = LinearLayout(host).apply {
            orientation = VERTICAL
            setPadding(dp(10), dp(6), dp(10), dp(11))
            addView(label(item.name, 12f, Color.WHITE, true).apply {
                maxLines = 2
                ellipsize = android.text.TextUtils.TruncateAt.END
            })
            addView(label(item.dimensionLabel + " · " + item.license,
                10f, Color.rgb(206, 220, 235)).apply {
                setPadding(0, dp(4), 0, 0)
                maxLines = 1
                ellipsize = android.text.TextUtils.TruncateAt.END
            })
        }
        tile.addView(textGroup, FrameLayout.LayoutParams(-1, -2, Gravity.BOTTOM))
        row?.addView(tile, LayoutParams(0, dp(cardHeight), 1f).apply {
            if (index % 2 == 0) marginEnd = dp(5) else marginStart = dp(5)
        })
        try {
            thumbs.execute {
                val bitmap = source.thumbnail(item)
                onUi {
                    if (token == requestId && bitmap != null) photo.setImageBitmap(bitmap)
                }
            }
        } catch (_: RejectedExecutionException) { }
    }

    private fun showDetails(item: Commons4kCatalog.Image) {
        if (destroyed) return
        val details = item.dimensionLabel + " · Original resolution\n\n" +
            "Creator: " + item.artist + "\nLicense: " + item.license +
            "\nSource: Wikimedia Commons\n\n" +
            "This file will be downloaded on demand, stored offline at " +
            "up to 4096 pixels on its longest side and used with your HALYN clock."
        AlertDialog.Builder(host)
            .setTitle(item.name)
            .setMessage(details)
            .setNegativeButton("Cancel", null)
            .setNeutralButton("Source & license") { _, _ -> openLink(item.sourcePage) }
            .setPositiveButton("Download & use") { _, _ -> download(item) }
            .show()
    }

    private fun download(item: Commons4kCatalog.Image) {
        if (destroyed || pending) return
        pending = true
        status.text = "Downloading real 4K original…"
        more.visibility = View.GONE
        io.execute {
            val success = try {
                source.download(item, PhotoWallpaperStore(host.applicationContext))
            } catch (_: Exception) { false }
            onUi {
                pending = false
                if (success) {
                    status.text = "Saved 4K wallpaper · " + item.name
                    onInstalled(item)
                    Toast.makeText(host, "4K wallpaper ready. Tap Set as wallpaper.",
                        Toast.LENGTH_LONG).show()
                } else {
                    status.text = "Download failed. Try another image or retry."
                }
                more.visibility = if (nextOffset != null) View.VISIBLE else View.GONE
            }
        }
    }

    private fun openLink(url: String) {
        try {
            host.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(url)))
        } catch (_: Exception) {
            Toast.makeText(host, "Could not open source page", Toast.LENGTH_SHORT).show()
        }
    }

    private fun onUi(action: () -> Unit) {
        if (destroyed) return
        host.runOnUiThread {
            if (!destroyed && !host.isDestroyed && !host.isFinishing) action()
        }
    }

    fun close() {
        destroyed = true
        requestId++
        io.shutdownNow()
        thumbs.shutdownNow()
    }
}
