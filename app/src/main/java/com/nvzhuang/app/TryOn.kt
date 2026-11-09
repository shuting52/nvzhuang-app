package com.nvzhuang.app

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import android.util.Base64
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import com.kyant.backdrop.Backdrop
import com.kyant.shapes.RoundedRectangle
import java.io.ByteArrayOutputStream
import java.io.File
import java.net.HttpURLConnection
import java.net.URL
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import kotlin.random.Random

/**
 * AI 试穿 Tab：上传人物照片（或从 AI 模特库选）+ 选一件服装，
 * 用用户自己的生图模型 Key 直接调模型接口出图，不经过任何后端。
 * 支持：Gemini Nano Banana / 豆包 Seedream / OpenAI 兼容（images/edits）。
 * Key 只存在这台手机 App 的私有存储里，不进代码、不进安装包。
 * 注意：纯文字模型 Key（如 DeepSeek-V4-Flash）做不了试穿，出不了图。
 */

enum class TryOnProvider(val label: String) {
    GEMINI("Gemini"),
    SEEDREAM("豆包 Seedream"),
    OPENAI("OpenAI 兼容"),
}

data class TryOnSettings(val provider: TryOnProvider, val baseUrl: String, val apiKey: String, val model: String)

fun defaultTryOnSettings(provider: TryOnProvider): TryOnSettings = when (provider) {
    TryOnProvider.GEMINI -> TryOnSettings(
        TryOnProvider.GEMINI, "https://generativelanguage.googleapis.com/v1beta", "", "gemini-2.5-flash-image",
    )
    TryOnProvider.SEEDREAM -> TryOnSettings(
        TryOnProvider.SEEDREAM, "https://ark.cn-beijing.volces.com/api/v3", "", "doubao-seedream-4-0-250828",
    )
    TryOnProvider.OPENAI -> TryOnSettings(TryOnProvider.OPENAI, "", "", "gpt-image-1")
}

private const val TRYON_PREFS = "nvzhuang_tryon"
private const val TRYON_HISTORY_PREFS = "nvzhuang_tryon_history"

fun loadTryOnSettings(context: Context): TryOnSettings {
    val sp = context.getSharedPreferences(TRYON_PREFS, Context.MODE_PRIVATE)
    val provider = try {
        TryOnProvider.valueOf(sp.getString("provider", TryOnProvider.GEMINI.name) ?: TryOnProvider.GEMINI.name)
    } catch (_: Exception) {
        TryOnProvider.GEMINI
    }
    val defaults = defaultTryOnSettings(provider)
    return TryOnSettings(
        provider = provider,
        baseUrl = sp.getString("base_url", defaults.baseUrl) ?: defaults.baseUrl,
        apiKey = sp.getString("api_key", "") ?: "",
        model = sp.getString("model", defaults.model) ?: defaults.model,
    )
}

fun saveTryOnSettings(context: Context, s: TryOnSettings) {
    context.getSharedPreferences(TRYON_PREFS, Context.MODE_PRIVATE).edit()
        .putString("provider", s.provider.name)
        .putString("base_url", s.baseUrl.trim())
        .putString("api_key", s.apiKey.trim())
        .putString("model", s.model.trim())
        .apply()
}

const val TRYON_PROMPT =
    "Change the outfit of the person in the first image to the exact outfit shown in the second image. " +
        "Keep the person's face, hairstyle, body shape, pose and the background unchanged. " +
        "Photorealistic full-body fashion catalog photo, modest commercial style, vertical 2:3."

// ---------- 图片处理 ----------

fun bitmapToJpegBase64(bitmap: Bitmap, maxSide: Int = 1024): String {
    val scale = minOf(1f, maxSide / maxOf(bitmap.width, bitmap.height).toFloat())
    val scaled = if (scale >= 1f) bitmap else Bitmap.createScaledBitmap(
        bitmap, (bitmap.width * scale).toInt(), (bitmap.height * scale).toInt(), true,
    )
    val out = ByteArrayOutputStream()
    scaled.compress(Bitmap.CompressFormat.JPEG, 85, out)
    return Base64.encodeToString(out.toByteArray(), Base64.NO_WRAP)
}

