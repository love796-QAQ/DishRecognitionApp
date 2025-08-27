package com.example.dishapp

import android.graphics.Bitmap
import ai.onnxruntime.*
import kotlin.math.sqrt

class Recognizer(private val ortEnv: OrtEnvironment, private val session: OrtSession) {

    /** Bitmap → FloatArray (1,3,224,224) */
    private fun preprocess(bitmap: Bitmap): FloatArray {
        val resized = Bitmap.createScaledBitmap(bitmap, 224, 224, true)
        val floatValues = FloatArray(3 * 224 * 224)
        var idx = 0
        for (y in 0 until 224) {
            for (x in 0 until 224) {
                val pixel = resized.getPixel(x, y)
                floatValues[idx++] = ((pixel shr 16 and 0xFF) / 255.0f - 0.5f) * 2
                floatValues[idx++] = ((pixel shr 8 and 0xFF) / 255.0f - 0.5f) * 2
                floatValues[idx++] = ((pixel and 0xFF) / 255.0f - 0.5f) * 2
            }
        }
        return floatValues
    }

    /** 提取特征向量 (1280维) */
    fun getEmbedding(bitmap: Bitmap): FloatArray {
        val inputName = session.inputNames.iterator().next()
        val inputTensor = OnnxTensor.createTensor(
            ortEnv,
            preprocess(bitmap),
            longArrayOf(1, 3, 224, 224)
        )
        val result = session.run(mapOf(inputName to inputTensor))
        val output = result[0].value as Array<FloatArray>
        return output[0]
    }

    /** 余弦相似度 */
    private fun cosineSimilarity(vec1: FloatArray, vec2: FloatArray): Float {
        var dot = 0f
        var normA = 0f
        var normB = 0f
        for (i in vec1.indices) {
            dot += vec1[i] * vec2[i]
            normA += vec1[i] * vec1[i]
            normB += vec2[i] * vec2[i]
        }
        return dot / (sqrt(normA) * sqrt(normB))
    }

    /** 返回 Top-K 识别结果 */
    fun recognizeTopK(bitmap: Bitmap, templateManager: TemplateManager, threshold: Float, k: Int = 3): List<Pair<String, Float>> {
        val emb = getEmbedding(bitmap)
        val results = mutableListOf<Pair<String, Float>>()

        for ((dish, vecList) in templateManager.getAllTemplates()) {
            for (vec in vecList) {
                val sim = cosineSimilarity(emb, vec)
                results.add(Pair(dish, sim))
            }
        }

        return results
            .filter { it.second >= threshold }
            .sortedByDescending { it.second }
            .take(k)
    }
}
