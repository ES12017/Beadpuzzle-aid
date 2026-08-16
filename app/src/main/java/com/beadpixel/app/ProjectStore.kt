package com.beadpixel.app

import android.content.Context
import org.json.JSONArray
import org.json.JSONObject
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.io.DataInputStream
import java.io.DataOutputStream
import java.io.File
import java.util.concurrent.Executors

object ProjectStore {
    private fun dir(context: Context): File = File(context.filesDir, "projects").apply { mkdirs() }

    private const val MAGIC = 0x50494E44 // "PIND"
    private const val VERSION = 3
    private val ioExecutor = Executors.newSingleThreadExecutor()
    private val saveLock = Any()
    private var saveQueued = false

    fun save(context: Context, p: PixelProject) {
        saveSync(context, p)
    }

    /** 异步保存：大画布写盘不阻塞 UI，连续保存自动合并（以最新一次为准）。 */
    fun saveAsync(context: Context, p: PixelProject) {
        val bytes = encode(p)
        val f = File(dir(context), p.id + ".bin")
        val jsonF = File(dir(context), p.id + ".json")
        synchronized(saveLock) {
            if (saveQueued) return
            saveQueued = true
        }
        ioExecutor.execute {
            try {
                f.writeBytes(bytes)
                if (jsonF.exists()) jsonF.delete()
            } catch (_: Exception) {
            }
            synchronized(saveLock) { saveQueued = false }
        }
    }

    /** 同步保存：退出/暂停时兜底，确保数据落盘。 */
    fun saveSync(context: Context, p: PixelProject) {
        val bytes = encode(p)
        val f = File(dir(context), p.id + ".bin")
        f.writeBytes(bytes)
        val jsonF = File(dir(context), p.id + ".json")
        if (jsonF.exists()) jsonF.delete()
    }

    private fun encode(p: PixelProject): ByteArray {
        val bos = ByteArrayOutputStream()
        DataOutputStream(bos).use { dos ->
            dos.writeInt(MAGIC)
            dos.writeInt(VERSION)
            dos.writeUTF(p.id)
            dos.writeUTF(p.name)
            dos.writeInt(p.width)
            dos.writeInt(p.height)
            dos.writeLong(p.updatedAt)
            dos.writeLong(p.createdAt)
            dos.writeBoolean(p.pinned)
            for (v in p.pixels) dos.writeInt(v)
        }
        return bos.toByteArray()
    }

    fun load(context: Context, id: String): PixelProject? {
        val f = File(dir(context), id + ".bin")
        if (f.exists()) {
            return try {
                decode(f.readBytes())
            } catch (_: Exception) {
                null
            }
        }
        val fj = File(dir(context), id + ".json")
        if (fj.exists()) {
            return loadJson(fj, id)
        }
        return null
    }

    private fun decode(bytes: ByteArray): PixelProject? {
        return try {
            DataInputStream(ByteArrayInputStream(bytes)).use { dis ->
                val magic = dis.readInt()
                if (magic != MAGIC) return null
                val version = dis.readInt()
                val id = dis.readUTF()
                val name = dis.readUTF()
                val w = dis.readInt()
                val h = dis.readInt()
                val updatedAt = dis.readLong()
                var createdAt = updatedAt
                var pinned = false
                if (version >= 3) {
                    createdAt = dis.readLong()
                    pinned = dis.readBoolean()
                }
                val px = IntArray(w * h)
                for (i in px.indices) px[i] = dis.readInt()
                PixelProject(id = id, name = name, width = w, height = h, pixels = px, updatedAt = updatedAt, createdAt = createdAt, pinned = pinned)
            }
        } catch (_: Exception) {
            null
        }
    }

    private fun loadJson(f: File, id: String): PixelProject? {
        return try {
            val o = JSONObject(f.readText())
            val w = o.getInt("width")
            val h = o.getInt("height")
            val arr = o.getJSONArray("pixels")
            val px = IntArray(w * h)
            val n = minOf(px.size, arr.length())
            for (i in 0 until n) px[i] = arr.getInt(i)
            PixelProject(
                id = o.optString("id", id),
                name = o.optString("name", "未命名"),
                width = w,
                height = h,
                pixels = px,
                updatedAt = o.optLong("updatedAt", System.currentTimeMillis()),
                createdAt = o.optLong("createdAt", o.optLong("updatedAt", System.currentTimeMillis())),
                pinned = o.optBoolean("pinned", false)
            )
        } catch (_: Exception) {
            null
        }
    }

    fun list(context: Context): List<PixelProject> {
        val fs = dir(context).listFiles()?.filter { it.extension == "bin" || it.extension == "json" } ?: return emptyList()
        return fs.mapNotNull { load(context, it.nameWithoutExtension) }.sortedByDescending { it.updatedAt }
    }

    fun delete(context: Context, id: String) {
        File(dir(context), id + ".bin").delete()
        File(dir(context), id + ".json").delete()
    }
}