suspend fun urlToJpegBase64(url: String): String = withContext(Dispatchers.IO) {
    val conn = (URL(url).openConnection() as HttpURLConnection).apply {
        connectTimeout = 15000
        readTimeout = 30000
        connect()
    }
    if (conn.responseCode !in 200..299) throw Exception("图片下载失败：HTTP ${conn.responseCode}")
    val bytes = conn.inputStream.readBytes()
    val bmp = BitmapFactory.decodeByteArray(bytes, 0, bytes.size) ?: throw Exception("图片解码失败")
    bitmapToJpegBase64(bmp)
}

suspend fun uriToJpegBase64(context: Context, uri: Uri): String = withContext(Dispatchers.IO) {
    val bytes = context.contentResolver.openInputStream(uri)?.readBytes() ?: throw Exception("打不开这张照片")
    val bmp = BitmapFactory.decodeByteArray(bytes, 0, bytes.size) ?: throw Exception("图片解码失败")
    bitmapToJpegBase64(bmp)
}

// ---------- 各家模型调用 ----------

private fun httpError(code: Int, body: String): Exception {
    val hint = when (code) {
        401 -> "（Key 可能不对或已失效）"
        404 -> "（接口地址或模型名可能不对）"
        429 -> "（接口限流或额度用完）"
        else -> ""
    }
    return Exception("接口返回 $code$hint：${body.take(200)}")
}

private suspend fun postJson(url: String, apiKey: String, keyHeader: String, body: JSONObject): JSONObject =
    withContext(Dispatchers.IO) {
        val conn = (URL(url).openConnection() as HttpURLConnection).apply {
            requestMethod = "POST"
            connectTimeout = 15000
            readTimeout = 120000
            doOutput = true
            setRequestProperty("Content-Type", "application/json")
            if (keyHeader == "x-goog-api-key") setRequestProperty("x-goog-api-key", apiKey)
            else setRequestProperty("Authorization", "Bearer $apiKey")
        }
        conn.outputStream.use { it.write(body.toString().toByteArray(Charsets.UTF_8)) }
        val code = conn.responseCode
        val text = try {
            conn.inputStream.bufferedReader().readText()
        } catch (_: Exception) {
            conn.errorStream?.bufferedReader()?.readText() ?: ""
        }
        if (code !in 200..299) throw httpError(code, text)
        JSONObject(text)
    }

private suspend fun downloadBytes(url: String): ByteArray = withContext(Dispatchers.IO) {
    val conn = (URL(url).openConnection() as HttpURLConnection).apply {
        connectTimeout = 15000
        readTimeout = 60000
        connect()
    }
    if (conn.responseCode !in 200..299) throw Exception("结果图下载失败：HTTP ${conn.responseCode}")
    conn.inputStream.readBytes()
}

private suspend fun runGemini(s: TryOnSettings, personB64: String, garmentB64: String): ByteArray {
    val url = "${s.baseUrl.trimEnd('/')}/models/${s.model}:generateContent"
    val body = JSONObject().apply {
        put("contents", JSONArray().apply {
            put(JSONObject().apply {
                put("parts", JSONArray().apply {
                    put(JSONObject().apply { put("text", TRYON_PROMPT) })
                    put(JSONObject().apply {
                        put("inline_data", JSONObject().apply {
                            put("mime_type", "image/jpeg")
                            put("data", personB64)
                        })
                    })
                    put(JSONObject().apply {
                        put("inline_data", JSONObject().apply {
                            put("mime_type", "image/jpeg")
                            put("data", garmentB64)
                        })
                    })
                })
            })
        })
        put("generationConfig", JSONObject().apply {
            put("responseModalities", JSONArray().apply { put("TEXT"); put("IMAGE") })
        })
    }
    val resp = postJson(url, s.apiKey, "x-goog-api-key", body)
    val parts = resp.optJSONArray("candidates")?.optJSONObject(0)
        ?.optJSONObject("content")?.optJSONArray("parts")
    if (parts != null) {
        for (i in 0 until parts.length()) {
            val data = parts.optJSONObject(i)?.optJSONObject("inlineData")?.optString("data", "")
            if (!data.isNullOrEmpty()) return Base64.decode(data, Base64.DEFAULT)
        }
        val text = parts.optJSONObject(0)?.optString("text", "") ?: ""
        throw Exception("模型没有返回图片${if (text.isNotEmpty()) "，只返回了文字：${text.take(120)}" else ""}")
    }
    throw Exception("接口返回无法识别：${resp.toString().take(200)}")
}

