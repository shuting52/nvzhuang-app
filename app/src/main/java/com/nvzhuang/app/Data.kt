package com.nvzhuang.app

import android.content.ContentValues
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.provider.MediaStore
import android.widget.Toast
import java.net.HttpURLConnection
import java.net.URL
import java.net.URLEncoder
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONArray

/** 数据来源：https://paipiange.github.io/nvzhuang/（MIT 开源，COSTUME-DB-01）。
 *  三个图库：写实服装 1407、动漫服装 1320、AI 模特 1499，共 4226 条。
 *  图片全部在线加载自原站，不打包进 APK。 */

enum class Mode(val label: String, val file: String) {
    COSTUME("写实", "costume.json"),
    ANIME("动漫", "anime.json"),
    PORTRAIT("AI模特", "portrait.json"),
}

data class Item(
    val mode: Mode,
    val id: String,
    val cat: String,
    val kw: String,
    val zh: List<String>,
    val thumb: String,
    val img: String,
    val full: String,
    val sec: String = "",
    val secZh: String = "",
    val hairZh: String = "",
    val styleZh: String = "",
) {
    val key: String get() = "${mode.name}:$id"
    /** 详情里展示的中文标签：服装用关键词中文，模特用发型/形式/造型 */
    val displayTags: List<String>
        get() = if (mode == Mode.PORTRAIT) {
            listOfNotNull(hairZh.ifEmpty { null }, secZh.ifEmpty { null }, styleZh.ifEmpty { null })
        } else zh
}

/** 分类日文名 -> 中文名（与原站一致） */
fun catZh(cat: String): String = when (cat) {
    "春" -> "春"
    "夏" -> "夏"
    "秋" -> "秋"
    "冬" -> "冬"
    "ランジェリー" -> "内衣"
    "パーティ" -> "派对"
    "水着" -> "泳装"
    "セクシー" -> "性感"
    "短发" -> "短发"
    "中发" -> "中发"
    "中长发" -> "中长发"
    "长发" -> "长发"
    else -> cat
}

fun catsFor(mode: Mode): List<String> = when (mode) {
    Mode.COSTUME, Mode.ANIME -> listOf("春", "夏", "秋", "冬", "ランジェリー", "パーティ", "水着", "セクシー")
    Mode.PORTRAIT -> listOf("短发", "中发", "中长发", "长发")
}

class Repo(val items: Map<Mode, List<Item>>, val en2zh: Map<String, String>) {
    val zh2en: Map<String, String> = en2zh.entries.associate { (en, zh) -> zh to en }
    fun all(mode: Mode): List<Item> = items[mode] ?: emptyList()
    fun byKey(key: String): Item? {
        val (m, id) = key.split(":", limit = 2)
        val mode = Mode.valueOf(m)
        return all(mode).firstOrNull { it.id == id }
    }

    /** 搜索：按 ID、英文关键词、中文关键词/标签、中文分类名匹配 */
    fun filter(mode: Mode, query: String, cat: String, sec: String): List<Item> {
        val q = query.trim()
        val tokens = q.split(Regex("\\s+")).filter { it.isNotEmpty() }
        return all(mode).filter { item ->
            (cat == "全部" || item.cat == cat) &&
                (sec == "全部" || item.sec == sec) &&
                tokens.all { tok -> matches(item, tok) }
        }
    }

    private fun matches(item: Item, tok: String): Boolean {
        val lower = tok.lowercase()
        if (item.id.lowercase().contains(lower)) return true
        if (item.kw.lowercase().contains(lower)) return true
        if (item.zh.any { it.contains(tok) }) return true
        if (catZh(item.cat).contains(tok)) return true
        if (item.secZh.contains(tok) || item.hairZh.contains(tok) || item.styleZh.contains(tok)) return true
        // 中文 -> 英文反查（例：搜“蕾丝”命中 lace）
        val en = zh2en[tok] ?: zh2en.entries.firstOrNull { it.key.contains(tok) }?.value
        if (en != null && item.kw.lowercase().contains(en.lowercase())) return true
        return false
    }
}

