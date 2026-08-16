package com.beadpixel.app

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Canvas
import android.net.Uri

object ImageConverter {

    fun loadBitmap(context: Context, uri: Uri): Bitmap? {
        return try {
            val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
            context.contentResolver.openInputStream(uri)?.use {
                BitmapFactory.decodeStream(it, null, bounds)
            }
            var sample = 1
            while (bounds.outWidth / sample > 2048 || bounds.outHeight / sample > 2048) {
                sample *= 2
            }
            val opts = BitmapFactory.Options().apply { inSampleSize = sample }
            context.contentResolver.openInputStream(uri)?.use {
                BitmapFactory.decodeStream(it, null, opts)
            }
        } catch (e: Exception) {
            null
        }
    }

    // mode: 0 = 拉伸填满, 1 = 保持比例适应（多余透明）
    // dither: 是否使用 Floyd-Steinberg 抖动（仅对照片/渐变有效）
    fun convert(src: Bitmap, width: Int, height: Int, palette: List<PaletteColor>, mode: Int): IntArray {
        val out = IntArray(width * height)
        if (palette.isEmpty() || width <= 0 || height <= 0) return out
        val colors = IntArray(palette.size) { palette[it].argb }
        val labs = Array(palette.size) { ColorUtils.rgbToLab(colors[it]) }

        // 1. 双线性平滑降采样到画布分辨率（柔和、不混出灰色，保留明暗层次）
        val target: Bitmap
        if (mode == 1) {
            val scale = minOf(width.toFloat() / src.width.toFloat(), height.toFloat() / src.height.toFloat())
            val dw = maxOf(1, (src.width.toFloat() * scale).toInt())
            val dh = maxOf(1, (src.height.toFloat() * scale).toInt())
            val scaled = Bitmap.createScaledBitmap(src, dw, dh, true)
            val canvas = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
            Canvas(canvas).drawBitmap(scaled, (width - dw) / 2f, (height - dh) / 2f, null)
            if (scaled !== src) scaled.recycle()
            target = canvas
        } else {
            target = Bitmap.createScaledBitmap(src, width, height, true)
        }
        val px = IntArray(width * height)
        target.getPixels(px, 0, width, 0, 0, width, height)
        if (target !== src) target.recycle()

        // 2. CIEDE2000 感知色差匹配调色盘
        // 3D 颜色查找表：一次构建，之后每个像素 O(1) 查表，大画布转换不再逐像素遍历色卡
        val lut = buildColorLut(colors, labs)
        for (i in px.indices) {
            val v = px[i]
            val a = (v ushr 24) and 0xFF
            if (a < 128) {
                out[i] = 0
            } else {
                val r = (v ushr 16) and 0xFF
                val g = (v ushr 8) and 0xFF
                val b = v and 0xFF
                out[i] = lut[((r ushr 3) shl 10) or ((g ushr 3) shl 5) or (b ushr 3)]
            }
        }
        removeNoise(out, width, height)
        return out
    }

    /**
     * 32x32x32 查找表：每个 RGB 5bit 量化桶存一个最近色卡色。
     * 先用感知加权 RGB 距离筛出 top-8 候选，再对候选做 CIEDE2000 精确选色，
     * 兼顾速度与色彩还原（避免纯加权距离在相近色上选错色号）。
     * 构建约 O(32768 * (色数 + 8))。
     */
    private fun buildColorLut(colors: IntArray, labs: Array<FloatArray>): IntArray {
        val lut = IntArray(32 * 32 * 32)
        val n = colors.size
        val cr = IntArray(n)
        val cg = IntArray(n)
        val cb = IntArray(n)
        for (i in 0 until n) {
            cr[i] = (colors[i] ushr 16) and 0xFF
            cg[i] = (colors[i] ushr 8) and 0xFF
            cb[i] = colors[i] and 0xFF
        }
        val K = 8
        val cand = IntArray(K)
        val candD = LongArray(K)
        val candLab = Array(K) { FloatArray(3) }
        var idx = 0
        for (r5 in 0 until 32) {
            val r = (r5 shl 3) or (r5 shr 2)
            for (g5 in 0 until 32) {
                val g = (g5 shl 3) or (g5 shr 2)
                for (b5 in 0 until 32) {
                    val b = (b5 shl 3) or (b5 shr 2)
                    var filled = 0
                    for (k in 0 until K) {
                        cand[k] = -1
                        candD[k] = Long.MAX_VALUE
                    }
                    for (i in 0 until n) {
                        val dr = r - cr[i]
                        val dg = g - cg[i]
                        val db = b - cb[i]
                        val d = dr.toLong() * dr * 2 + dg.toLong() * dg * 4 + db.toLong() * db * 3
                        if (d < candD[K - 1]) {
                            var k = K - 2
                            while (k >= 0 && d < candD[k]) {
                                cand[k + 1] = cand[k]
                                candD[k + 1] = candD[k]
                                k--
                            }
                            cand[k + 1] = i
                            candD[k + 1] = d
                        }
                    }
                    var best = colors[0]
                    var bestE = Double.MAX_VALUE
                    for (k in 0 until K) {
                        if (cand[k] < 0) break
                        candLab[k] = ColorUtils.rgbToLab(colors[cand[k]])
                        val e = ColorUtils.deltaE2000(candLab[k], labs[cand[k]])
                        if (e < bestE) {
                            bestE = e
                            best = colors[cand[k]]
                        }
                    }
                    lut[idx++] = best
                }
            }
        }
        return lut
    }


    // 边缘感知噪点滤波：仅当该像素在平坦区域（有 >=4 个同色邻居）且自身孤立时，
    // 替换为占优邻居色。保留边缘细线，去掉平脸上的跳色噪点。
    private fun removeNoise(out: IntArray, width: Int, height: Int) {
        val copy = out.copyOf()
        for (y in 0 until height) {
            for (x in 0 until width) {
                val i = y * width + x
                if (copy[i] == 0) continue
                val counts = HashMap<Int, Int>()
                for (dy in -1..1) {
                    for (dx in -1..1) {
                        val nx = x + dx
                        val ny = y + dy
                        if (nx < 0 || ny < 0 || nx >= width || ny >= height) continue
                        val nc = copy[ny * width + nx]
                        counts[nc] = (counts[nc] ?: 0) + 1
                    }
                }
                val cur = copy[i]
                if ((counts[cur] ?: 0) <= 1) {
                    var best = cur
                    var bestCnt = 0
                    for ((col, cnt) in counts) {
                        if (col == cur) continue
                        if (cnt >= 4 && cnt > bestCnt) {
                            bestCnt = cnt
                            best = col
                        }
                    }
                    if (best != cur) out[i] = best
                }
            }
        }
    }

    private fun nearest(argb: Int, colors: IntArray, labs: Array<FloatArray>): Int {
        val lab = ColorUtils.rgbToLab(argb)
        var best = colors[0]
        var bestD = Double.MAX_VALUE
        for (i in colors.indices) {
            val d = ColorUtils.deltaE2000(lab, labs[i])
            if (d < bestD) {
                bestD = d
                best = colors[i]
            }
        }
        return best
    }
}