private suspend fun runSeedream(s: TryOnSettings, personB64: String, garmentB64: String): ByteArray {
    val url = "${s.baseUrl.trimEnd('/')}/images/generations"
    val body = JSONObject().apply {
        put("model", s.model)
        put("prompt", TRYON_PROMPT)
        put("image", JSONArray().apply {
            put("data:image/jpeg;base64,$personB64")
            put("data:image/jpeg;base64,$garmentB64")
        })
        put("response_format", "b64_json")
    }
    val resp = postJson(url, s.apiKey, "bearer", body)
    val item = resp.optJSONArray("data")?.optJSONObject(0)
        ?: throw Exception("接口返回无法识别：${resp.toString().take(200)}")
    val b64 = item.optString("b64_json", "")
    if (b64.isNotEmpty()) return Base64.decode(b64, Base64.DEFAULT)
    val imageUrl = item.optString("url", "")
    if (imageUrl.isNotEmpty()) return downloadBytes(imageUrl)
    throw Exception("接口没有返回图片：${resp.toString().take(200)}")
}

private suspend fun runOpenAiEdits(s: TryOnSettings, personB64: String, garmentB64: String): ByteArray =
    withContext(Dispatchers.IO) {
        val boundary = "----nvzhuang${System.currentTimeMillis()}"
        val url = "${s.baseUrl.trimEnd('/')}/images/edits"
        val conn = (URL(url).openConnection() as HttpURLConnection).apply {
            requestMethod = "POST"
            connectTimeout = 15000
            readTimeout = 120000
            doOutput = true
            setRequestProperty("Authorization", "Bearer ${s.apiKey}")
            setRequestProperty("Content-Type", "multipart/form-data; boundary=$boundary")
        }
        fun field(name: String, value: String): ByteArray =
            "--$boundary\r\nContent-Disposition: form-data; name=\"$name\"\r\n\r\n$value\r\n".toByteArray(Charsets.UTF_8)
        fun filePart(name: String, filename: String, bytes: ByteArray): ByteArray {
            val head = "--$boundary\r\nContent-Disposition: form-data; name=\"$name\"; filename=\"$filename\"\r\nContent-Type: image/jpeg\r\n\r\n".toByteArray(Charsets.UTF_8)
            return head + bytes + "\r\n".toByteArray(Charsets.UTF_8)
        }
        val body = ByteArrayOutputStream().apply {
            write(field("model", s.model))
            write(field("prompt", TRYON_PROMPT))
            write(field("size", "1024x1536"))
            write(field("response_format", "b64_json"))
            write(filePart("image", "person.jpg", Base64.decode(personB64, Base64.DEFAULT)))
            write(filePart("image", "garment.jpg", Base64.decode(garmentB64, Base64.DEFAULT)))
            write("--$boundary--\r\n".toByteArray(Charsets.UTF_8))
        }
        conn.outputStream.use { it.write(body.toByteArray()) }
        val code = conn.responseCode
        val text = try {
            conn.inputStream.bufferedReader().readText()
        } catch (_: Exception) {
            conn.errorStream?.bufferedReader()?.readText() ?: ""
        }
        if (code !in 200..299) throw httpError(code, text)
        val item = JSONObject(text).optJSONArray("data")?.optJSONObject(0)
            ?: throw Exception("接口返回无法识别：${text.take(200)}")
        val b64 = item.optString("b64_json", "")
        if (b64.isNotEmpty()) return@withContext Base64.decode(b64, Base64.DEFAULT)
        val imageUrl = item.optString("url", "")
        if (imageUrl.isNotEmpty()) return@withContext downloadBytes(imageUrl)
        throw Exception("接口没有返回图片：${text.take(200)}")
    }

/** 调模型出图。返回图片原始字节（JPEG/PNG）。 */
suspend fun runTryOn(settings: TryOnSettings, personB64: String, garmentB64: String): Result<ByteArray> {
    return try {
        val bytes = when (settings.provider) {
            TryOnProvider.GEMINI -> runGemini(settings, personB64, garmentB64)
            TryOnProvider.SEEDREAM -> runSeedream(settings, personB64, garmentB64)
            TryOnProvider.OPENAI -> runOpenAiEdits(settings, personB64, garmentB64)
        }
        Result.success(bytes)
    } catch (e: Exception) {
        Result.failure(e)
    }
}

