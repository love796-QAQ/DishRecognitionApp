package com.example.dishapp

import android.content.Context
import android.graphics.Bitmap
import org.json.JSONObject
import java.io.File
import java.io.FileReader
import java.io.FileWriter
import java.io.FileOutputStream

class TemplateManager(private val context: Context) {
    private val templateDir: File = File(context.getExternalFilesDir(null), "templates")
    private val cacheFile: File = File(templateDir, "templates.json")

    // 缓存结构: { 菜品名: [ [1280维向量], ... ] }
    private var templateCache: MutableMap<String, MutableList<FloatArray>> = mutableMapOf()

    init {
        if (!templateDir.exists()) templateDir.mkdirs()
        loadCache()
    }

    /** 保存模板图片和向量 */
    fun addTemplate(dishName: String, bitmap: Bitmap, embedding: FloatArray) {
        val dishDir = File(templateDir, dishName)
        if (!dishDir.exists()) dishDir.mkdirs()

        val file = File(dishDir, "template_${System.currentTimeMillis()}.jpg")
        FileOutputStream(file).use { out ->
            bitmap.compress(Bitmap.CompressFormat.JPEG, 90, out)
        }

        val list = templateCache.getOrPut(dishName) { mutableListOf() }
        list.add(embedding)
        saveCache()
    }

    /** 获取所有模板 */
    fun getAllTemplates(): Map<String, List<FloatArray>> {
        return templateCache
    }

    /** 删除某个菜品的模板 */
    fun deleteDish(dishName: String) {
        val dishDir = File(templateDir, dishName)
        if (dishDir.exists()) dishDir.deleteRecursively()
        templateCache.remove(dishName)
        saveCache()
    }

    /** 加载 JSON 缓存 */
    private fun loadCache() {
        if (!cacheFile.exists()) return
        val text = FileReader(cacheFile).readText()
        val json = JSONObject(text)
        for (dish in json.keys()) {
            val arrList = mutableListOf<FloatArray>()
            val jsonArr = json.getJSONArray(dish)
            for (i in 0 until jsonArr.length()) {
                val jsonVec = jsonArr.getJSONArray(i)
                val vec = FloatArray(jsonVec.length()) { j -> jsonVec.getDouble(j).toFloat() }
                arrList.add(vec)
            }
            templateCache[dish] = arrList
        }
    }

    /** 保存 JSON 缓存 */
    private fun saveCache() {
        val json = JSONObject()
        for ((dish, vectors) in templateCache) {
            val arr = org.json.JSONArray()
            for (vec in vectors) {
                val vArr = org.json.JSONArray()
                vec.forEach { vArr.put(it) }
                arr.put(vArr)
            }
            json.put(dish, arr)
        }
        FileWriter(cacheFile).use { it.write(json.toString()) }
    }
}
