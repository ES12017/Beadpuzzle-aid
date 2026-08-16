package com.beadpixel.app

import android.content.Context
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.util.Base64

object PaletteStore {
    private const val FILE_NAME = "palettes.json"

    @Synchronized
    fun load(context: Context): MutableList<Palette> {
        val f = File(context.filesDir, FILE_NAME)
        if (!f.exists()) {
            val list = defaultPalettes(context)
            save(context, list)
            return list
        }
        val list = try {
            val arr = JSONArray(f.readText())
            val l = mutableListOf<Palette>()
            for (i in 0 until arr.length()) l.add(Palette.fromJson(arr.getJSONObject(i)))
            l
        } catch (e: Exception) {
            defaultPalettes(context)
        }
        // 迁移：移除旧默认调色盘（基础色/经典119色），补入内置品牌色卡
        val hasOldDefaults = list.any { it.name == "基础色" || it.name == "经典 119 色" }
        val hasColorCards = list.any { it.name == "优肯197色" || it.id == "coco-291" }
        if (hasOldDefaults || !hasColorCards) {
            list.removeAll { it.name == "基础色" || it.name == "经典 119 色" }
            val existingIds = list.map { it.id }.toSet()
            val builtins = defaultPalettes(context)
            var changed = false
            for (bp in builtins) {
                if (bp.id !in existingIds) {
                    bp.isActive = false
                    list.add(bp)
                    changed = true
                }
            }
            if (changed) {
                if (list.none { it.isActive } && list.isNotEmpty()) list[0].isActive = true
                save(context, list)
            }
        }
        if (list.none { it.isActive } && list.isNotEmpty()) {
            list[0].isActive = true
            save(context, list)
        }
        return list
    }

    fun save(context: Context, palettes: List<Palette>) {
        val arr = JSONArray()
        for (p in palettes) arr.put(p.toJson())
        File(context.filesDir, FILE_NAME).writeText(arr.toString())
    }

    fun activePalette(context: Context): Palette? {
        val list = load(context)
        return list.firstOrNull { it.isActive } ?: list.firstOrNull()
    }

    fun setActive(context: Context, palettes: MutableList<Palette>, paletteId: String) {
        for (p in palettes) p.isActive = (p.id == paletteId)
        save(context, palettes)
    }

    private fun defaultPalettes(context: Context): MutableList<Palette> {
        val list = mutableListOf<Palette>()
        try {
            val text = context.resources.openRawResource(R.raw.builtin_palettes).bufferedReader().use { it.readText() }
            val arr = JSONArray(text)
            for (i in 0 until arr.length()) {
                list.add(Palette.fromJson(arr.getJSONObject(i)))
            }
        } catch (e: Exception) {
        }
        if (list.isEmpty()) {
            val basic = Palette(name = "基础色")
            basic.colors.add(PaletteColor(name = "黑色", argb = 0xFF000000.toInt()))
            basic.colors.add(PaletteColor(name = "白色", argb = 0xFFFFFFFF.toInt()))
            list.add(basic)
        }
        if (list.none { it.isActive } && list.isNotEmpty()) list[0].isActive = true
        return list
    }

    fun exportHxs(palette: Palette): String {
        val data = JSONArray()
        for (c in palette.colors) {
            val o = JSONObject()
            val code = if (c.code.isNotBlank()) c.code else if (c.name.isNotBlank()) c.name else "#" + ColorUtils.hex(c.argb)
            o.put("name", code)
            o.put("color", ColorUtils.argbToUnsignedLong(c.argb))
            data.put(o)
        }
        val root = JSONObject()
        root.put("palette_name", palette.name)
        root.put("palette_data", data)
        return Base64.getEncoder().encodeToString(root.toString().toByteArray(Charsets.UTF_8))
    }

    fun exportJson(palette: Palette): String {
        val o = palette.toJson()
        o.put("format", "beadpixel-palette")
        o.put("version", 1)
        return o.toString()
    }

    fun parsePaletteText(text: String): Palette? {
        val trimmed = text.trim()
        try {
            val o = JSONObject(trimmed)
            val p = fromAnyJson(o)
            if (p != null) return p
        } catch (e: Exception) {
        }
        try {
            val bytes = Base64.getMimeDecoder().decode(trimmed)
            val o = JSONObject(String(bytes, Charsets.UTF_8))
            val p = fromAnyJson(o)
            if (p != null) return p
        } catch (e: Exception) {
        }
        return null
    }

    private fun fromAnyJson(o: JSONObject): Palette? {
        if (o.has("palette_data")) {
            val p = Palette(name = o.optString("palette_name", "导入调色盘"))
            val arr = o.optJSONArray("palette_data") ?: return p
            for (i in 0 until arr.length()) {
                val c = arr.getJSONObject(i)
                val name = c.optString("name")
                var argb = 0xFF000000.toInt()
                if (c.has("color")) {
                    argb = c.optLong("color", 0).toInt()
                }
                p.colors.add(PaletteColor(name = "", code = name, argb = argb))
            }
            return p
        }
        if (o.has("colors")) {
            return Palette.fromJson(o)
        }
        return null
    }
}