// ---------- 生成记录 ----------

data class TryOnRecord(val id: String, val garmentId: String, val model: String, val time: Long, val file: String)

fun saveTryOnResult(context: Context, bytes: ByteArray, garmentId: String, model: String): TryOnRecord? {
    return try {
        val dir = File(context.filesDir, "tryon").apply { if (!exists()) mkdirs() }
        val id = System.currentTimeMillis().toString()
        val file = File(dir, "$id.jpg")
        file.writeBytes(bytes)
        val record = TryOnRecord(id, garmentId, model, System.currentTimeMillis(), file.name)
        val records = loadTryOnHistory(context).toMutableList().apply { add(0, record) }.take(30)
        val arr = JSONArray()
        for (r in records) {
            arr.put(JSONObject().apply {
                put("id", r.id); put("garmentId", r.garmentId); put("model", r.model)
                put("time", r.time); put("file", r.file)
            })
        }
        context.getSharedPreferences(TRYON_HISTORY_PREFS, Context.MODE_PRIVATE).edit()
            .putString("records", arr.toString()).apply()
        record
    } catch (_: Exception) {
        null
    }
}

fun loadTryOnHistory(context: Context): List<TryOnRecord> {
    return try {
        val text = context.getSharedPreferences(TRYON_HISTORY_PREFS, Context.MODE_PRIVATE)
            .getString("records", "[]") ?: "[]"
        val arr = JSONArray(text)
        (0 until arr.length()).mapNotNull { i ->
            val o = arr.optJSONObject(i) ?: return@mapNotNull null
            TryOnRecord(
                o.optString("id"), o.optString("garmentId"), o.optString("model"),
                o.optLong("time"), o.optString("file"),
            )
        }
    } catch (_: Exception) {
        emptyList()
    }
}

fun deleteTryOnRecord(context: Context, record: TryOnRecord) {
    try {
        File(File(context.filesDir, "tryon"), record.file).delete()
        val records = loadTryOnHistory(context).filter { it.id != record.id }
        val arr = JSONArray()
        for (r in records) {
            arr.put(JSONObject().apply {
                put("id", r.id); put("garmentId", r.garmentId); put("model", r.model)
                put("time", r.time); put("file", r.file)
            })
        }
        context.getSharedPreferences(TRYON_HISTORY_PREFS, Context.MODE_PRIVATE).edit()
            .putString("records", arr.toString()).apply()
    } catch (_: Exception) {
    }
}

fun tryOnFile(context: Context, record: TryOnRecord): File =
    File(File(context.filesDir, "tryon"), record.file)

fun findGarmentById(repo: Repo, id: String): Item? {
    val q = id.trim()
    if (q.isEmpty()) return null
    return repo.all(Mode.COSTUME).firstOrNull { it.id.equals(q, ignoreCase = true) }
        ?: repo.all(Mode.ANIME).firstOrNull { it.id.equals(q, ignoreCase = true) }
}

// ---------- 试穿 Tab 界面 ----------

