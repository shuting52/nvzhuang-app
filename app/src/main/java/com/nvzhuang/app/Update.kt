package com.nvzhuang.app

import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.provider.Settings
import android.widget.Toast
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.text.BasicText
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.FileProvider
import java.io.File
import java.io.FileOutputStream
import java.net.HttpURLConnection
import java.net.URL
import java.util.concurrent.atomic.AtomicBoolean
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.json.JSONObject
import com.kyant.shapes.RoundedRectangle

/**
 * 软件更新：弹窗是 Ludion login-lumen 的原生复刻（浅蓝底 + 近白玻璃卡 28dp 大圆角 +
 * 内容逐行浮现 + 玻璃提交按钮上斜扫的光带）；点“立即更新”开始下载，下载中按钮变
 * “更新中…”，跑满变“已更新 ✓”，1.2 秒后自动调起安装；下载中点“取消下载”可随时取消。
 * 下载中不显示进度条。
 * 功能：启动时自动查一次 + 关于页手动查；新版本下载到缓存后直接调起安装器覆盖安装，
 * 不用卸载旧版。覆盖安装的前提是包名相同 + 签名同一把 + versionCode 更大，
 * 本工程签名固定用同一把 keystore，且 v1+v2+v3 三种签名方案全开。
 *
 * 更新源：GitHub 仓库 shuting52/nvzhuang-app 根目录的 app-version.json，
 * 每次发新版时更新该文件（versionCode/versionName/apkUrl/notes 三段：优化/删除/增加）。
 */

const val UPDATE_JSON_URL =
    "https://raw.githubusercontent.com/shuting52/nvzhuang-app/main/app-version.json"

data class UpdateNotes(
    val optimize: List<String> = emptyList(),
    val remove: List<String> = emptyList(),
    val add: List<String> = emptyList(),
)

data class UpdateInfo(
    val versionName: String,
    val versionCode: Int,
    val apkUrl: String,
    val notes: UpdateNotes,
)

fun currentVersionCode(context: Context): Int {
    val info = context.packageManager.getPackageInfo(context.packageName, 0)
    return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) info.longVersionCode.toInt()
    else @Suppress("DEPRECATION") info.versionCode
}

fun currentVersionName(context: Context): String {
    return try {
        context.packageManager.getPackageInfo(context.packageName, 0).versionName ?: ""
    } catch (_: Exception) {
        ""
    }
}

/** 查版本文件。网关超时（502/503/504）或网络抖动时自动重试 3 次，不用手动再点。 */
suspend fun fetchUpdateInfo(): Result<UpdateInfo> {
    var lastError: Exception? = null
    repeat(3) { attempt ->
        if (attempt > 0) delay(if (attempt == 1) 1500L else 3000L)
        val r = fetchUpdateInfoOnce()
        r.onSuccess { return r }
        lastError = r.exceptionOrNull() as? Exception
    }
    return Result.failure(lastError ?: Exception("检查更新失败"))
}

private suspend fun fetchUpdateInfoOnce(): Result<UpdateInfo> {
    return try {
        val text = withContext(Dispatchers.IO) {
            val conn = (URL(UPDATE_JSON_URL).openConnection() as HttpURLConnection).apply {
                connectTimeout = 10000
                readTimeout = 15000
                connect()
            }
            if (conn.responseCode !in 200..299) throw Exception("HTTP ${conn.responseCode}")
            conn.inputStream.bufferedReader().readText()
        }
        val json = JSONObject(text)
        val notesJson = json.optJSONObject("notes")
        fun listOf(key: String): List<String> {
            val arr = notesJson?.optJSONArray(key) ?: return emptyList()
            return (0 until arr.length()).mapNotNull { arr.optString(it).takeIf { s -> s.isNotEmpty() } }
        }
        Result.success(
            UpdateInfo(
                versionName = json.getString("versionName"),
                versionCode = json.getInt("versionCode"),
                apkUrl = json.getString("apkUrl"),
                notes = UpdateNotes(listOf("optimize"), listOf("remove"), listOf("add")),
            ),
        )
    } catch (e: Exception) {
        Result.failure(e)
    }
}

