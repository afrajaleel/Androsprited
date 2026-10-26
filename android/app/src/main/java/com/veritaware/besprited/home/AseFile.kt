// Copyright (C) 2026 Veritaware
//
// This program is free software; you can redistribute it and/or modify
// it under the terms of the GNU General Public License version 2 as
// published by the Free Software Foundation.

package com.veritaware.besprited.home

import android.graphics.Bitmap
import java.io.ByteArrayOutputStream
import java.io.File
import java.io.InputStream
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.util.zip.Deflater
import java.util.zip.Inflater

enum class ColorMode(val depth: Int) { RGB(32), GRAYSCALE(16), INDEXED(8) }

enum class Background { TRANSPARENT, WHITE, BLACK }

/** Basic facts about a sprite file, read from its header. */
data class SpriteInfo(val width: Int, val height: Int, val frames: Int, val colorMode: ColorMode?)

/**
 * Minimal .ase/.aseprite support: writes a new one-frame sprite and renders
 * the first frame of an existing one for thumbnails.
 */
object AseFile {
    private const val FILE_MAGIC = 0xA5E0
    private const val FRAME_MAGIC = 0xF1FA
    private const val CHUNK_OLD_PALETTE = 0x0004
    private const val CHUNK_LAYER = 0x2004
    private const val CHUNK_CEL = 0x2005
    private const val CHUNK_PALETTE = 0x2019

    private const val LAYER_VISIBLE = 1
    private const val LAYER_EDITABLE = 2
    private const val LAYER_BACKGROUND = 8

    // ------------------------------------------------------------------
    // Writing

    fun writeNew(
        file: File,
        width: Int,
        height: Int,
        mode: ColorMode,
        background: Background,
        palette: IntArray,
    ) {
        val pal = if (palette.isEmpty()) intArrayOf(0xFF000000.toInt(), 0xFFFFFFFF.toInt()) else palette.copyOf(minOf(256, palette.size))
        val bpp = mode.depth / 8
        val pixels = ByteArray(width * height * bpp)
        val bgColor = when (background) {
            Background.TRANSPARENT -> null
            Background.WHITE -> 0xFFFFFFFF.toInt()
            Background.BLACK -> 0xFF000000.toInt()
        }
        if (bgColor != null) {
            val px: ByteArray = when (mode) {
                ColorMode.RGB -> byteArrayOf(bgColor.r.toByte(), bgColor.g.toByte(), bgColor.b.toByte(), 0xFF.toByte())
                ColorMode.GRAYSCALE -> byteArrayOf(bgColor.r.toByte(), 0xFF.toByte())
                ColorMode.INDEXED -> byteArrayOf(nearestIndex(pal, bgColor).toByte())
            }
            for (i in pixels.indices) pixels[i] = px[i % bpp]
        }

        val chunks = mutableListOf<ByteArray>()
        chunks += oldPaletteChunk(pal)
        val layerFlags = LAYER_VISIBLE or LAYER_EDITABLE or (if (bgColor != null) LAYER_BACKGROUND else 0)
        chunks += layerChunk(layerFlags, if (bgColor != null) "Background" else "Layer 1")
        chunks += celChunk(width, height, pixels)

        val frameBody = ByteArrayOutputStream().apply { chunks.forEach { write(it) } }.toByteArray()
        val frame = le(16 + frameBody.size).apply {
            putInt(16 + frameBody.size)
            putShort(FRAME_MAGIC.toShort())
            putShort(chunks.size.toShort())
            putShort(100) // ms
            putShort(0)
            putInt(chunks.size)
            put(frameBody)
        }.array()

        val total = 128 + frame.size
        val header = le(128).apply {
            putInt(total)
            putShort(FILE_MAGIC.toShort())
            putShort(1) // frames
            putShort(width.toShort())
            putShort(height.toShort())
            putShort(mode.depth.toShort())
            putInt(1) // layer opacity is valid
            putShort(100)
            putInt(0)
            putInt(0)
            put(0) // transparent index
            put(ByteArray(3))
            putShort((if (pal.size >= 256) 0 else pal.size).toShort())
            put(1) // pixel ratio
            put(1)
            putShort(0) // grid
            putShort(0)
            putShort(16)
            putShort(16)
        }.array()

        file.parentFile?.mkdirs()
        file.outputStream().use {
            it.write(header)
            it.write(frame)
        }
    }

