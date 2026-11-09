package com.nvzhuang.app

import android.content.Context
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL

/**
 * AI 写穿搭文案：走用户自己的 OpenAI 兼容接口（默认 AMD Radeon 免费接口）。
 * DeepSeek-V4-Flash 是纯文字模型，只写文案、不出图。
 *
 * Key 的去处：只存在用户手机 App 的私有存储里（SharedPreferences），
 * 不写进代码、不打进安装包、不经过聊天。Base URL 与模型名不是密钥，
 * 作为默认值直接写在 App 里，用户可在设置里改。
 */

data class AiSettings(val baseUrl: String, val model: String, val apiKey: String)

const val DEFAULT_AI_BASE_URL = "https://developer.amd.com.cn/radeon/api/v1"
const val DEFAULT_AI_MODEL = "DeepSeek-V4-Flash"

private const val AI_PREFS = "nvzhuang_ai"

fun loadAiSettings(context: Context): AiSettings {
    val sp = context.getSharedPreferences(AI_PREFS, Context.MODE_PRIVATE)
    return AiSettings(
        baseUrl = sp.getString("base_url", DEFAULT_AI_BASE_URL) ?: DEFAULT_AI_BASE_URL,
        model = sp.getString("model", DEFAULT_AI_MODEL) ?: DEFAULT_AI_MODEL,
        apiKey = sp.getString("api_key", "") ?: "",
    )
}

fun saveAiSettings(context: Context, settings: AiSettings) {
    context.getSharedPreferences(AI_PREFS, Context.MODE_PRIVATE).edit()
        .putString("base_url", settings.baseUrl.trim())
        .putString("model", settings.model.trim())
        .putString("api_key", settings.apiKey.trim())
        .apply()
}

private fun chatUrl(baseUrl: String): String {
    val base = baseUrl.trim().trimEnd('/')
    return if (base.endsWith("/chat/completions")) base else "$base/chat/completions"
}

private fun buildCopyPrompt(item: Item): Pair<String, String> {
    val system = "你是一位小红书穿搭博主，擅长用口语化、有种草感的中文写穿搭文案。只输出文案本身，不要解释、不要加前后缀说明。"
    val user = if (item.mode == Mode.PORTRAIT) {
        "模特编号：${item.id}\n发型：${item.hairZh}\n呈现形式：${item.secZh}\n造型描述：${item.styleZh}\n" +
            "请写一段 80-120 字的文案，夸这位的发型和整体造型，突出适合拍什么风格的照片，语气自然亲切；" +
            "最后另起一行给 5-8 个话题标签（#开头，用空格分隔）。"
    } else {
        "服装编号：${item.id}\n分类：${catZh(item.cat)}\n中文标签：${item.displayTags.joinToString("、")}\n英文关键词：${item.kw}\n" +
            "请写一段 80-120 字的穿搭文案，突出这套衣服的亮点和适合的场合，语气自然亲切；" +
            "最后另起一行给 5-8 个话题标签（#开头，用空格分隔）。"
    }
    return system to user
}

private suspend fun chatCompletion(settings: AiSettings, system: String, user: String, maxTokens: Int): String =
    withContext(Dispatchers.IO) {
        val body = JSONObject().apply {
            put("model", settings.model)
            put("temperature", 0.8)
            put("max_tokens", maxTokens)
            put("messages", JSONArray().apply {
                put(JSONObject().apply { put("role", "system"); put("content", system) })
                put(JSONObject().apply { put("role", "user"); put("content", user) })
            })
        }
        val conn = (URL(chatUrl(settings.baseUrl)).openConnection() as HttpURLConnection).apply {
            requestMethod = "POST"
            connectTimeout = 15000
            readTimeout = 60000
            doOutput = true
            setRequestProperty("Content-Type", "application/json")
            setRequestProperty("Authorization", "Bearer ${settings.apiKey}")
        }
        conn.outputStream.use { it.write(body.toString().toByteArray(Charsets.UTF_8)) }
        val code = conn.responseCode
        val text = try {
            conn.inputStream.bufferedReader().readText()
        } catch (_: Exception) {
            conn.errorStream?.bufferedReader()?.readText() ?: ""
        }
        if (code !in 200..299) {
            val hint = if (code == 401) "（Key 可能不对或已失效）" else if (code == 404) "（接口地址或模型名可能不对）" else ""
            throw Exception("接口返回 $code$hint：${text.take(200)}")
        }
        val content = JSONObject(text)
            .optJSONArray("choices")?.optJSONObject(0)
            ?.optJSONObject("message")?.optString("content", "")?.trim() ?: ""
        if (content.isEmpty()) throw Exception("接口返回了空内容：${text.take(200)}")
        content
    }

/** 为一件衣服/模特写穿搭文案。未填 Key 时直接失败，调用方提示去设置里填。 */
suspend fun generateCopy(context: Context, item: Item): Result<String> {
    val settings = loadAiSettings(context)
    if (settings.apiKey.isBlank()) {
        return Result.failure(Exception("NO_KEY"))
    }
    return try {
        val (system, user) = buildCopyPrompt(item)
        Result.success(chatCompletion(settings, system, user, 800))
    } catch (e: Exception) {
        Result.failure(e)
    }
}

/** 测试连接：只发一句“你好”，看接口通不通、Key 对不对。 */
suspend fun testAiConnection(context: Context): Result<String> {
    val settings = loadAiSettings(context)
    if (settings.apiKey.isBlank()) {
        return Result.failure(Exception("NO_KEY"))
    }
    return try {
        val reply = chatCompletion(
            settings,
            "你是一个助手，只回答“连接成功”四个字。",
            "你好",
            50,
        )
        Result.success(reply)
    } catch (e: Exception) {
        Result.failure(e)
    }
}