class UpdateDownloader {
    private val cancelled = AtomicBoolean(false)

    fun cancel() {
        cancelled.set(true)
    }

    /** 下载 APK 到目标文件，onProgress 回传 0f..1f（总长未知时回传 -1f）。取消时返回失败。
     *  网关超时（502/503/504）或网络超时自动重试 3 次；404 等不重试；取消不重试。 */
    suspend fun download(url: String, dest: File, onProgress: (Float) -> Unit): Result<File> {
        cancelled.set(false)
        var lastError: Exception? = null
        repeat(3) { attempt ->
            if (attempt > 0) delay(if (attempt == 1) 2000L else 4000L)
            val result = downloadOnce(url, dest, onProgress)
            result.onSuccess { return result }
            val e = result.exceptionOrNull() as? Exception ?: return result
            if (e.message == "CANCELLED") return result
            lastError = e
            if (!isRetryable(e)) return result
        }
        return Result.failure(lastError ?: Exception("下载失败"))
    }

    private fun isRetryable(e: Exception): Boolean {
        val m = e.message ?: return true
        if (m.contains("HTTP 502") || m.contains("HTTP 503") || m.contains("HTTP 504")) return true
        if (e is java.net.SocketTimeoutException) return true
        if (m.contains("timeout", ignoreCase = true)) return true
        return false
    }

    private suspend fun downloadOnce(url: String, dest: File, onProgress: (Float) -> Unit): Result<File> {
        return try {
            withContext(Dispatchers.IO) {
                val conn = (URL(url).openConnection() as HttpURLConnection).apply {
                    connectTimeout = 15000
                    readTimeout = 60000
                    connect()
                }
                if (conn.responseCode !in 200..299) throw Exception("下载失败：HTTP ${conn.responseCode}")
                val total = conn.contentLengthLong
                dest.parentFile?.mkdirs()
                var done = 0L
                conn.inputStream.use { input ->
                    FileOutputStream(dest).use { output ->
                        val buffer = ByteArray(64 * 1024)
                        while (true) {
                            if (cancelled.get()) throw Exception("CANCELLED")
                            val n = input.read(buffer)
                            if (n < 0) break
                            output.write(buffer, 0, n)
                            done += n
                            if (total > 0) onProgress((done.toFloat() / total).coerceIn(0f, 1f))
                        }
                    }
                }
                onProgress(1f)
                Result.success(dest)
            }
        } catch (e: Exception) {
            if (e.message == "CANCELLED") {
                dest.delete()
                Result.failure(Exception("CANCELLED"))
            } else {
                Result.failure(e)
            }
        }
    }
}

private const val UPDATE_PREFS = "update_prefs"
private const val PENDING_INSTALL_PATH = "pending_install_path"

/** 是否已获得“安装未知应用”授权（Android 8 以下不需要这项授权）。 */
fun canInstallPackages(context: Context): Boolean =
    Build.VERSION.SDK_INT < Build.VERSION_CODES.O ||
        context.packageManager.canRequestPackageInstalls()

/** 记下已下载、等授权后接着装的 APK 路径（存 SharedPreferences，进程被杀也不丢）。 */
fun setPendingInstall(context: Context, file: File?) {
    context.getSharedPreferences(UPDATE_PREFS, Context.MODE_PRIVATE).edit()
        .apply {
            if (file == null) remove(PENDING_INSTALL_PATH)
            else putString(PENDING_INSTALL_PATH, file.absolutePath)
        }
        .apply()
}

fun getPendingInstall(context: Context): File? =
    context.getSharedPreferences(UPDATE_PREFS, Context.MODE_PRIVATE)
        .getString(PENDING_INSTALL_PATH, null)?.let { File(it) }

/** 调起系统安装器覆盖安装。未给“安装未知应用”权限时先跳系统设置页，回来自动接着装（不用再点一次）。 */
fun installApk(context: Context, file: File): Boolean {
    if (!canInstallPackages(context)) {
        setPendingInstall(context, file)
        val settings = Intent(Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES).apply {
            data = Uri.parse("package:${context.packageName}")
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }
        context.startActivity(settings)
        Toast.makeText(context, "请先允许“安装未知应用”，只开一次，回来自动接着装", Toast.LENGTH_LONG).show()
        return false
    }
    setPendingInstall(context, null)
    val uri = FileProvider.getUriForFile(context, "com.nvzhuang.app.fileprovider", file)
    val intent = Intent(Intent.ACTION_VIEW).apply {
        setDataAndType(uri, "application/vnd.android.package-archive")
        addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_GRANT_READ_URI_PERMISSION)
    }
    context.startActivity(intent)
    return true
}