    private fun oldPaletteChunk(pal: IntArray): ByteArray {
        val body = le(4 + pal.size * 3).apply {
            putShort(1) // packets
            put(0) // skip
            put((if (pal.size >= 256) 0 else pal.size).toByte()) // 0 = 256
            for (c in pal) {
                put(c.r.toByte()); put(c.g.toByte()); put(c.b.toByte())
            }
        }.array()
        return chunk(CHUNK_OLD_PALETTE, body)
    }

    private fun layerChunk(flags: Int, name: String): ByteArray {
        val nameBytes = name.toByteArray(Charsets.UTF_8)
        val body = le(18 + nameBytes.size).apply {
            putShort(flags.toShort())
            putShort(0) // image layer
            putShort(0) // child level
            putShort(0)
            putShort(0)
            putShort(0) // normal blend
            put(0xFF.toByte()) // opacity
            put(ByteArray(3))
            putShort(nameBytes.size.toShort())
            put(nameBytes)
        }.array()
        return chunk(CHUNK_LAYER, body)
    }

    private fun celChunk(width: Int, height: Int, pixels: ByteArray): ByteArray {
        val deflater = Deflater(Deflater.BEST_COMPRESSION)
        deflater.setInput(pixels)
        deflater.finish()
        val zipped = ByteArrayOutputStream()
        val buf = ByteArray(16 * 1024)
        while (!deflater.finished()) zipped.write(buf, 0, deflater.deflate(buf))
        deflater.end()
        val data = zipped.toByteArray()

        val body = le(20 + data.size).apply {
            putShort(0) // layer
            putShort(0) // x
            putShort(0) // y
            put(0xFF.toByte())
            putShort(2) // compressed image
            put(ByteArray(7))
            putShort(width.toShort())
            putShort(height.toShort())
            put(data)
        }.array()
        return chunk(CHUNK_CEL, body)
    }

    private fun chunk(type: Int, body: ByteArray): ByteArray =
        le(6 + body.size).apply {
            putInt(6 + body.size)
            putShort(type.toShort())
            put(body)
        }.array()

    private fun le(size: Int): ByteBuffer = ByteBuffer.allocate(size).order(ByteOrder.LITTLE_ENDIAN)

    private fun nearestIndex(pal: IntArray, color: Int): Int {
        var best = 0
        var bestDist = Int.MAX_VALUE
        pal.forEachIndexed { i, c ->
            val dr = c.r - color.r
            val dg = c.g - color.g
            val db = c.b - color.b
            val d = dr * dr + dg * dg + db * db
            if (d < bestDist) {
                bestDist = d
                best = i
            }
        }
        return best
    }

    // ------------------------------------------------------------------
    // Reading

    fun readInfo(file: File): SpriteInfo? = runCatching {
        file.inputStream().use { input ->
            val h = ByteBuffer.wrap(input.readExactly(128)).order(ByteOrder.LITTLE_ENDIAN)
            h.int
            if (h.short.toInt() and 0xFFFF != FILE_MAGIC) return null
            val frames = h.short.u16()
            val w = h.short.u16()
            val hh = h.short.u16()
            val depth = h.short.u16()
            val mode = ColorMode.entries.firstOrNull { it.depth == depth }
            SpriteInfo(w, hh, frames, mode)
        }
    }.getOrNull()

    private class Layer(val visible: Boolean, val isImage: Boolean, val background: Boolean, val opacity: Int)

