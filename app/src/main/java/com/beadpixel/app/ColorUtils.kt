package com.beadpixel.app

import android.graphics.Color

object ColorUtils {
    fun hex(argb: Int): String {
        val r = (argb ushr 16) and 0xFF
        val g = (argb ushr 8) and 0xFF
        val b = argb and 0xFF
        return String.format("%02X%02X%02X", r, g, b)
    }

    fun parseHex(s: String): Int? {
        var t = s.trim().removePrefix("#")
        if (t.length == 6) t = "FF" + t
        if (t.length != 8) return null
        return try {
            java.lang.Long.parseLong(t, 16).toInt()
        } catch (e: Exception) {
            null
        }
    }

    fun luminance(argb: Int): Double {
        val r = (argb ushr 16) and 0xFF
        val g = (argb ushr 8) and 0xFF
        val b = argb and 0xFF
        return (0.299 * r + 0.587 * g + 0.114 * b) / 255.0
    }

    fun contrastText(argb: Int): Int = if (luminance(argb) > 0.55) Color.BLACK else Color.WHITE

    fun argbToUnsignedLong(argb: Int): Long = argb.toLong() and 0xFFFFFFFFL

    // ---- CIEDE2000 感知色差 ----
    fun rgbToLab(argb: Int): FloatArray {
        val r = ((argb ushr 16) and 0xFF) / 255.0
        val g = ((argb ushr 8) and 0xFF) / 255.0
        val b = (argb and 0xFF) / 255.0
        fun lin(c: Double): Double = if (c > 0.04045) Math.pow((c + 0.055) / 1.055, 2.4) else c / 12.92
        val rl = lin(r)
        val gl = lin(g)
        val bl = lin(b)
        val x = (0.4124564 * rl + 0.3575761 * gl + 0.1804375 * bl) / 0.95047
        val y = 0.2126729 * rl + 0.7151522 * gl + 0.0721750 * bl
        val z = (0.0193339 * rl + 0.1191920 * gl + 0.9503041 * bl) / 1.08883
        fun f(t: Double): Double = if (t > 0.008856) Math.cbrt(t) else (7.787 * t + 16.0 / 116.0)
        val fx = f(x)
        val fy = f(y)
        val fz = f(z)
        return floatArrayOf(
            (116.0 * fy - 16.0).toFloat(),
            (500.0 * (fx - fy)).toFloat(),
            (200.0 * (fy - fz)).toFloat()
        )
    }

    fun deltaE2000(lab1: FloatArray, lab2: FloatArray): Double {
        val l1 = lab1[0].toDouble()
        val a1 = lab1[1].toDouble()
        val b1 = lab1[2].toDouble()
        val l2 = lab2[0].toDouble()
        val a2 = lab2[1].toDouble()
        val b2 = lab2[2].toDouble()
        val c1 = Math.sqrt(a1 * a1 + b1 * b1)
        val c2 = Math.sqrt(a2 * a2 + b2 * b2)
        val cb = (c1 + c2) / 2.0
        val g = 0.5 * (1.0 - Math.sqrt(Math.pow(cb, 7.0) / (Math.pow(cb, 7.0) + Math.pow(25.0, 7.0))))
        val a1p = (1.0 + g) * a1
        val a2p = (1.0 + g) * a2
        val c1p = Math.sqrt(a1p * a1p + b1 * b1)
        val c2p = Math.sqrt(a2p * a2p + b2 * b2)
        fun hue(aa: Double, bb: Double): Double {
            if (aa == 0.0 && bb == 0.0) return 0.0
            var h = Math.toDegrees(Math.atan2(bb, aa))
            if (h < 0) h += 360.0
            return h
        }
        val h1p = hue(a1p, b1)
        val h2p = hue(a2p, b2)
        val dlp = l2 - l1
        val dcp = c2p - c1p
        var dhp = 0.0
        if (c1p * c2p != 0.0) {
            dhp = h2p - h1p
            if (dhp > 180.0) dhp -= 360.0
            else if (dhp < -180.0) dhp += 360.0
        }
        val dhpRad = Math.toRadians(dhp) / 2.0
        val dhp2 = 2.0 * Math.sqrt(c1p * c2p) * Math.sin(dhpRad)
        val lbp = (l1 + l2) / 2.0
        val cbp = (c1p + c2p) / 2.0
        var hbp = if (c1p * c2p == 0.0) h1p + h2p else {
            var v = (h1p + h2p) / 2.0
            if (Math.abs(h1p - h2p) > 180.0) {
                if (h1p + h2p < 360.0) v += 180.0 else v -= 180.0
            }
            v
        }
        val t = 1.0 - 0.17 * Math.cos(Math.toRadians(hbp - 30.0)) +
            0.24 * Math.cos(Math.toRadians(2.0 * hbp)) +
            0.32 * Math.cos(Math.toRadians(3.0 * hbp + 6.0)) -
            0.20 * Math.cos(Math.toRadians(4.0 * hbp - 63.0))
        val dTheta = 30.0 * Math.exp(-Math.pow((hbp - 275.0) / 25.0, 2.0))
        val rc = 2.0 * Math.sqrt(Math.pow(cbp, 7.0) / (Math.pow(cbp, 7.0) + Math.pow(25.0, 7.0)))
        val sl = 1.0 + (0.015 * Math.pow(lbp - 50.0, 2.0)) / Math.sqrt(20.0 + Math.pow(lbp - 50.0, 2.0))
        val sc = 1.0 + 0.045 * cbp
        val sh = 1.0 + 0.015 * cbp * t
        val rt = -Math.sin(Math.toRadians(2.0 * dTheta)) * rc
        val lt = dlp / sl
        val ct = dcp / sc
        val ht = dhp2 / sh
        return Math.sqrt(lt * lt + ct * ct + ht * ht + rt * ct * ht)
    }
}