enum class UpdatePhase { IDLE, DOWNLOADING, DONE, ERROR }

/**
 * lumen 提交按钮：Ludion login-lumen 的 .submit 原生版——全宽 52dp、14dp 圆角、
 * 强调色细边 + 强调色 14% 底，下载中时一道斜光带从左向右扫过玻璃面（Animatable 循环）。
 */
@Composable
fun LumenButton(text: String, enabled: Boolean, sweeping: Boolean, onClick: () -> Unit) {
    val p = LocalPalette.current
    val band = remember { Animatable(0f) }
    LaunchedEffect(sweeping) {
        if (sweeping) {
            while (true) {
                band.animateTo(1.2f, tween(1800, easing = LinearEasing))
                band.snapTo(-0.2f)
            }
        } else band.snapTo(-0.2f)
    }
    Box(
        Modifier.fillMaxWidth().height(52.dp)
            .clip(RoundedRectangle(14.dp))
            .background(p.accent.copy(alpha = 0.14f))
            .border(1.dp, p.accent, RoundedRectangle(14.dp))
            .clickable(enabled = enabled) { onClick() },
        contentAlignment = Alignment.Center,
    ) {
        Canvas(Modifier.fillMaxSize()) {
            val bw = size.width * 0.20f
            val x = band.value * (size.width + bw * 2f) - bw
            val slant = size.height * 0.35f
            val path = Path().apply {
                moveTo(x, -slant)
                lineTo(x + bw, -slant)
                lineTo(x + bw - slant, size.height + slant)
                lineTo(x - slant, size.height + slant)
                close()
            }
            drawPath(path, Color.White.copy(alpha = 0.30f))
        }
        BasicText(
            text,
            style = TextStyle(color = p.text, fontSize = 16.sp, fontWeight = FontWeight.SemiBold),
        )
    }
}

/** lumen 下划线链接按钮（login-lumen 的 .link）：取消下载 / 稍后再说用。 */
@Composable
fun LumenLink(text: String, onClick: () -> Unit) {
    val p = LocalPalette.current
    BasicText(
        text,
        modifier = Modifier.clickable { onClick() }.padding(vertical = 6.dp),
        style = TextStyle(
            color = p.text, fontSize = 14.sp, fontWeight = FontWeight.Medium,
            textDecoration = TextDecoration.Underline,
        ),
    )
}

/**
 * 发现新版本弹窗：Ludion login-lumen 的原生复刻（不塞 HTML、无 WebView；
 * WebGL 的海底都市场景做不到 1:1，取它的神：浅蓝底 + 近白玻璃卡 28dp 大圆角 +
 * 内容逐行浮现 + 玻璃提交按钮上斜扫的光带）。注意 login-lumen 原本是登录页
 * （邮箱/密码表单），这里只取它的视觉语言做更新弹窗，内容仍是更新本身：
 * 版本说明按“优化 / 删除 / 增加”三段写，空段写“无”。下载中不显示进度条，
 * 按钮变成“更新中…”就是下载中的提示；跑满变“已更新 ✓”，1.2 秒后自动调起安装。
 */
