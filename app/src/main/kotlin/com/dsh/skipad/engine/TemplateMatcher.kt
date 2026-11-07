package com.dsh.skipad.engine

import android.graphics.Bitmap
import android.graphics.Point

/**
 * 极简模板匹配：把截图和模板都缩到 1/8，灰度后做绝对差累加（带提前退出）。
 * 目的是"在屏幕上找那块跳过按钮"，不追求亚像素精度。
 */
object TemplateMatcher {

    private const val SCALE = 8

    fun find(screen: Bitmap, template: Bitmap, minScore: Double = 0.80): Point? {
        val sw = screen.width / SCALE
        val sh = screen.height / SCALE
        val tw = template.width / SCALE
        val th = template.height / SCALE
        if (sw < 16 || sh < 16 || tw < 4 || th < 4) return null
        if (tw >= sw || th >= sh) return null

        val s = gray(screen, sw, sh)
        val t = gray(template, tw, th)
        if (s == null || t == null) return null

        var best = Long.MAX_VALUE
        var bx = -1
        var by = -1
        val maxX = sw - tw
        val maxY = sh - th

        for (y in 0..maxY) {
            val rowBase = y * sw
            for (x in 0..maxX) {
                var sum = 0L
                var j = 0
                var abort = false
                while (j < th && !abort) {
                    val sb = rowBase + j * sw + x
                    val tb = j * tw
                    var i = 0
                    while (i < tw) {
                        val d = s[sb + i] - t[tb + i]
                        sum += if (d < 0) -d else d
                        i++
                    }
                    if (sum > best) abort = true
                    j++
                }
                if (!abort && sum < best) {
                    best = sum
                    bx = x
                    by = y
                }
            }
        }

        if (bx < 0) return null
        val maxSum = 255.0 * tw * th
        val score = 1.0 - best / maxSum
        if (score < minScore) return null
        return Point((bx + tw / 2) * SCALE, (by + th / 2) * SCALE)
    }

    private fun gray(bmp: Bitmap, w: Int, h: Int): IntArray? {
        if (bmp.width < w || bmp.height < h) return null
        val small = try {
            Bitmap.createScaledBitmap(bmp, w, h, false)
        } catch (t: Throwable) {
            return null
        }
        val px = IntArray(w * h)
        small.getPixels(px, 0, w, 0, 0, w, h)
        val out = IntArray(w * h)
        for (i in px.indices) {
            val p = px[i]
            val r = (p shr 16) and 0xFF
            val g = (p shr 8) and 0xFF
            val b = p and 0xFF
            out[i] = (r * 77 + g * 151 + b * 28) shr 8
        }
        return out
    }
}