fun loadRepo(context: Context): Repo {
    val items = mutableMapOf<Mode, List<Item>>()
    for (mode in Mode.entries) {
        val text = context.assets.open(mode.file).bufferedReader().readText()
        val arr = JSONArray(text)
        val list = ArrayList<Item>(arr.length())
        for (i in 0 until arr.length()) {
            val o = arr.getJSONObject(i)
            val zh = ArrayList<String>()
            val zhArr = o.optJSONArray("zh")
            if (zhArr != null) for (j in 0 until zhArr.length()) zh.add(zhArr.getString(j))
            list.add(
                Item(
                    mode = mode,
                    id = o.getString("id"),
                    cat = o.getString("cat"),
                    kw = o.getString("kw"),
                    zh = zh,
                    thumb = o.getString("thumb"),
                    img = o.getString("img"),
                    full = o.getString("full"),
                    sec = o.optString("sec", ""),
                    secZh = o.optString("secZh", ""),
                    hairZh = o.optString("hairZh", ""),
                    styleZh = o.optString("styleZh", ""),
                )
            )
        }
        items[mode] = list
    }
    val en2zhText = context.assets.open("en2zh.json").bufferedReader().readText()
    val en2zhObj = org.json.JSONObject(en2zhText)
    val en2zh = mutableMapOf<String, String>()
    val keys = en2zhObj.keys()
    while (keys.hasNext()) {
        val k = keys.next()
        en2zh[k] = en2zhObj.getString(k)
    }
    return Repo(items, en2zh)
}

// ---------- 提示词库 ----------

/** 提示词库来源：https://noahvisualsx.com/zh/prompts/（2026-11-10 抓取，177 条）。
 *  全部写进 assets/prompts.json 做成本地数据，不嵌网站；配图在线加载自原站。 */
data class PromptItem(
    val id: String,
    val title: String,
    val note: String,
    val kind: String,
    val ratio: String,
    val tags: List<String>,
    val prompt: String,
    val image: String,
    /** AI 视频的原站示例视频地址（mp4，在线播放）；没有视频的条目为空。 */
    val video: String = "",
) {
    /** 列表分类：AI 生图 / AI 视频 / 其他（ChatGPT、Muse、Midjourney、可灵等） */
    val group: String
        get() = when (kind) {
            "AI 生图" -> "AI 生图"
            "AI 视频" -> "AI 视频"
            else -> "其他"
        }
}

fun loadPrompts(context: Context): List<PromptItem> {
    val text = context.assets.open("prompts.json").bufferedReader().readText()
    val arr = JSONArray(text)
    val list = ArrayList<PromptItem>(arr.length())
    for (i in 0 until arr.length()) {
        val o = arr.getJSONObject(i)
        val tags = ArrayList<String>()
        val tagsArr = o.optJSONArray("tags")
        if (tagsArr != null) for (j in 0 until tagsArr.length()) tags.add(tagsArr.getString(j))
        list.add(
            PromptItem(
                id = o.getString("id"),
                title = o.getString("title"),
                note = o.optString("note", ""),
                kind = o.optString("kind", ""),
                ratio = o.optString("ratio", ""),
                tags = tags,
                prompt = o.getString("prompt"),
                image = o.optString("image", ""),
                video = o.optString("video", ""),
            )
        )
    }
    return list
}

// ---------- 收藏 ----------

private const val PREFS = "nvzhuang"
private const val KEY_FAVS = "favs"

fun loadFavs(context: Context): Set<String> =
    context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).getStringSet(KEY_FAVS, emptySet()) ?: emptySet()

fun saveFavs(context: Context, favs: Set<String>) {
    context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit().putStringSet(KEY_FAVS, favs).apply()
}

// ---------- 分享 / 打开链接 / 下载 ----------

fun copyText(context: Context, text: String, toast: String) {
    val cm = context.getSystemService(Context.CLIPBOARD_SERVICE) as android.content.ClipboardManager
    cm.setPrimaryClip(android.content.ClipData.newPlainText("淑婷试衣间", text))
    Toast.makeText(context, toast, Toast.LENGTH_SHORT).show()
}

fun openUrl(context: Context, url: String) {
    try {
        context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(url)))
    } catch (_: Exception) {
        Toast.makeText(context, "打不开链接", Toast.LENGTH_SHORT).show()
    }
}