@Composable
fun UpdateDialog(info: UpdateInfo, backdrop: com.kyant.backdrop.Backdrop, onClose: () -> Unit) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val p = LocalPalette.current
    val glass = LocalGlassSettings.current
    val dark = p == DarkPalette
    var phase by remember { mutableStateOf(UpdatePhase.IDLE) }
    var error by remember { mutableStateOf("") }
    var apkFile by remember { mutableStateOf<File?>(null) }
    val downloader = remember { UpdateDownloader() }
    var shown by remember { mutableStateOf(false) }
    LaunchedEffect(Unit) { shown = true }

    // 逐行浮现：标题 → 说明 → 按钮，间隔 90ms（login-lumen 的 rise）
    val a0 by animateFloatAsState(if (shown) 1f else 0f, tween(480, delayMillis = 0), label = "rise0")
    val a1 by animateFloatAsState(if (shown) 1f else 0f, tween(480, delayMillis = 90), label = "rise1")
    val a2 by animateFloatAsState(if (shown) 1f else 0f, tween(480, delayMillis = 180), label = "rise2")

    LaunchedEffect(phase) {
        if (phase == UpdatePhase.DONE) {
            delay(1200)
            apkFile?.let { installApk(context, it) }
            onClose()
        }
    }

    fun startDownload() {
        phase = UpdatePhase.DOWNLOADING
        error = ""
        scope.launch {
            val dir = File(context.cacheDir, "updates").apply { if (!exists()) mkdirs() }
            val dest = File(dir, "Shuting-Shiyijian-${info.versionName}.apk")
            val result = downloader.download(info.apkUrl, dest) { }
            result.onSuccess {
                apkFile = it
                phase = UpdatePhase.DONE
            }.onFailure {
                phase = if (it.message == "CANCELLED") UpdatePhase.IDLE else UpdatePhase.ERROR
                if (it.message != "CANCELLED") error = it.message ?: "下载失败"
            }
        }
    }

    fun rise(alpha: Float) = Modifier.graphicsLayer {
        this.alpha = alpha
        translationY = (1f - alpha) * 10.dp.toPx()
    }

    Box(
        Modifier.fillMaxSize().background(
            Brush.verticalGradient(
                if (dark) listOf(Color(0x99000000), Color(0xCC000000))
                else listOf(Color(0xFFE9EEF6).copy(alpha = 0.55f), Color(0xFF10182B).copy(alpha = 0.72f)),
            ),
        ),
        contentAlignment = Alignment.Center,
    ) {
        Column(
            Modifier.fillMaxWidth(0.88f).clip(RoundedRectangle(28.dp))
                .glass(backdrop, glass, p.glass, radiusDp = 28f)
                .padding(horizontal = 20.dp, vertical = 22.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Column(Modifier.fillMaxWidth().then(rise(a0)), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                T("发现新版本 ${info.versionName}", 20, bold = true)
                T("新版本已就绪，更新后继续使用。", 14, p.muted)
            }
            Column(Modifier.fillMaxWidth().then(rise(a1)), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                UpdateNoteSection("优化", info.notes.optimize)
                UpdateNoteSection("删除", info.notes.remove)
                UpdateNoteSection("增加", info.notes.add)
            }
            Column(
                Modifier.fillMaxWidth().then(rise(a2)),
                verticalArrangement = Arrangement.spacedBy(10.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                LumenButton(
                    text = when (phase) {
                        UpdatePhase.IDLE -> "立即更新"
                        UpdatePhase.DOWNLOADING -> "更新中…"
                        UpdatePhase.DONE -> "已更新 ✓"
                        UpdatePhase.ERROR -> "重新下载"
                    },
                    enabled = phase == UpdatePhase.IDLE || phase == UpdatePhase.ERROR,
                    sweeping = phase == UpdatePhase.DOWNLOADING,
                    onClick = { startDownload() },
                )
                if (phase == UpdatePhase.DOWNLOADING) {
                    LumenLink("取消下载") {
                        downloader.cancel()
                        Toast.makeText(context, "已取消更新", Toast.LENGTH_SHORT).show()
                    }
                }
                if (phase == UpdatePhase.ERROR && error.isNotEmpty()) {
                    T(error, 13, Color(0xFFE5484D))
                }
                if (phase == UpdatePhase.IDLE || phase == UpdatePhase.ERROR) {
                    LumenLink("稍后再说", onClose)
                }
            }
        }
    }
}

@Composable
fun UpdateNoteSection(title: String, items: List<String>) {
    val p = LocalPalette.current
    Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
        T(title, 14, bold = true)
        if (items.isEmpty()) {
            T("无", 13, p.muted)
        } else {
            for (item in items) T("· $item", 13, p.muted)
        }
    }
}