@Composable
fun TryOnTab(repo: Repo, garment: Item?, backdrop: Backdrop, onGarmentChange: (Item?) -> Unit, modifier: Modifier = Modifier) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val p = LocalPalette.current
    val glass = LocalGlassSettings.current
    var settings by remember { mutableStateOf(loadTryOnSettings(context)) }
    var personB64 by remember { mutableStateOf<String?>(null) }
    var personPreview by remember { mutableStateOf<Any?>(null) }
    var personLabel by remember { mutableStateOf("") }
    var personLoading by remember { mutableStateOf(false) }
    var garmentQuery by remember { mutableStateOf("") }
    var generating by remember { mutableStateOf(false) }
    var resultBytes by remember { mutableStateOf<ByteArray?>(null) }
    var resultLabel by remember { mutableStateOf("") }
    var error by remember { mutableStateOf("") }
    var history by remember { mutableStateOf(loadTryOnHistory(context)) }
    var showModelPicker by remember { mutableStateOf(false) }

    val pickPhoto = rememberLauncherForActivityResult(ActivityResultContracts.GetContent()) { uri ->
        if (uri != null) {
            personLoading = true
            scope.launch {
                try {
                    personB64 = uriToJpegBase64(context, uri)
                    personPreview = uri
                    personLabel = "上传的照片"
                    error = ""
                } catch (e: Exception) {
                    error = "照片处理失败：${e.message}"
                } finally {
                    personLoading = false
                }
            }
        }
    }

    fun saveSettings() = saveTryOnSettings(context, settings)

    fun generate() {
        val person = personB64
        val g = garment
        if (person == null) {
            error = "请先选一张人物照片（上传，或从 AI 模特库选）"
            return
        }
        if (g == null) {
            error = "请先选一件服装（去图库点“试穿这套”，或下面输编号查找）"
            return
        }
        if (settings.apiKey.isBlank()) {
            error = "请先在下方设置里填 API Key（要生图模型，纯文字 Key 做不了试穿）"
            return
        }
        saveSettings()
        generating = true
        error = ""
        resultBytes = null
        scope.launch {
            val garmentB64 = try {
                urlToJpegBase64(g.img)
            } catch (e: Exception) {
                generating = false
                error = "服装图下载失败：${e.message}"
                return@launch
            }
            val r = runTryOn(settings, person, garmentB64)
            generating = false
            r.onSuccess { bytes ->
                resultBytes = bytes
                resultLabel = g.id
                saveTryOnResult(context, bytes, g.id, settings.model)
                history = loadTryOnHistory(context)
            }.onFailure { error = it.message ?: "生成失败" }
        }
    }

    Column(modifier.fillMaxWidth().verticalScroll(rememberScrollState()).padding(horizontal = 16.dp)) {
        T("AI 试穿", 18, bold = true, modifier = Modifier.padding(top = 4.dp))
        T("上传你的照片，选一件衣服，模型帮你穿上身。照片和 Key 只在你手机上用，不上传到别处。", 13, p.muted)
        Spacer(Modifier.height(12.dp))

        // 人物
        TryOnSection("1 · 人物照片", backdrop) {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                Box(
                    Modifier.size(96.dp).clip(RoundedRectangle(12.dp)).glass(backdrop, glass, p.glass, radiusDp = 12f),
                    contentAlignment = Alignment.Center,
                ) {
                    when {
                        personLoading -> T("处理中…", 12, p.muted)
                        personPreview != null -> AsyncImage(
                            model = personPreview, contentDescription = "人物",
                            modifier = Modifier.fillMaxSize(), contentScale = ContentScale.Crop,
                        )
                        else -> T("未选照片", 12, p.muted)
                    }
                }
                Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    T(if (personLabel.isEmpty()) "还没选人物" else personLabel, 14, bold = true)
                    ActionButton("📷 上传照片", false, { pickPhoto.launch("image/*") }, Modifier.fillMaxWidth(), backdrop = backdrop)
                    ActionButton("👩 从 AI 模特库选", false, { showModelPicker = true }, Modifier.fillMaxWidth(), backdrop = backdrop)
                }
            }
        }
        Spacer(Modifier.height(12.dp))

        // 服装
        TryOnSection("2 · 服装", backdrop) {
            if (garment != null) {
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    Box(Modifier.size(96.dp).clip(RoundedRectangle(12.dp)).glass(backdrop, glass, p.glass, radiusDp = 12f)) {
                        AsyncImage(
                            model = garment.thumb, contentDescription = garment.id,
                            modifier = Modifier.fillMaxSize(), contentScale = ContentScale.Crop,
                        )
                    }
                    Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                        T(garment.id, 15, bold = true)
                        T("${catZh(garment.cat)} · ${garment.displayTags.take(5).joinToString(" ")}", 13, p.muted)
                        T("点“试穿这套”可换别的衣服", 12, p.muted)
                    }
                }
            } else {
                T("还没选服装，去图库点开一套，点“试穿这套”", 13, p.muted)
            }
            Spacer(Modifier.height(8.dp))
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Box(
                    Modifier.weight(1f).clip(RoundedRectangle(10.dp)).glass(backdrop, glass, p.glass, radiusDp = 10f)
                        .padding(horizontal = 12.dp, vertical = 10.dp),
                ) {
                    if (garmentQuery.isEmpty()) T("输编号找衣服，如 SPR-0001", 13, p.muted)
                    androidx.compose.foundation.text.BasicTextField(
                        value = garmentQuery, onValueChange = { garmentQuery = it },
                        modifier = Modifier.fillMaxWidth(),
                        textStyle = androidx.compose.ui.text.TextStyle(color = p.text, fontSize = 13.sp),
                        singleLine = true,
                    )
                }
                ActionButton("查找", false, {
                    val found = findGarmentById(repo, garmentQuery)
                    if (found != null) {
                        onGarmentChange(found)
                        error = ""
                    } else {
                        error = "没找到编号 $garmentQuery"
                    }
                }, Modifier.width(64.dp), backdrop = backdrop)
                ActionButton("随机", false, {
                    val all = repo.all(Mode.COSTUME)
                    if (all.isNotEmpty()) onGarmentChange(all.random(Random.Default))
                }, Modifier.width(64.dp), backdrop = backdrop)
            }
        }
        Spacer(Modifier.height(12.dp))

        // 模型设置
        TryOnSection("3 · 模型设置（自定义 Key）", backdrop) {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                for (provider in TryOnProvider.entries) {
                    Chip(provider.label, settings.provider == provider, backdrop) {
                        val defaults = defaultTryOnSettings(provider)
                        settings = settings.copy(
                            provider = provider, baseUrl = defaults.baseUrl, model = defaults.model,
                        )
                        saveSettings()
                    }
                }
            }
            Spacer(Modifier.height(8.dp))
            AiField("Base URL", settings.baseUrl, false) { settings = settings.copy(baseUrl = it) }
            AiField("API Key", settings.apiKey, true) { settings = settings.copy(apiKey = it) }
            AiField("模型名", settings.model, false) { settings = settings.copy(model = it) }
            Spacer(Modifier.height(8.dp))
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                ActionButton("保存设置", true, {
                    saveSettings()
                    Toast.makeText(context, "已保存", Toast.LENGTH_SHORT).show()
                }, Modifier.weight(1f))
                ActionButton("恢复默认", false, {
                    settings = defaultTryOnSettings(settings.provider).copy(apiKey = settings.apiKey)
                    saveSettings()
                }, Modifier.weight(1f), backdrop = backdrop)
            }
            Spacer(Modifier.height(6.dp))
            T("要生图模型才能试穿：Gemini 填 Nano Banana 模型、豆包填 Seedream、OpenAI 兼容填 gpt-image-1 这类。纯文字 Key（如 DeepSeek）做不了试穿。", 12, p.muted)
        }
        Spacer(Modifier.height(12.dp))

        // 生成
        ActionButton(
            if (generating) "生成中，可能要等 1 分钟…" else "✨ 开始 AI 试穿",
            true, { generate() }, Modifier.fillMaxWidth(),
        )
        if (error.isNotEmpty()) {
            Spacer(Modifier.height(8.dp))
            T(error, 13, Color(0xFFE5484D))
        }
        if (resultBytes != null) {
            Spacer(Modifier.height(12.dp))
            T("试穿结果 · $resultLabel", 15, bold = true)
            Spacer(Modifier.height(8.dp))
            Box(
                Modifier.fillMaxWidth().clip(RoundedCornerShape(14.dp)).background(p.chipBg),
                contentAlignment = Alignment.Center,
            ) {
                AsyncImage(
                    model = resultBytes, contentDescription = "试穿结果",
                    modifier = Modifier.fillMaxWidth().aspectRatio(2f / 3f),
                    contentScale = ContentScale.Fit,
                )
            }
            Spacer(Modifier.height(8.dp))
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                ActionButton("⬇ 下载结果图", true, {
                    scope.launch {
                        val bytes = resultBytes ?: return@launch
                        val msg = downloadBytesToGallery(context, bytes, "试穿-$resultLabel")
                        Toast.makeText(context, msg, Toast.LENGTH_LONG).show()
                    }
                }, Modifier.weight(1f))
                ActionButton("再生成一次", false, { generate() }, Modifier.weight(1f), backdrop = backdrop)
            }
        }
        Spacer(Modifier.height(16.dp))

        // 生成记录
        if (history.isNotEmpty()) {
            T("生成记录（${history.size}）", 15, bold = true)
            Spacer(Modifier.height(8.dp))
            LazyVerticalGrid(
                columns = GridCells.Fixed(3),
                modifier = Modifier.fillMaxWidth().height(240.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                items(history, key = { it.id }) { record ->
                    Box(Modifier.aspectRatio(2f / 3f).clip(RoundedRectangle(10.dp)).glass(backdrop, glass, p.glass, radiusDp = 10f)) {
                        AsyncImage(
                            model = tryOnFile(context, record), contentDescription = record.garmentId,
                            modifier = Modifier.fillMaxSize().clickable {
                                scope.launch {
                                    resultBytes = try {
                                        withContext(Dispatchers.IO) { tryOnFile(context, record).readBytes() }
                                    } catch (_: Exception) {
                                        null
                                    }
                                    resultLabel = record.garmentId
                                }
                            },
                            contentScale = ContentScale.Crop,
                        )
                        Box(
                            Modifier.align(Alignment.TopEnd).padding(4.dp)
                                .clip(CircleShape).background(Color(0xCC000000)).clickable {
                                    deleteTryOnRecord(context, record)
                                    history = loadTryOnHistory(context)
                                }.padding(6.dp),
                        ) { T("✕", 11, Color.White, bold = true) }
                    }
                }
            }
        }
        Spacer(Modifier.height(96.dp))
    }

    if (showModelPicker) {
        ModelPickerDialog(
            repo = repo,
            backdrop = backdrop,
            onPick = { item ->
                showModelPicker = false
                personLoading = true
                scope.launch {
                    try {
                        personB64 = urlToJpegBase64(item.img)
                        personPreview = item.thumb
                        personLabel = "AI 模特 ${item.id}（${item.hairZh} · ${item.secZh}）"
                        error = ""
                    } catch (e: Exception) {
                        error = "模特图下载失败：${e.message}"
                    } finally {
                        personLoading = false
                    }
                }
            },
            onClose = { showModelPicker = false },
        )
    }
}

