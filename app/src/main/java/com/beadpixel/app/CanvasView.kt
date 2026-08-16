package com.beadpixel.app

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Paint
import android.util.AttributeSet
import android.view.GestureDetector
import android.view.MotionEvent
import android.view.ScaleGestureDetector
import android.view.View
import android.view.ViewConfiguration
import kotlin.math.abs
import kotlin.math.ceil
import kotlin.math.floor
import kotlin.math.max
import kotlin.math.min

class CanvasView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = 0
) : View(context, attrs, defStyleAttr) {

    enum class Tool { BRUSH, ERASER, FILL, EYEDROPPER, PAN }

    var widthCells = 32
    var heightCells = 32
    var pixels: IntArray = IntArray(0)
        set(value) {
            field = value
            cacheDirtyAll = true
        }
    var palette: List<PaletteColor> = emptyList()
        set(value) {
            field = value
            rebuildPaletteMap()
        }
    var tool: Tool = Tool.BRUSH
    var currentColor: Int = 0xFF000000.toInt()

    var onPixelEdited: ((Int, Int) -> Unit)? = null
    var onStrokeEnd: (() -> Unit)? = null
    var onStrokeStart: (() -> Unit)? = null
    var onPickedColor: ((Int) -> Unit)? = null
    var onUndoStateChanged: ((Boolean, Boolean) -> Unit)? = null
    var onTransformChanged: (() -> Unit)? = null

    var showGrid = true
    var checkerBackground = true
    var codeDisplayMode = 0
    var undoLimit = 60
    var majorGridThreshold = 8f
    var minorGridThreshold = 20f
    var doubleTapToFit = false

    private val checkerPaint = Paint()
    private val cellPaint = Paint()
    private val gridPaint = Paint()
    private val textPaint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val labelPaint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val labelBgPaint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val borderPaint = Paint()
    private val filterPaint = Paint().apply { isFilterBitmap = true }
    private val nearestPaint = Paint()

    private var sizedOnce = false
    private var multiTouch = false

    private var scale = 1f
    private var offX = 0f
    private var offY = 0f
    private var minScale = 0.05f
    private var maxScale = 160f
    private var fitScale = 1f

    // 离屏渲染缓存：大画布避免每帧绘制全部格子
    private var cacheBitmap: Bitmap? = null
    private var cacheScale = 0f
    private var cacheDirtyAll = true
    private var cacheChecker = false
    private val dirtyCells = HashSet<Int>()
    private val cacheLock = Any()
    private var rebuildThread: Thread? = null
    private var rebuildRequested = false
    private var rebuildTargetScale = 0f
    private var rebuildTargetChecker = false

    private val undoStack = mutableListOf<IntArray>()
    private val redoStack = mutableListOf<IntArray>()

    private var lastCellX = -1
    private var lastCellY = -1
    private var drawingStroke = false
    private var pendingTap = false
    private val slop = ViewConfiguration.get(context).scaledTouchSlop

    private var lastTouchX = 0f
    private var lastTouchY = 0f
    private var lastFocusX = 0f
    private var lastFocusY = 0f
    private var downX = 0f
    private var downY = 0f

    private val paletteMap = HashMap<Int, String>()

    private val scaleDetector = ScaleGestureDetector(context, object : ScaleGestureDetector.SimpleOnScaleGestureListener() {
        override fun onScale(detector: ScaleGestureDetector): Boolean {
            val sf = detector.scaleFactor
            if (sf.isNaN() || sf <= 0.05f || sf >= 20f) return true
            val f = detector.focusX
            val fy = detector.focusY
            val ns = (scale * sf).coerceIn(minScale, maxScale)
            if (abs(ns - scale) < 0.0001f) return true
            offX = f - (f - offX) * (ns / scale)
            offY = fy - (fy - offY) * (ns / scale)
            scale = ns
            invalidate()
            return true
        }
    })

    private val gestureDetector = GestureDetector(context, object : GestureDetector.SimpleOnGestureListener() {
        override fun onDown(e: MotionEvent): Boolean = true
        override fun onDoubleTap(e: MotionEvent): Boolean {
            if (doubleTapToFit) fitToView()
            return true
        }
        override fun onLongPress(e: MotionEvent) {
            if (drawingStroke || pendingTap || tool == Tool.PAN) return
            val cell = cellAt(e.x, e.y) ?: return
            val c = pixels[cell.second * widthCells + cell.first]
            if (c != 0) onPickedColor?.invoke(c)
        }
    })

    init {
        isClickable = true
        isFocusable = true
        checkerPaint.style = Paint.Style.FILL
        cellPaint.style = Paint.Style.FILL
        gridPaint.style = Paint.Style.STROKE
        gridPaint.strokeWidth = 1f
        gridPaint.color = 0x66000000.toInt()
        borderPaint.style = Paint.Style.STROKE
        borderPaint.strokeWidth = dp(1.5f)
        borderPaint.color = 0xFF444444.toInt()
        textPaint.textAlign = Paint.Align.CENTER
        textPaint.isFakeBoldText = true
        labelPaint.isFakeBoldText = true
    }

    fun setCanvas(width: Int, height: Int, px: IntArray, pal: List<PaletteColor>) {
        widthCells = width
        heightCells = height
        pixels = px
        palette = pal
        rebuildPaletteMap()
        fitToView()
    }

    private fun rebuildPaletteMap() {
        paletteMap.clear()
        for (c in palette) {
            if (!paletteMap.containsKey(c.argb)) paletteMap[c.argb] = c.codeText()
        }
    }

    fun cellSizeDp(): Float = scale / resources.displayMetrics.density









    fun fitToView() {
        val w = width.toFloat()
        val h = height.toFloat()
        if (w <= 0 || h <= 0 || widthCells <= 0 || heightCells <= 0) return
        val pad = dp(20f)
        val fit = min((w - pad * 2) / widthCells, (h - pad * 2) / heightCells)
        fitScale = fit
        scale = max(fit, 0.5f)
        minScale = fit * 0.05f
        maxScale = max(160f, fit * 80f)
        offX = (w - widthCells * scale) / 2f
        offY = (h - heightCells * scale) / 2f
        invalidate()
    }

    fun undo() {
        if (undoStack.isEmpty()) return
        redoStack.add(pixels.copyOf())
        if (redoStack.size > undoLimit) redoStack.removeAt(0)
        pixels = undoStack.removeAt(undoStack.size - 1)
        notifyUndoState()
        invalidate()
    }

    fun redo() {
        if (redoStack.isEmpty()) return
        undoStack.add(pixels.copyOf())
        if (undoStack.size > undoLimit) undoStack.removeAt(0)
        pixels = redoStack.removeAt(redoStack.size - 1)
        notifyUndoState()
        invalidate()
    }

    private fun notifyUndoState() {
        onUndoStateChanged?.invoke(undoStack.isNotEmpty(), redoStack.isNotEmpty())
    }

    override fun onSizeChanged(w: Int, h: Int, oldw: Int, oldh: Int) {
        super.onSizeChanged(w, h, oldw, oldh)
        // 只在首次布局时适配视图；面板收起/展开导致的尺寸变化不重置用户缩放
        if (!sizedOnce) {
            sizedOnce = true
            fitToView()
        }
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        if (widthCells <= 0 || heightCells <= 0 || pixels.size < widthCells * heightCells) return
        if (scale <= 0) return
        val x0 = floor((0f - offX) / scale).toInt().coerceAtLeast(0)
        val y0 = floor((0f - offY) / scale).toInt().coerceAtLeast(0)
        val x1 = ceil((width.toFloat() - offX) / scale).toInt().coerceAtMost(widthCells)
        val y1 = ceil((height.toFloat() - offY) / scale).toInt().coerceAtMost(heightCells)
        if (x1 <= x0 || y1 <= y0) return
        val zoomed = scale > fitScale * 1.02f
        ensureCache()
        var dcBitmap: Bitmap? = null
        var dcScale = 1f
        synchronized(cacheLock) {
            dcBitmap = cacheBitmap
            dcScale = cacheScale
        }
        val cache = dcBitmap
        val sx = if (dcScale > 0f) scale / dcScale else 1f
        val visCount = (x1 - x0).toLong() * (y1 - y0).toLong()
        // 放大且可见格子可控时直接按全分辨率绘制色块，与网格线严格对齐；
        // 其余情况用离屏缓存（缩小时双线性滤波避免摩尔纹）
        val directDraw = zoomed && visCount <= 8000L && scale >= dp(6f)
        if (directDraw) {
            drawDirectCells(canvas, x0, x1, y0, y1)
        } else if (cache != null) {
            val bp = if (sx < 0.98f) filterPaint else nearestPaint
            canvas.save()
            canvas.translate(offX, offY)
            canvas.scale(sx, sx)
            canvas.drawBitmap(cache, 0f, 0f, bp)
            canvas.restore()
        } else {
            canvas.drawColor(emptyCellColor())
        }
        val codeShown = when (codeDisplayMode) {
            1 -> true
            2 -> false
            else -> scale >= dp(12f)
        }
        val minCodeScale = if (codeDisplayMode == 1) dp(6f) else dp(12f)
        val drawCodes = codeShown && scale >= minCodeScale && visCount <= 2000L
        if (showGrid && zoomed) {
            val night = isNight()
            val minorColor = if (night) 0x44FFFFFF.toInt() else 0x44000000.toInt()
            val majorColor = if (night) 0x99FFFFFF.toInt() else 0x99000000.toInt()
            val labelColor = if (night) 0xCCFFFFFF.toInt() else 0xCC000000.toInt()
            val canvasLeft = offX
            val canvasTop = offY
            val canvasRight = offX + widthCells * scale
            val canvasBottom = offY + heightCells * scale
            val vy0 = max(0f, offY)
            val vy1 = min(height.toFloat(), canvasBottom)
            val vx0 = max(0f, offX)
            val vx1 = min(width.toFloat(), canvasRight)
            if (scale >= dp(majorGridThreshold)) {
                // 每 5 格一条加粗大格线
                gridPaint.strokeWidth = dp(1f)
                gridPaint.color = majorColor
                for (i in x0..x1) {
                    if (i % 5 != 0) continue
                    val lx = offX + i * scale
                    canvas.drawLine(lx, vy0, lx, vy1, gridPaint)
                }
                for (j in y0..y1) {
                    if (j % 5 != 0) continue
                    val ly = offY + j * scale
                    canvas.drawLine(vx0, ly, vx1, ly, gridPaint)
                }
            }
            if (scale >= dp(minorGridThreshold)) {
                // 普通小格线（放大后显示）
                gridPaint.strokeWidth = if (scale >= dp(14f)) dp(1f) else 1f
                gridPaint.color = minorColor
                for (i in x0..x1) {
                    val lx = offX + i * scale
                    canvas.drawLine(lx, vy0, lx, vy1, gridPaint)
                }
                for (j in y0..y1) {
                    val ly = offY + j * scale
                    canvas.drawLine(vx0, ly, vx1, ly, gridPaint)
                }
            }
        }
        if (drawCodes) {
            for (cy in y0 until y1) {
                val rowBase = cy * widthCells
                val top = offY + cy * scale
                for (cx in x0 until x1) {
                    val c = pixels[rowBase + cx]
                    if (c == 0) continue
                    val t = paletteMap[c] ?: ("#" + ColorUtils.hex(c))
                    drawCellText(canvas, t, offX + cx * scale, top, scale, c)
                }
            }
        }

        // 格数标注：仿 p10（左深灰竖条 + 底白条，直角；放大细分每格、贴屏幕边缘；缩小贴画布边缘）
        if (showGrid && scale >= dp(8f)) {
            val night = isNight()
            val colBarColor = if (night) 0xF0333333.toInt() else 0xFFFFFFFF.toInt()
            val colTextColor = if (night) 0xFFFFFFFF.toInt() else 0xFF000000.toInt()
            labelPaint.textSize = dp(7f)
            labelPaint.textAlign = Paint.Align.CENTER
            val canvasOut = offX < -1f || offY < -1f ||
                (offX + widthCells * scale) > width.toFloat() + 1f ||
                (offY + heightCells * scale) > height.toFloat() + 1f
            if (canvasOut) {
                // 放大：屏幕边缘，细分到每格
                val step = if (scale >= dp(14f)) 1 else ceil(48f / scale).toInt().coerceAtLeast(1)
                val rowBarW = dp(22f)
                val colBarH = dp(24f)
                // 左侧行号竖条（白色，与底部一致；直角；收窄）
                labelBgPaint.color = colBarColor
                canvas.drawRect(0f, 0f, rowBarW, height.toFloat(), labelBgPaint)
                // 数字间分隔线（格子行边界）
                labelBgPaint.color = if (night) 0x44FFFFFF.toInt() else 0x44000000.toInt()
                var lineJ = y0
                while (lineJ <= y1) {
                    val lineY = offY + lineJ * scale
                    if (lineY >= 0f && lineY <= height.toFloat()) {
                        canvas.drawRect(dp(2f), lineY, rowBarW - dp(2f), lineY + dp(1f), labelBgPaint)
                    }
                    lineJ += step
                }
                labelPaint.color = colTextColor
                var lj = ((y0 + step - 1) / step) * step
                while (lj <= y1) {
                    if (lj >= 0 && lj < heightCells) {
                        val ly = offY + lj * scale + scale / 2f
                        if (ly >= 0f && ly <= height.toFloat() - colBarH) {
                            canvas.drawText(lj.toString(), rowBarW / 2f, ly + labelPaint.textSize / 3f, labelPaint)
                        }
                    }
                    lj += step
                }
                // 底部列号横条（直角，从行号条右侧开始）
                val barY = height.toFloat() - colBarH
                labelBgPaint.color = colBarColor
                canvas.drawRect(rowBarW, barY, width.toFloat(), height.toFloat(), labelBgPaint)
                labelPaint.color = colTextColor
                var li = ((x0 + step - 1) / step) * step
                while (li <= x1) {
                    if (li >= 0 && li < widthCells) {
                        val lx = offX + li * scale + scale / 2f
                        if (lx >= rowBarW && lx <= width.toFloat()) {
                            canvas.drawText(li.toString(), lx, barY + dp(15f), labelPaint)
                        }
                    }
                    li += step
                }
            } else {
                // 缩小/适配：画布边缘，每 5 格
                val step = 5 * ceil(48f / (scale * 5f)).toInt().coerceAtLeast(1)
                val labelCanvasLeft = offX
                val labelCanvasRight = offX + widthCells * scale
                val labelCanvasTop = offY
                val labelCanvasBottom = offY + heightCells * scale
                val rowBarW = dp(22f)
                val colBarH = dp(24f)
                // 底部列号横条（画布底边下方）
                if (labelCanvasBottom + colBarH <= height.toFloat()) {
                    labelBgPaint.color = colBarColor
                    canvas.drawRect(labelCanvasLeft, labelCanvasBottom, labelCanvasRight, labelCanvasBottom + colBarH, labelBgPaint)
                    labelPaint.color = colTextColor
                    var li = ((x0 + step - 1) / step) * step
                    while (li <= x1) {
                        if (li >= 0 && li <= widthCells) {
                            val lx = offX + li * scale + scale / 2f
                            if (lx >= -30f && lx <= width.toFloat() + 30f) {
                                canvas.drawText(li.toString(), lx, labelCanvasBottom + dp(15f), labelPaint)
                            }
                        }
                        li += step
                    }
                }
                // 左侧行号竖条（画布左边，白色，收窄，带分隔线）
                if (labelCanvasLeft >= rowBarW + dp(4f)) {
                    val barX = labelCanvasLeft - rowBarW
                    labelBgPaint.color = colBarColor
                    canvas.drawRect(barX, labelCanvasTop, barX + rowBarW, labelCanvasBottom, labelBgPaint)
                    labelBgPaint.color = if (night) 0x44FFFFFF.toInt() else 0x44000000.toInt()
                    var lineJ = y0
                    while (lineJ <= y1) {
                        val lineY = offY + lineJ * scale
                        if (lineY >= labelCanvasTop && lineY <= labelCanvasBottom) {
                            canvas.drawRect(barX + dp(2f), lineY, barX + rowBarW - dp(2f), lineY + dp(1f), labelBgPaint)
                        }
                        lineJ += step
                    }
                    labelPaint.color = colTextColor
                    var lj = ((y0 + step - 1) / step) * step
                    while (lj <= y1) {
                        if (lj >= 0 && lj <= heightCells) {
                            val ly = offY + lj * scale + scale / 2f
                            if (ly >= -30f && ly <= height.toFloat() + 30f) {
                                canvas.drawText(lj.toString(), barX + rowBarW / 2f, ly + labelPaint.textSize / 3f, labelPaint)
                            }
                        }
                        lj += step
                    }
                }
            }
        }

        val bx1 = offX + widthCells * scale
        val by1 = offY + heightCells * scale
        canvas.drawRect(offX, offY, bx1, by1, borderPaint)
        onTransformChanged?.invoke()
    }

    private fun markDirtyCell(x: Int, y: Int) {
        synchronized(cacheLock) {
            if (dirtyCells.size >= 512) {
                dirtyCells.clear()
                cacheDirtyAll = true
            } else {
                dirtyCells.add(y * widthCells + x)
            }
        }
        invalidate()
    }

    private fun markDirtyAll() {
        synchronized(cacheLock) {
            dirtyCells.clear()
            cacheDirtyAll = true
        }
        invalidate()
    }



    /** 放大时直接按全分辨率绘制可见格子（色块 + 可选棋盘格背景），与网格线严格对齐。 */
    private fun drawDirectCells(canvas: Canvas, x0: Int, x1: Int, y0: Int, y1: Int) {
        val useChecker = checkerBackground && scale >= dp(8f)
        if (!useChecker) {
            canvas.drawColor(emptyCellColor())
        }
        for (cy in y0 until y1) {
            val top = offY + cy * scale
            val rowBase = cy * widthCells
            for (cx in x0 until x1) {
                val c = pixels[rowBase + cx]
                val left = offX + cx * scale
                if (c == 0) {
                    if (useChecker) {
                        checkerPaint.color = if (((cx + cy) and 1) == 0) 0xFFF2F2F2.toInt() else 0xFFDDDDDD.toInt()
                        canvas.drawRect(left, top, left + scale, top + scale, checkerPaint)
                    }
                } else {
                    cellPaint.color = c
                    canvas.drawRect(left, top, left + scale, top + scale, cellPaint)
                }
            }
        }
    }

    private fun ensureCache() {
        val maxCells = max(widthCells, heightCells)
        if (maxCells <= 0) return
        val maxDim = 2048f
        // 缓存格宽取整为整数像素：相邻色块在缓存中无缝拼接，
        // 避免浮点格宽取整不一致产生 1px 缝隙（缩小视图的白色网格/摩尔纹根源）
        val raw = min(scale, maxDim / maxCells).coerceAtLeast(0.5f)
        val targetScale = floor(raw).toInt().toFloat().coerceAtLeast(1f)
        val zoomed = scale > fitScale * 1.02f
        val wantChecker = checkerBackground && zoomed && scale >= dp(8f)
        synchronized(cacheLock) {
            if (cacheBitmap == null) {
                requestRebuildLocked(targetScale, wantChecker)
                return
            }
            val needRebuild = rebuildRequested ||
                targetScale > cacheScale * 1.5f ||
                targetScale < cacheScale * 0.67f ||
                cacheDirtyAll ||
                wantChecker != cacheChecker
            if (needRebuild) {
                requestRebuildLocked(targetScale, wantChecker)
            } else if (dirtyCells.isNotEmpty()) {
                updateDirtyCellsLocked()
            }
        }
    }

    private fun requestRebuildLocked(target: Float, checker: Boolean) {
        if (rebuildThread != null) {
            rebuildRequested = true
            rebuildTargetScale = target
            rebuildTargetChecker = checker
            return
        }
        if (widthCells * heightCells <= 20000) {
            // 小画布：同步缓存避免闪屏
            val bmp = buildCacheBitmap(target, checker)
            cacheBitmap?.let { if (it !== bmp) it.recycle() }
            cacheBitmap = bmp
            cacheScale = target
            cacheChecker = checker
            cacheDirtyAll = false
            if (dirtyCells.isNotEmpty()) applyDirtyToBitmap(bmp)
            return
        }
        val t = target
        val c = checker
        rebuildThread = Thread {
            try {
                rebuildLoop(t, c)
            } catch (e: Throwable) {
                // 后台重建异常时回退到 UI 线程同步重建，避免后台线程崩溃导致应用闪退
                try {
                    val bmp = buildCacheBitmap(t, c)
                    synchronized(cacheLock) {
                        cacheBitmap?.let { if (it !== bmp) it.recycle() }
                        cacheBitmap = bmp
                        cacheScale = t
                        cacheChecker = c
                        cacheDirtyAll = false
                        dirtyCells.clear()
                    }
                } catch (e2: Throwable) {
                    // 双重失败：丢弃缓存，下次 onDraw 重试
                    synchronized(cacheLock) {
                        cacheBitmap = null
                        cacheScale = 0f
                        cacheDirtyAll = true
                    }
                }
            } finally {
                synchronized(cacheLock) {
                    rebuildThread = null
                }
                postInvalidate()
            }
        }.apply {
            isDaemon = true
            name = "canvas-cache"
            start()
        }
    }

    private fun rebuildLoop(initialTarget: Float, initialChecker: Boolean) {
        var target = initialTarget
        var checker = initialChecker
        while (true) {
            val bmp = buildCacheBitmap(target, checker)
            var swapped = false
            synchronized(cacheLock) {
                if (rebuildRequested) {
                    target = rebuildTargetScale
                    checker = rebuildTargetChecker
                    rebuildRequested = false
                } else {
                    cacheBitmap = bmp
                    cacheScale = target
                    cacheChecker = checker
                    cacheDirtyAll = false
                    if (dirtyCells.isNotEmpty()) applyDirtyToBitmap(bmp)
                    swapped = true
                }
            }
            if (swapped) break
        }
    }

    private fun buildCacheBitmap(targetScale: Float, useChecker: Boolean): Bitmap {
        val cs = targetScale.toInt().coerceAtLeast(1)
        val w = max(1, widthCells * cs)
        val h = max(1, heightCells * cs)
        val bmp = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888)
        val cv = Canvas(bmp)
        val cPaint = Paint()
        val chPaint = Paint()
        if (!useChecker) {
            cv.drawColor(emptyCellColor())
        }
        for (y in 0 until heightCells) {
            val top = y * cs
            val bottom = top + cs
            val rowBase = y * widthCells
            for (x in 0 until widthCells) {
                val c = pixels[rowBase + x]
                val left = x * cs
                val right = left + cs
                if (c == 0) {
                    if (useChecker) {
                        chPaint.color = if (((x + y) and 1) == 0) 0xFFF2F2F2.toInt() else 0xFFDDDDDD.toInt()
                        cv.drawRect(left.toFloat(), top.toFloat(), right.toFloat(), bottom.toFloat(), chPaint)
                    }
                } else {
                    cPaint.color = c
                    cv.drawRect(left.toFloat(), top.toFloat(), right.toFloat(), bottom.toFloat(), cPaint)
                }
            }
        }
        return bmp
    }

    private fun applyDirtyToBitmap(bmp: Bitmap) {
        val cs = cacheScale.toInt().coerceAtLeast(1)
        val zoomed = scale > fitScale * 1.02f
        val useChecker = checkerBackground && zoomed && scale >= dp(8f)
        val cv = Canvas(bmp)
        val cPaint = Paint()
        val chPaint = Paint()
        for (idx in dirtyCells) {
            val x = idx % widthCells
            val y = idx / widthCells
            val c = pixels[idx]
            val left = (x * cs).toFloat()
            val top = (y * cs).toFloat()
            val right = left + cs
            val bottom = top + cs
            if (c == 0) {
                chPaint.color = if (useChecker) {
                    if (((x + y) and 1) == 0) 0xFFF2F2F2.toInt() else 0xFFDDDDDD.toInt()
                } else {
                    emptyCellColor()
                }
                cv.drawRect(left, top, right, bottom, chPaint)
            } else {
                cPaint.color = c
                cv.drawRect(left, top, right, bottom, cPaint)
            }
        }
        dirtyCells.clear()
    }

    private fun updateDirtyCellsLocked() {
        val cache = cacheBitmap ?: return
        applyDirtyToBitmap(cache)
    }

    private fun drawCellText(canvas: Canvas, text: String, left: Float, top: Float, cell: Float, color: Int) {
        if (text.isEmpty()) return
        textPaint.color = ColorUtils.contrastText(color)
        var size = cell * 0.42f
        textPaint.textSize = size
        val maxW = cell * 0.92f
        if (text.length * size * 0.62f > maxW) {
            var guard = 0
            while (guard < 8 && size > 3f && textPaint.measureText(text) > maxW) {
                size *= 0.82f
                textPaint.textSize = size
                guard++
            }
        }
        val x = left + cell / 2f
        val y = top + cell / 2f - (textPaint.ascent() + textPaint.descent()) / 2f
        canvas.drawText(text, x, y, textPaint)
    }

    override fun onTouchEvent(event: MotionEvent): Boolean {
        scaleDetector.onTouchEvent(event)
        gestureDetector.onTouchEvent(event)
        when (event.actionMasked) {
            MotionEvent.ACTION_DOWN -> {
                parent?.requestDisallowInterceptTouchEvent(true)
                multiTouch = false
                downX = event.x
                downY = event.y
                lastTouchX = event.x
                lastTouchY = event.y
                lastFocusX = event.x
                lastFocusY = event.y
                lastCellX = -1
                lastCellY = -1
                drawingStroke = false
                pendingTap = true
            }
            MotionEvent.ACTION_POINTER_DOWN -> {
                // 第二根手指落下：进入多指会话，取消进行中的单指笔绘
                multiTouch = true
                cancelStroke()
                pendingTap = false
                var fx = 0f
                var fy = 0f
                for (i in 0 until event.pointerCount) {
                    fx += event.getX(i)
                    fy += event.getY(i)
                }
                lastFocusX = fx / event.pointerCount
                lastFocusY = fy / event.pointerCount
            }
            MotionEvent.ACTION_MOVE -> {
                if (event.pointerCount >= 2) {
                    pendingTap = false
                    var fx = 0f
                    var fy = 0f
                    for (i in 0 until event.pointerCount) {
                        fx += event.getX(i)
                        fy += event.getY(i)
                    }
                    fx /= event.pointerCount
                    fy /= event.pointerCount
                    offX += fx - lastFocusX
                    offY += fy - lastFocusY
                    lastFocusX = fx
                    lastFocusY = fy
                    invalidate()
                } else if (!scaleDetector.isInProgress) {
                    if (tool == Tool.PAN) {
                        offX += event.x - lastTouchX
                        offY += event.y - lastTouchY
                        lastTouchX = event.x
                        lastTouchY = event.y
                        invalidate()
                    } else if (tool == Tool.BRUSH || tool == Tool.ERASER) {
                        if (drawingStroke) {
                            continueStroke(event.x, event.y)
                        } else if (!multiTouch) {
                            val dx = event.x - downX
                            val dy = event.y - downY
                            if (dx * dx + dy * dy >= slop * slop) {
                                onStrokeStart?.invoke()
                                pushUndo()
                                drawingStroke = true
                                pendingTap = false
                                paintCellAt(event.x, event.y)
                            }
                        }
                    }
                    lastTouchX = event.x
                    lastTouchY = event.y
                }
            }
            MotionEvent.ACTION_POINTER_UP -> {
                // 松开一根手指：停止笔绘，避免剩余手指继续画
                drawingStroke = false
                pendingTap = false
                lastCellX = -1
                lastCellY = -1
            }
            MotionEvent.ACTION_UP -> {
                when (tool) {
                    Tool.BRUSH, Tool.ERASER -> {
                        if (drawingStroke) {
                            endStroke()
                        } else if (pendingTap) {
                            pushUndo()
                            val cell = cellAt(downX, downY)
                            if (cell != null) {
                                setPixel(cell.first, cell.second)
                                invalidate()
                                onStrokeEnd?.invoke()
                            } else {
                                notifyUndoState()
                            }
                        }
                    }
                    Tool.FILL -> {
                        if (pendingTap) {
                            val cell = cellAt(downX, downY)
                            if (cell != null) {
                                pushUndo()
                                floodFill(cell.first, cell.second)
                                onStrokeEnd?.invoke()
                            }
                        }
                    }
                    Tool.EYEDROPPER -> {
                        if (pendingTap) {
                            val cell = cellAt(downX, downY)
                            if (cell != null) {
                                val c = pixels[cell.second * widthCells + cell.first]
                                if (c != 0) onPickedColor?.invoke(c)
                            }
                        }
                    }
                    Tool.PAN -> {
                    }
                }
                pendingTap = false
                drawingStroke = false
                multiTouch = false
                lastCellX = -1
                lastCellY = -1
                parent?.requestDisallowInterceptTouchEvent(false)
            }
            MotionEvent.ACTION_CANCEL -> {
                drawingStroke = false
                pendingTap = false
                multiTouch = false
                lastCellX = -1
                lastCellY = -1
                parent?.requestDisallowInterceptTouchEvent(false)
            }
        }
        return true
    }

    private fun cancelStroke() {
        if (drawingStroke) {
            if (undoStack.isNotEmpty()) {
                pixels = undoStack.removeAt(undoStack.size - 1)
                notifyUndoState()
            }
            drawingStroke = false
            lastCellX = -1
            lastCellY = -1
            invalidate()
        }
    }

    private fun cellAt(x: Float, y: Float): Pair<Int, Int>? {
        val cx = floor((x - offX) / scale).toInt()
        val cy = floor((y - offY) / scale).toInt()
        if (cx < 0 || cy < 0 || cx >= widthCells || cy >= heightCells) return null
        return cx to cy
    }

    private fun paintCellAt(x: Float, y: Float) {
        val cell = cellAt(x, y) ?: return
        if (cell.first == lastCellX && cell.second == lastCellY) return
        setPixel(cell.first, cell.second)
        lastCellX = cell.first
        lastCellY = cell.second
        invalidate()
    }

    private fun continueStroke(x: Float, y: Float) {
        val cell = cellAt(x, y) ?: return
        if (lastCellX < 0) {
            setPixel(cell.first, cell.second)
        } else {
            lineCells(lastCellX, lastCellY, cell.first, cell.second)
        }
        lastCellX = cell.first
        lastCellY = cell.second
        invalidate()
    }

    private fun lineCells(x0: Int, y0: Int, x1: Int, y1: Int) {
        var dx = abs(x1 - x0)
        var dy = -abs(y1 - y0)
        val sx = if (x0 < x1) 1 else -1
        val sy = if (y0 < y1) 1 else -1
        var err = dx + dy
        var x = x0
        var y = y0
        while (true) {
            setPixel(x, y)
            if (x == x1 && y == y1) break
            val e2 = 2 * err
            if (e2 >= dy) {
                err += dy
                x += sx
            }
            if (e2 <= dx) {
                err += dx
                y += sy
            }
        }
    }

    private fun setPixel(x: Int, y: Int) {
        if (x < 0 || y < 0 || x >= widthCells || y >= heightCells) return
        val color = if (tool == Tool.ERASER) 0 else currentColor
        val i = y * widthCells + x
        if (pixels[i] != color) {
            pixels[i] = color
            onPixelEdited?.invoke(x, y)
            markDirtyCell(x, y)
        }
    }

    private fun floodFill(x: Int, y: Int) {
        val target = pixels[y * widthCells + x]
        val replace = currentColor
        if (target == replace) return
        val stack = mutableListOf<Int>()
        stack.add(x)
        stack.add(y)
        while (stack.isNotEmpty()) {
            val cy = stack.removeAt(stack.size - 1)
            val cx = stack.removeAt(stack.size - 1)
            if (cx < 0 || cy < 0 || cx >= widthCells || cy >= heightCells) continue
            val i = cy * widthCells + cx
            if (pixels[i] != target) continue
            pixels[i] = replace
            onPixelEdited?.invoke(cx, cy)
            stack.add(cx + 1)
            stack.add(cy)
            stack.add(cx - 1)
            stack.add(cy)
            stack.add(cx)
            stack.add(cy + 1)
            stack.add(cx)
            stack.add(cy - 1)
        }
        cacheDirtyAll = true
        invalidate()
    }

    private fun pushUndo() {
        if (undoStack.size >= undoLimit) undoStack.removeAt(0)
        undoStack.add(pixels.copyOf())
        redoStack.clear()
        notifyUndoState()
    }

    private fun endStroke() {
        if (drawingStroke) {
            drawingStroke = false
            notifyUndoState()
            onStrokeEnd?.invoke()
        }
        lastCellX = -1
        lastCellY = -1
    }

    private fun isNight(): Boolean {
        val night = resources.configuration.uiMode and android.content.res.Configuration.UI_MODE_NIGHT_MASK
        return night == android.content.res.Configuration.UI_MODE_NIGHT_YES
    }

    private fun emptyCellColor(): Int {
        return if (isNight()) 0xFF1C1C1C.toInt() else 0xFFE8E8E8.toInt()
    }

    private fun dp(v: Float): Float = v * resources.displayMetrics.density
}