    /** Renders the first frame (visible layers composited) into a bitmap. */
    fun renderFirstFrame(file: File, maxPixels: Int = 1024 * 1024): Bitmap? = runCatching {
        file.inputStream().buffered().use { input ->
            val h = ByteBuffer.wrap(input.readExactly(128)).order(ByteOrder.LITTLE_ENDIAN)
            h.int
            if (h.short.toInt() and 0xFFFF != FILE_MAGIC) return null
            h.short
            val w = h.short.u16()
            val hh = h.short.u16()
            val depth = h.short.u16()
            val flags = h.int
            h.short; h.int; h.int
            val transparentIndex = h.get().toInt() and 0xFF
            if (w <= 0 || hh <= 0 || w.toLong() * hh > maxPixels) return null

            val fh = ByteBuffer.wrap(input.readExactly(16)).order(ByteOrder.LITTLE_ENDIAN)
            val frameSize = fh.int
            if (fh.short.toInt() and 0xFFFF != FRAME_MAGIC) return null
            val oldChunks = fh.short.u16()
            fh.short; fh.short
            val newChunks = fh.int
            val chunkCount = if (oldChunks == 0xFFFF || newChunks != 0) newChunks else oldChunks
            val frame = ByteBuffer.wrap(input.readExactly(frameSize - 16)).order(ByteOrder.LITTLE_ENDIAN)

            val palette = IntArray(256) { 0xFF000000.toInt() }
            val layers = mutableListOf<Layer>()
            // Hidden groups hide their children: track visibility per level.
            val levelVisible = BooleanArray(64) { true }
            val cels = mutableListOf<Pair<Int, ByteBuffer>>()

            repeat(chunkCount) {
                if (frame.remaining() < 6) return@repeat
                val start = frame.position()
                val size = frame.int
                val type = frame.short.u16()
                val body = frame.slice().order(ByteOrder.LITTLE_ENDIAN).limit(size - 6) as ByteBuffer
                when (type) {
                    CHUNK_PALETTE -> {
                        body.int
                        val from = body.int
                        val to = body.int
                        body.position(body.position() + 8)
                        for (i in from..to) {
                            val entryFlags = body.short.u16()
                            val r = body.get().u8(); val g = body.get().u8()
                            val b = body.get().u8(); val a = body.get().u8()
                            if (i in 0..255) palette[i] = argb(a, r, g, b)
                            if (entryFlags and 1 != 0) body.position(body.position() + body.short.u16())
                        }
                    }
                    CHUNK_OLD_PALETTE -> {
                        var index = 0
                        repeat(body.short.u16()) {
                            index += body.get().u8()
                            val n = body.get().u8().let { if (it == 0) 256 else it }
                            repeat(n) {
                                val r = body.get().u8(); val g = body.get().u8(); val b = body.get().u8()
                                if (index in 0..255) palette[index] = argb(255, r, g, b)
                                index++
                            }
                        }
                    }
                    CHUNK_LAYER -> {
                        val lf = body.short.u16()
                        val lt = body.short.u16()
                        val level = body.short.u16().coerceIn(0, 62)
                        body.short; body.short; body.short
                        val opacity = if (flags and 1 != 0) body.get().u8() else 255
                        val visible = (lf and LAYER_VISIBLE) != 0 && levelVisible[level]
                        levelVisible[level + 1] = visible
                        layers += Layer(visible, lt == 0, (lf and LAYER_BACKGROUND) != 0, opacity)
                    }
                    CHUNK_CEL -> cels += body.getShort(0).toInt().and(0xFFFF) to body
                }
                frame.position(start + size)
            }

            val out = IntArray(w * hh)
            // Cels are stored in layer order; draw them bottom to top.
            for ((layerIndex, body) in cels.sortedBy { it.first }) {
                val layer = layers.getOrNull(layerIndex) ?: continue
                if (!layer.visible || !layer.isImage) continue
                body.position(2)
                val x = body.short.toInt()
                val y = body.short.toInt()
                val celOpacity = body.get().u8()
                val celType = body.short.u16()
                body.position(body.position() + 7)
                if (celType != 0 && celType != 2) continue // linked/tilemap: skip
                val cw = body.short.u16()
                val ch = body.short.u16()
                val bpp = depth / 8
                val raw = ByteArray(cw * ch * bpp)
                if (celType == 0) {
                    body.get(raw, 0, minOf(raw.size, body.remaining()))
                } else {
                    val zipped = ByteArray(body.remaining()).also { body.get(it) }
                    val inflater = Inflater()
                    inflater.setInput(zipped)
                    var off = 0
                    while (off < raw.size && !inflater.finished()) {
                        val n = inflater.inflate(raw, off, raw.size - off)
                        if (n == 0 && (inflater.needsInput() || inflater.needsDictionary())) break
                        off += n
                    }
                    inflater.end()
                }
                val opacity = layer.opacity * celOpacity / 255
                for (py in 0 until ch) {
                    val dy = y + py
                    if (dy !in 0 until hh) continue
                    for (px in 0 until cw) {
                        val dx = x + px
                        if (dx !in 0 until w) continue
                        val i = (py * cw + px) * bpp
                        val src = when (depth) {
                            32 -> argb(raw[i + 3].u8(), raw[i].u8(), raw[i + 1].u8(), raw[i + 2].u8())
                            16 -> raw[i].u8().let { v -> argb(raw[i + 1].u8(), v, v, v) }
                            else -> raw[i].u8().let { idx ->
                                if (idx == transparentIndex && !layer.background) 0 else palette[idx]
                            }
                        }
                        val o = dy * w + dx
                        out[o] = blend(out[o], src, opacity)
                    }
                }
            }
            Bitmap.createBitmap(out, w, hh, Bitmap.Config.ARGB_8888)
        }
    }.getOrNull()

