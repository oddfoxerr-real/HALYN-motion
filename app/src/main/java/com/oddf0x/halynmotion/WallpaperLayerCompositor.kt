package com.oddf0x.halynmotion

import android.content.Context
import android.graphics.Canvas
import android.graphics.RectF
import kotlin.math.roundToInt

/**
 * One stable ordering pipeline for the live WallpaperService and the preview.
 *
 * Background (in HalynRenderer) -> independently ordered widget/image/subject
 * stack. Every item is painted in saved bottom-to-top order; a cropped subject
 * can therefore be behind selected widgets yet in front of other widgets.
 * Nothing is flattened or removed from the user's original files.
 */
class WallpaperLayerCompositor(context: Context) {
    private val app = context.applicationContext
    private val layers = WallpaperLayerStore(app)
    private val widgets = WallpaperWidgetStore(app)
    private val widgetPainter = WallpaperWidgetRenderer(app)
    private val subject = DepthLayerStore(app)
    private val photo = PhotoWallpaperStore(app)

    fun active(): Boolean = layers.active()

    fun draw(
        canvas: Canvas, state: MotionState,
        gyro: ParallaxPose, viewport: RectF? = null,
        overrideWidgets: List<WallpaperWidget>? = null,
        now: Long = System.currentTimeMillis()
    ) {
        val area = viewport ?: RectF(
            0f, 0f, canvas.width.toFloat(), canvas.height.toFloat()
        )
        val items = overrideWidgets ?: widgets.load()
        val byId = items.associateBy { "widget:" + it.id }
        val stack = layers.merged(items, subject.available())
        for (layer in stack) {
            if (!layer.visible) continue
            when (layer.kind) {
                "image" -> layers.drawImage(canvas, layer, area)
                "widget" -> {
                    val element = byId[layer.id] ?: continue
                    canvas.saveLayerAlpha(area,
                        (layer.opacity * 255f).roundToInt().coerceIn(0, 255))
                    widgetPainter.draw(canvas, state, area, listOf(element), now)
                    canvas.restore()
                }
                "subject" -> {
                    if (!subject.available()) continue
                    canvas.save()
                    canvas.clipRect(area)
                    val cx = area.centerX()
                    val cy = area.centerY()
                    canvas.translate(
                        (layer.x - .5f) * area.width(),
                        (layer.y - .5f) * area.height()
                    )
                    canvas.rotate(layer.rotation, cx, cy)
                    canvas.scale(layer.scale, layer.scale, cx, cy)
                    canvas.saveLayerAlpha(area,
                        (layer.opacity * 255f).roundToInt().coerceIn(0, 255))
                    subject.draw(canvas, state.copy(depthEnabled = true),
                        gyro, photo, area)
                    canvas.restore()
                    canvas.restore()
                }
            }
        }
    }
}
