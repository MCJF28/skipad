package com.dsh.skipad.service

import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.RectF
import android.view.MotionEvent
import android.view.View

/**
 * 学习模式的全屏取点浮层：
 *  - 点哪里，就在哪里标一个带编号的圈
 *  - 底部有「完成」按钮，不用手动退出应用
 */
class CalibrationView(
    context: Context,
    private val onPick: (Float, Float) -> Unit,
    private val onFinish: () -> Unit
) : View(context) {

    private val dimPaint = Paint().apply { color = 0xB3000000.toInt() }
    private val markerPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.parseColor("#3DDC84")
        style = Paint.Style.STROKE
        strokeWidth = 6f
    }
    private val markerFill = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = 0x443DDC84
        style = Paint.Style.FILL
    }
    private val numPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.WHITE
        textSize = 46f
        textAlign = Paint.Align.CENTER
        isFakeBoldText = true
    }
    private val hintPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.WHITE
        textSize = 40f
        textAlign = Paint.Align.CENTER
    }
    private val hintSubPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = 0xCCFFFFFF.toInt()
        textSize = 32f
        textAlign = Paint.Align.CENTER
    }
    private val btnPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = 0xF2303030.toInt()
        style = Paint.Style.FILL
    }
    private val btnText = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.WHITE
        textSize = 40f
        textAlign = Paint.Align.CENTER
        isFakeBoldText = true
    }

    private val finishRect = RectF()
    private var markX = -1f
    private var markY = -1f

    override fun onSizeChanged(w: Int, h: Int, oldw: Int, oldh: Int) {
        super.onSizeChanged(w, h, oldw, oldh)
        val bw = (w * 0.46f).coerceAtLeast(360f)
        val bh = 120f
        val left = (w - bw) / 2f
        val top = h - bh - 140f
        finishRect.set(left, top, left + bw, top + bh)
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        canvas.drawRect(0f, 0f, width.toFloat(), height.toFloat(), dimPaint)

        val cx = width / 2f
        canvas.drawText("点一下「跳过」按钮", cx, height * 0.16f, hintPaint)
        canvas.drawText("可重复点击调整，点「完成」或退出应用结束", cx, height * 0.16f + 52f, hintSubPaint)

        if (markX >= 0) {
            canvas.drawCircle(markX, markY, 56f, markerFill)
            canvas.drawCircle(markX, markY, 56f, markerPaint)
            canvas.drawText("1", markX, markY + 16f, numPaint)
        }

        canvas.drawRoundRect(finishRect, 60f, 60f, btnPaint)
        canvas.drawText(
            "完成",
            finishRect.centerX(),
            finishRect.centerY() + 14f,
            btnText
        )
    }

    override fun onTouchEvent(event: MotionEvent): Boolean {
        if (event.action != MotionEvent.ACTION_UP) return true

        if (finishRect.contains(event.x, event.y)) {
            onFinish()
            return true
        }

        markX = event.x
        markY = event.y
        invalidate()
        onPick(event.rawX, event.rawY)
        return true
    }
}
