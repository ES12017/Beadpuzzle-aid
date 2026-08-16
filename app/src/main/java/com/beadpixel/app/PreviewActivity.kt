package com.beadpixel.app

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Paint
import android.os.Bundle
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import com.beadpixel.app.databinding.ActivityPreviewBinding
import kotlin.math.max
import kotlin.math.min

class PreviewActivity : AppCompatActivity() {

    companion object {
        const val EXTRA_PROJECT_ID = "project_id"
    }

    private lateinit var binding: ActivityPreviewBinding

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityPreviewBinding.inflate(layoutInflater)
        setContentView(binding.root)

        val id = intent.getStringExtra(EXTRA_PROJECT_ID)
        val p = id?.let { ProjectStore.load(this, it) }
        if (p == null) {
            Toast.makeText(this, "画布不存在", Toast.LENGTH_SHORT).show()
            finish()
            return
        }
        binding.previewImage.setImageBitmap(render(p))
        binding.btnBack.setOnClickListener { finish() }
    }

    // 与"导出图片（不勾选网格）"一致：无网格、无棋盘格、无色号文字
    private fun render(p: PixelProject): Bitmap {
        val metrics = resources.displayMetrics
        val maxPx = max(1, min(metrics.widthPixels - dp(16) * 2, metrics.heightPixels - dp(64) * 2))
        val cell = max(1, maxPx / max(p.width, p.height))
        val bmp = Bitmap.createBitmap(p.width * cell, p.height * cell, Bitmap.Config.ARGB_8888)
        val cv = Canvas(bmp)
        val paint = Paint()
        for (y in 0 until p.height) {
            for (x in 0 until p.width) {
                val c = p.pixels[y * p.width + x]
                if (c != 0) {
                    paint.color = c
                    cv.drawRect(
                        (x * cell).toFloat(),
                        (y * cell).toFloat(),
                        ((x + 1) * cell).toFloat(),
                        ((y + 1) * cell).toFloat(),
                        paint
                    )
                }
            }
        }
        return bmp
    }

    private fun dp(v: Int): Int = (v * resources.displayMetrics.density).toInt()
}