/** 淘宝/拼多多搜同款：先复制中文关键词，再打开购物搜索页 */
fun shopSearch(context: Context, item: Item, platform: String) {
    val keywords = if (item.displayTags.isNotEmpty()) item.displayTags.joinToString(" ") else item.kw
    copyText(context, keywords, "关键词已复制，去购物 App 粘贴搜索")
    val q = URLEncoder.encode(keywords, "UTF-8")
    val url = if (platform == "taobao") "https://s.taobao.com/search?q=$q"
    else "https://mobile.yangkeduo.com/search.html?search_key=$q"
    openUrl(context, url)
}

/** 把图片字节存到相册 Pictures/淑婷试衣间（试穿结果用）。 */
suspend fun downloadBytesToGallery(context: Context, bytes: ByteArray, name: String): String =
    withContext(Dispatchers.IO) {
        try {
            val fileName = "$name.jpg"
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                val values = ContentValues().apply {
                    put(MediaStore.Images.Media.DISPLAY_NAME, fileName)
                    put(MediaStore.Images.Media.MIME_TYPE, "image/jpeg")
                    put(MediaStore.Images.Media.RELATIVE_PATH, Environment.DIRECTORY_PICTURES + "/淑婷试衣间")
                    put(MediaStore.Images.Media.IS_PENDING, 1)
                }
                val resolver = context.contentResolver
                val uri = resolver.insert(MediaStore.Images.Media.EXTERNAL_CONTENT_URI, values)
                    ?: return@withContext "保存失败：无法创建相册文件"
                resolver.openOutputStream(uri)?.use { it.write(bytes) }
                values.clear()
                values.put(MediaStore.Images.Media.IS_PENDING, 0)
                resolver.update(uri, values, null, null)
            } else {
                @Suppress("DEPRECATION")
                val dir = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_PICTURES + "/淑婷试衣间")
                if (!dir.exists()) dir.mkdirs()
                java.io.File(dir, fileName).writeBytes(bytes)
            }
            "已保存到相册：Pictures/淑婷试衣间/$fileName"
        } catch (e: Exception) {
            "保存失败：${e.message ?: "未知错误"}"
        }
    }

/** 下载原图到相册 Pictures/淑婷试衣间。
 *  注意：原站写实版 assets/png/ 目录下的原尺寸 PNG 目前全部 404（2026-11-09 实测），
 *  所以写实版 full 指向高清 img webp；动漫/模特 full 为外站 PNG，可正常下载。 */
suspend fun downloadImage(context: Context, item: Item): String = withContext(Dispatchers.IO) {
    try {
        val ext = item.full.substringAfterLast('.', "webp").substringBefore('?').ifEmpty { "webp" }
        val fileName = "${item.id}.$ext"
        val url = URL(item.full)
        val conn = url.openConnection() as HttpURLConnection
        conn.connectTimeout = 15000
        conn.readTimeout = 30000
        conn.connect()
        if (conn.responseCode !in 200..299) return@withContext "下载失败：服务器返回 ${conn.responseCode}"
        val bytes = conn.inputStream.readBytes()
        conn.disconnect()
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            val values = ContentValues().apply {
                put(MediaStore.Images.Media.DISPLAY_NAME, fileName)
                put(MediaStore.Images.Media.MIME_TYPE, if (ext == "png") "image/png" else "image/webp")
                put(MediaStore.Images.Media.RELATIVE_PATH, Environment.DIRECTORY_PICTURES + "/淑婷试衣间")
                put(MediaStore.Images.Media.IS_PENDING, 1)
            }
            val resolver = context.contentResolver
            val uri = resolver.insert(MediaStore.Images.Media.EXTERNAL_CONTENT_URI, values)
                ?: return@withContext "下载失败：无法创建相册文件"
            resolver.openOutputStream(uri)?.use { it.write(bytes) }
            values.clear()
            values.put(MediaStore.Images.Media.IS_PENDING, 0)
            resolver.update(uri, values, null, null)
        } else {
            @Suppress("DEPRECATION")
            val dir = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_PICTURES + "/淑婷试衣间")
            if (!dir.exists()) dir.mkdirs()
            java.io.File(dir, fileName).writeBytes(bytes)
        }
        "已保存到相册：Pictures/淑婷试衣间/$fileName"
    } catch (e: Exception) {
        "下载失败：${e.message ?: "网络错误"}"
    }
}
