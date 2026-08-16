package com.beadpixel.app

import android.content.DialogInterface
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.view.View
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.recyclerview.widget.LinearLayoutManager
import com.beadpixel.app.databinding.ActivityMainBinding
import com.google.android.material.dialog.MaterialAlertDialogBuilder

class MainActivity : AppCompatActivity() {

    private lateinit var binding: ActivityMainBinding
    private val projects = mutableListOf<PixelProject>()
    private lateinit var adapter: ProjectAdapter

    private val pickImage = registerForActivityResult(ActivityResultContracts.GetContent()) { uri ->
        if (uri != null) onImageNew(uri)
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)
        SystemUi.fitSystemBars(this, binding.root)

        showDisclaimerIfNeeded()

        binding.btnNew.setOnClickListener { showNewProjectDialog() }
        binding.btnImageNew.setOnClickListener { pickImage.launch("image/*") }
        binding.btnPalette.setOnClickListener {
            startActivity(Intent(this, PaletteActivity::class.java))
        }
        binding.btnSettings.setOnClickListener {
            startActivity(Intent(this, SettingsActivity::class.java))
        }

        adapter = ProjectAdapter(
            projects,
            onClick = { openProject(it) },
            onLongClick = { showProjectMenu(it) }
        )
        binding.projectList.layoutManager = LinearLayoutManager(this)
        binding.projectList.adapter = adapter
    }

    override fun onResume() {
        super.onResume()
        reload()
    }

    private fun reload() {
        projects.clear()
        projects.addAll(
            ProjectStore.list(this).sortedWith(
                compareByDescending<PixelProject> { it.pinned }.thenByDescending { it.createdAt }
            )
        )
        adapter.notifyDataSetChanged()
        binding.emptyView.visibility = if (projects.isEmpty()) View.VISIBLE else View.GONE
    }

    private fun openProject(p: PixelProject) {
        startActivity(
            Intent(this, EditorActivity::class.java)
                .putExtra(EditorActivity.EXTRA_PROJECT_ID, p.id)
        )
    }

    private fun showDisclaimerIfNeeded() {
        if (SettingsRepository.disclaimerAccepted(this)) return
        val text = "欢迎使用「拼豆辅助」。首次使用前，请阅读并确认以下条款：\n\n" +
            "一、图片使用：本应用的「图片转像素」功能，请仅转换您拥有合法权利或已获授权的图片" +
            "（本人原创、本人拍摄、已授权素材等）。请勿将他人受版权保护的作品、他人肖像或隐私内容" +
            "用于传播或商业用途，由此产生的纠纷由使用者自行承担。\n\n" +
            "二、隐私与数据：本应用不收集、不上传任何个人信息或图片。您创建的所有画布、调色盘等" +
            "数据仅保存在本机；卸载应用后数据可能丢失，请自行备份。\n\n" +
            "三、服务免责：本应用按「现状」提供，不保证转换效果、准确性或特定功能。因使用本应用" +
            "（包括但不限于数据丢失、设备问题、转换效果不满意）造成的任何损失，开发者不承担责任。\n\n" +
            "四、调色盘格式：支持 .hxs_palette 等调色盘格式互通，与相关产品无隶属或授权关系。\n\n" +
            "五、仅供学习交流，严禁商用。\n\n" +
            "六、合法使用：请勿将本应用用于任何违法或侵犯他人权益的用途。\n\n" +
            "点击「同意并继续」即表示您已阅读并同意以上条款。"
        val scroll = android.widget.ScrollView(this)
        val tv = android.widget.TextView(this)
        tv.text = text
        tv.textSize = 14f
        tv.setTextColor(0xFF000000.toInt())
        tv.setLineSpacing(0f, 1.25f)
        tv.setPadding(dp(8), dp(4), dp(8), dp(4))
        scroll.addView(tv, android.widget.FrameLayout.LayoutParams(
            android.widget.FrameLayout.LayoutParams.MATCH_PARENT,
            android.widget.FrameLayout.LayoutParams.WRAP_CONTENT
        ))
        MaterialAlertDialogBuilder(this)
            .setTitle("免责声明")
            .setView(scroll)
            .setCancelable(false)
            .setPositiveButton("同意并继续", null)
            .setNegativeButton("不同意并退出", null)
            .show()
            .apply {
                getButton(DialogInterface.BUTTON_POSITIVE).setOnClickListener {
                    SettingsRepository.setDisclaimerAccepted(this@MainActivity, true)
                    dismiss()
                }
                getButton(DialogInterface.BUTTON_NEGATIVE).setOnClickListener {
                    finish()
                }
            }
    }

    private fun dp(v: Int): Int = (v * resources.displayMetrics.density).toInt()

    private fun showNewProjectDialog() {
        Dialogs.newProject(this) { name, w, h ->
            val p = PixelProject(name = name, width = w, height = h, pixels = IntArray(w * h))
            ProjectStore.save(this, p)
            openProject(p)
        }
    }

    private fun onImageNew(uri: Uri) {
        val palette = PaletteStore.activePalette(this)
        if (palette == null || palette.colors.isEmpty()) {
            Toast.makeText(this, "请先在调色盘中添加颜色", Toast.LENGTH_LONG).show()
            return
        }
        val bmp = ImageConverter.loadBitmap(this, uri)
        if (bmp == null) {
            Toast.makeText(this, "无法读取图片", Toast.LENGTH_SHORT).show()
            return
        }
        // 按图片比例给一个合适默认尺寸（长边 100）
        val maxSide = 100
        val iw: Int
        val ih: Int
        if (bmp.width >= bmp.height) {
            iw = maxSide
            ih = maxOf(1, bmp.height * maxSide / bmp.width)
        } else {
            ih = maxSide
            iw = maxOf(1, bmp.width * maxSide / bmp.height)
        }
        Dialogs.newProject(this, iw, ih) { name, w, h ->
            Dialogs.imageMode(this) { mode ->
                val result = ImageConverter.convert(bmp, w, h, palette.colors, mode)
                val p = PixelProject(name = name, width = w, height = h, pixels = result)
                ProjectStore.save(this, p)
                openProject(p)
                Toast.makeText(this, "已从图片创建画布", Toast.LENGTH_SHORT).show()
            }
        }
    }

    private fun showProjectMenu(p: PixelProject) {
        val items = arrayOf(if (p.pinned) "取消置顶" else "置顶", "删除")
        MaterialAlertDialogBuilder(this)
            .setTitle(p.name)
            .setItems(items) { _, which ->
                when (which) {
                    0 -> {
                        p.pinned = !p.pinned
                        ProjectStore.save(this, p)
                        reload()
                        Toast.makeText(this, if (p.pinned) "已置顶" else "已取消置顶", Toast.LENGTH_SHORT).show()
                    }
                    1 -> showDeleteDialog(p)
                }
            }
            .setNegativeButton("取消", null)
            .show()
    }

    private fun showDeleteDialog(p: PixelProject) {
        MaterialAlertDialogBuilder(this)
            .setTitle("删除画布")
            .setMessage("确定删除画布「" + p.name + "」？")
            .setPositiveButton("删除", null)
            .setNegativeButton("取消", null)
            .show()
            .apply {
                getButton(DialogInterface.BUTTON_POSITIVE).setOnClickListener {
                    ProjectStore.delete(this@MainActivity, p.id)
                    reload()
                    Toast.makeText(this@MainActivity, "已删除", Toast.LENGTH_SHORT).show()
                    dismiss()
                }
            }
    }
}