    private fun blend(dst: Int, src: Int, opacity: Int): Int {
        val sa = (src ushr 24) * opacity / 255
        if (sa == 0) return dst
        val da = dst ushr 24
        val outA = sa + da * (255 - sa) / 255
        if (outA == 0) return 0
        fun ch(s: Int, d: Int) = (s * sa + d * da * (255 - sa) / 255) / outA
        return argb(outA, ch(src.r, dst.r), ch(src.g, dst.g), ch(src.b, dst.b))
    }

    /** Parses a GIMP .gpl palette. */
    fun readGpl(input: InputStream): IntArray =
        input.bufferedReader().readLines()
            .asSequence()
            .map { it.trim() }
            .filter { it.isNotEmpty() && it[0].isDigit() }
            .mapNotNull { line ->
                val parts = line.split(Regex("\\s+"))
                if (parts.size < 3) return@mapNotNull null
                val (r, g, b) = parts.take(3).map { it.toIntOrNull() ?: return@mapNotNull null }
                argb(255, r, g, b)
            }
            .take(256)
            .toList()
            .toIntArray()
}

private fun InputStream.readExactly(n: Int): ByteArray {
    val buf = ByteArray(n)
    var off = 0
    while (off < n) {
        val r = read(buf, off, n - off)
        if (r < 0) throw java.io.EOFException()
        off += r
    }
    return buf
}

private fun Short.u16() = toInt() and 0xFFFF
private fun Byte.u8() = toInt() and 0xFF
private val Int.r get() = (this shr 16) and 0xFF
private val Int.g get() = (this shr 8) and 0xFF
private val Int.b get() = this and 0xFF
private fun argb(a: Int, r: Int, g: Int, b: Int) =
    (a.coerceIn(0, 255) shl 24) or (r.coerceIn(0, 255) shl 16) or (g.coerceIn(0, 255) shl 8) or b.coerceIn(0, 255)
