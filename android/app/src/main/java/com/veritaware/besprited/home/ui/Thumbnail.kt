// Copyright (C) 2026 Veritaware
//
// This program is free software; you can redistribute it and/or modify
// it under the terms of the GNU General Public License version 2 as
// published by the Free Software Foundation.

package com.veritaware.besprited.home.ui

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.util.LruCache
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.produceState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.FilterQuality
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import com.veritaware.besprited.home.AseFile
import com.veritaware.besprited.home.Sprite
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import kotlin.math.max
import kotlin.math.min

private object ThumbnailCache {
    // Sized in kilobytes.
    private val cache = object : LruCache<String, ImageBitmap>(24 * 1024) {
        override fun sizeOf(key: String, value: ImageBitmap) = max(1, value.width * value.height * 4 / 1024)
    }

    fun key(sprite: Sprite) = "${sprite.file.absolutePath}@${sprite.modified}"

    operator fun get(key: String): ImageBitmap? = cache.get(key)

    fun load(sprite: Sprite): ImageBitmap? {
        val key = key(sprite)
        cache.get(key)?.let { return it }
        val bitmap = if (sprite.isAseprite) AseFile.renderFirstFrame(sprite.file) else decodeSampled(sprite.file, 512)
        return bitmap?.asImageBitmap()?.also { cache.put(key, it) }
    }

    private fun decodeSampled(file: File, maxSide: Int): Bitmap? {
        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        BitmapFactory.decodeFile(file.absolutePath, bounds)
        if (bounds.outWidth <= 0) return null
        var sample = 1
        while (max(bounds.outWidth, bounds.outHeight) / (sample * 2) >= maxSide) sample *= 2
        return BitmapFactory.decodeFile(file.absolutePath, BitmapFactory.Options().apply { inSampleSize = sample })
    }
}

/** A sprite's first frame on a transparency checkerboard, scaled without blurring pixels. */
@Composable
fun SpriteThumbnail(sprite: Sprite, modifier: Modifier = Modifier) {
    val key = ThumbnailCache.key(sprite)
    val image by produceState(ThumbnailCache[key], key) {
        if (value == null) value = withContext(Dispatchers.IO) { runCatching { ThumbnailCache.load(sprite) }.getOrNull() }
    }
    val light = MaterialTheme.colorScheme.surfaceContainerHighest
    val dark = MaterialTheme.colorScheme.surfaceContainerHigh

    Box(modifier, contentAlignment = Alignment.Center) {
        val img = image
        if (img == null) {
            Canvas(Modifier.fillMaxSize()) { checkerboard(light, dark) }
            Icon(
                if (sprite.isAnimated) AppIcons.Movie else AppIcons.Image,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.outline,
                modifier = Modifier.size(32.dp),
            )
        } else {
            Canvas(Modifier.fillMaxSize().padding(10.dp)) {
                // Integer zoom keeps pixel art crisp; fall back to fit for big images.
                val fit = min(size.width / img.width, size.height / img.height)
                val scale = if (fit >= 1f) kotlin.math.floor(fit) else fit
                val w = img.width * scale
                val h = img.height * scale
                val topLeft = Offset((size.width - w) / 2f, (size.height - h) / 2f)
                clipRectChecker(topLeft, Size(w, h), light, dark)
                drawImage(
                    img,
                    dstOffset = IntOffset(topLeft.x.toInt(), topLeft.y.toInt()),
                    dstSize = IntSize(w.toInt(), h.toInt()),
                    filterQuality = if (fit >= 1f) FilterQuality.None else FilterQuality.Low,
                )
            }
        }
    }
}

private fun DrawScope.checkerboard(light: Color, dark: Color, cell: Float = 8.dp.toPx()) =
    clipRectChecker(Offset.Zero, size, light, dark, cell)

private fun DrawScope.clipRectChecker(
    origin: Offset,
    area: Size,
    light: Color,
    dark: Color,
    cell: Float = 6.dp.toPx(),
) {
    drawRect(light, origin, area)
    val cols = kotlin.math.ceil(area.width / cell).toInt()
    val rows = kotlin.math.ceil(area.height / cell).toInt()
    for (y in 0 until rows) for (x in 0 until cols) {
        if ((x + y) % 2 == 0) continue
        val left = origin.x + x * cell
        val top = origin.y + y * cell
        drawRect(
            dark,
            Offset(left, top),
            Size(min(cell, origin.x + area.width - left), min(cell, origin.y + area.height - top)),
        )
    }
}