@Composable
fun TryOnSection(title: String, backdrop: Backdrop, content: @Composable () -> Unit) {
    val p = LocalPalette.current
    val glass = LocalGlassSettings.current
    Column(
        Modifier.fillMaxWidth().clip(RoundedRectangle(glassRadiusDp(glass).dp)).glass(backdrop, glass, p.glass)
            .padding(14.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        T(title, 15, bold = true)
        content()
    }
}

@Composable
fun ModelPickerDialog(repo: Repo, backdrop: Backdrop, onPick: (Item) -> Unit, onClose: () -> Unit) {
    val p = LocalPalette.current
    val glass = LocalGlassSettings.current
    var seed by remember { mutableIntStateOf(0) }
    val items = remember(seed) { repo.all(Mode.PORTRAIT).shuffled(Random(seed)).take(60) }
    Box(
        Modifier.fillMaxSize().background(Color(0x99000000)).clickable { onClose() },
        contentAlignment = Alignment.Center,
    ) {
        Column(
            Modifier.fillMaxWidth(0.92f).clip(RoundedRectangle(16.dp)).glass(backdrop, glass, p.glass, radiusDp = 16f)
                .padding(14.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                T("选一位 AI 模特", 16, bold = true, modifier = Modifier.weight(1f))
                ActionButton("换一批", false, { seed = Random.nextInt() }, Modifier.width(84.dp), backdrop = backdrop)
            }
            LazyVerticalGrid(
                columns = GridCells.Fixed(3),
                modifier = Modifier.fillMaxWidth().height(420.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                items(items, key = { it.id }) { item ->
                    Column(
                        Modifier.clip(RoundedRectangle(10.dp)).glass(backdrop, glass, p.glass, radiusDp = 10f)
                            .clickable { onPick(item) },
                    ) {
                        Box(Modifier.fillMaxWidth().aspectRatio(2f / 3f)) {
                            AsyncImage(
                                model = item.thumb, contentDescription = item.id,
                                modifier = Modifier.fillMaxSize(), contentScale = ContentScale.Crop,
                            )
                        }
                        T("${item.hairZh} · ${item.secZh}", 11, p.muted, modifier = Modifier.padding(6.dp))
                    }
                }
            }
            ActionButton("关闭", false, onClose, Modifier.fillMaxWidth(), backdrop = backdrop)
        }
    }
}
