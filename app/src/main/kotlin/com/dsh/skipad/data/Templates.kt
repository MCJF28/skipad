package com.dsh.skipad.data

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import java.io.File

/**
 * 学习模式下截取的「跳过」按钮小图，用于运行时模板匹配。
 * 存于应用私有目录，不上传。
 */
object Templates {

    private fun dir(ctx: Context): File =
        File(ctx.filesDir, "templates").apply { if (!exists()) mkdirs() }

    fun save(ctx: Context, pkg: String, bmp: Bitmap): Boolean {
        return try {
            val f = File(dir(ctx), "$pkg.png")
            f.outputStream().use { bmp.compress(Bitmap.CompressFormat.PNG, 100, it) }
            true
        } catch (t: Throwable) {
            false
        }
    }

    fun load(ctx: Context, pkg: String): Bitmap? {
        return try {
            val f = File(dir(ctx), "$pkg.png")
            if (!f.exists()) null else BitmapFactory.decodeFile(f.absolutePath)
        } catch (t: Throwable) {
            null
        }
    }

    fun has(ctx: Context, pkg: String): Boolean = File(dir(ctx), "$pkg.png").exists()

    fun remove(ctx: Context, pkg: String) {
        try {
            File(dir(ctx), "$pkg.png").delete()
        } catch (t: Throwable) {
            // 忽略
        }
    }
}
