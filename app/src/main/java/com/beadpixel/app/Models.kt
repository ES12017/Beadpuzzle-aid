package com.beadpixel.app

import org.json.JSONArray
import org.json.JSONObject
import java.util.UUID

data class PaletteColor(
    val id: String = UUID.randomUUID().toString(),
    var name: String = "",
    var code: String = "",
    var argb: Int = 0xFF000000.toInt()
) {
    fun label(): String {
        if (name.isNotBlank()) return name
        if (code.isNotBlank()) return code
        return "#" + ColorUtils.hex(argb)
    }

    fun codeText(): String {
        if (code.isNotBlank()) return code
        return "#" + ColorUtils.hex(argb)
    }
}

data class Palette(
    val id: String = UUID.randomUUID().toString(),
    var name: String = "新调色盘",
    var colors: MutableList<PaletteColor> = mutableListOf(),
    var isActive: Boolean = false
) {
    fun toJson(): JSONObject {
        val arr = JSONArray()
        for (c in colors) {
            val o = JSONObject()
            o.put("name", c.name)
            o.put("code", c.code)
            o.put("rgb", "#" + ColorUtils.hex(c.argb))
            arr.put(o)
        }
        val o = JSONObject()
        o.put("id", id)
        o.put("name", name)
        o.put("isActive", isActive)
        o.put("colors", arr)
        return o
    }

    companion object {
        fun fromJson(o: JSONObject): Palette {
            val p = Palette(
                id = o.optString("id", UUID.randomUUID().toString()),
                name = o.optString("name", "调色盘"),
                isActive = o.optBoolean("isActive", false)
            )
            val arr = o.optJSONArray("colors") ?: JSONArray()
            for (i in 0 until arr.length()) {
                val c = arr.getJSONObject(i)
                val rgb = c.optString("rgb")
                val argb = ColorUtils.parseHex(rgb) ?: 0xFF000000.toInt()
                p.colors.add(
                    PaletteColor(
                        id = c.optString("id", UUID.randomUUID().toString()),
                        name = c.optString("name"),
                        code = c.optString("code"),
                        argb = argb
                    )
                )
            }
            return p
        }
    }
}

data class PixelProject(
    val id: String = UUID.randomUUID().toString(),
    var name: String = "未命名",
    var width: Int = 32,
    var height: Int = 32,
    var pixels: IntArray = IntArray(32 * 32),
    var updatedAt: Long = System.currentTimeMillis()
)
