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
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.dp
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

/**
 * 软件更新：按钮与进度复刻 crystal-liquid-ui-kit 的 Update 组件——
 * 白色胶囊 + 蓝色圆标 ↓，点按变宽、液态进度从左向右灌满（渐变流动 + 前缘淡出），
 * 右侧弹出 × 可随时取消；跑满变 Updated ✓，1.2 秒后自动复位（本 App 是 1.2 秒后自动调起安装）。
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
 * 水晶版 Update 胶囊按钮。
 * Idle：152dp 白胶囊，蓝色圆标 ↓ + “Update”；点按 → 变宽到 202dp，
 * 液态进度灌满（蓝渐变流动，前缘淡出），右侧 × 弹出可取消；
 * 跑满 → “Updated” ✓，1.2 秒后复位。
 */
@Composable
fun UpdatePill(
    phase: UpdatePhase,
    progress: Float,
    onClick: () -> Unit,
    onCancel: () -> Unit,
) {
    val p = LocalPalette.current
    val downloading = phase == UpdatePhase.DOWNLOADING
    val width by animateDpAsState(if (downloading) 202.dp else 152.dp, label = "updWidth")
    val cancelScale by animateFloatAsState(if (downloading) 1f else 0f, label = "updCancel")
    // 液态流动：0→1 循环（复刻 kit 里 liquid 1.6s linear infinite 的流动感）
    val shiftAnim = remember { Animatable(0f) }
    LaunchedEffect(Unit) {
        while (true) {
            shiftAnim.animateTo(1f, tween(1600, easing = LinearEasing))
            shiftAnim.snapTo(0f)
        }
    }
    val shift = shiftAnim.value
    Row(verticalAlignment = Alignment.CenterVertically) {
        Box(
            Modifier.width(width).height(54.dp).clip(RoundedCornerShape(999.dp))
                .background(p.card)
                .clickable(enabled = phase == UpdatePhase.IDLE) { onClick() },
            contentAlignment = Alignment.CenterStart,
        ) {
            // 液态进度
            if (downloading || phase == UpdatePhase.DONE) {
                BoxWithConstraints(Modifier.fillMaxSize()) {
                    val wPx = with(LocalDensity.current) { maxWidth.toPx() }
                    val fillW = (maxWidth * if (phase == UpdatePhase.DONE) 1f else progress)
                    Box(
                        Modifier.width(fillW).fillMaxSize()
                            .background(
                                Brush.horizontalGradient(
                                    colors = listOf(Color(0xFF8EC1FF), Color(0xFFD3E6FF), Color(0xFF8EC1FF)),
                                    startX = -wPx + shift * wPx * 2,
                                    endX = shift * wPx * 2,
                                ),
                            ),
                    )
                    // 前缘淡出
                    if (phase != UpdatePhase.DONE) {
                        Box(
                            Modifier.offset(x = fillW - 24.dp).width(24.dp).fillMaxSize()
                                .background(
                                    Brush.horizontalGradient(
                                        listOf(Color(0xFF8EC1FF).copy(alpha = 0.9f), Color.Transparent),
                                    ),
                                ),
                        )
                    }
                }
            }
            Row(
                Modifier.padding(start = 16.dp, end = 22.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                Box(
                    Modifier.size(28.dp).clip(CircleShape).background(BrandGradient),
                    contentAlignment = Alignment.Center,
                ) {
                    T(if (phase == UpdatePhase.DONE) "✓" else "↓", 16, Color.White, bold = true)
                }
                T(
                    when (phase) {
                        UpdatePhase.IDLE -> "更新"
                        UpdatePhase.DOWNLOADING -> "更新中"
                        UpdatePhase.DONE -> "已更新"
                        UpdatePhase.ERROR -> "更新"
                    },
                    19, p.accent, bold = true,
                )
            }
        }
        // × 取消（下载中才出现，-15dp 重叠）
        Box(
            Modifier.offset(x = (-15).dp).size(54.dp)
                .graphicsLayer { scaleX = cancelScale; scaleY = cancelScale; alpha = cancelScale }
                .clip(CircleShape).background(p.card)
                .clickable(enabled = downloading) { onCancel() },
            contentAlignment = Alignment.Center,
        ) { T("×", 25, p.text) }
    }
}

/** 发现新版本弹窗：版本说明按“优化 / 删除 / 增加”三段写，空段写“无”。 */
@Composable
fun UpdateDialog(info: UpdateInfo, backdrop: com.kyant.backdrop.Backdrop, onClose: () -> Unit) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val p = LocalPalette.current
    val glass = LocalGlassSettings.current
    var phase by remember { mutableStateOf(UpdatePhase.IDLE) }
    var progress by remember { mutableFloatStateOf(0f) }
    var error by remember { mutableStateOf("") }
    var apkFile by remember { mutableStateOf<File?>(null) }
    val downloader = remember { UpdateDownloader() }

    // 跑满变 Updated，1.2 秒后自动调起安装（复刻 kit 的复位节奏）
    LaunchedEffect(phase) {
        if (phase == UpdatePhase.DONE) {
            delay(1200)
            apkFile?.let { installApk(context, it) }
            onClose()
        }
    }

    fun startDownload() {
        phase = UpdatePhase.DOWNLOADING
        progress = 0f
        error = ""
        scope.launch {
            val dir = File(context.cacheDir, "updates").apply { if (!exists()) mkdirs() }
            val dest = File(dir, "Shuting-Shiyijian-${info.versionName}.apk")
            val result = downloader.download(info.apkUrl, dest) { p -> progress = p }
            result.onSuccess {
                apkFile = it
                phase = UpdatePhase.DONE
            }.onFailure {
                phase = if (it.message == "CANCELLED") UpdatePhase.IDLE else UpdatePhase.ERROR
                if (it.message != "CANCELLED") error = it.message ?: "下载失败"
                progress = 0f
            }
        }
    }

    Box(
        Modifier.fillMaxSize().background(Color(0x99000000)),
        contentAlignment = Alignment.Center,
    ) {
        Column(
            Modifier.fillMaxWidth(0.88f).clip(RoundedCornerShape(16.dp))
                .glass(backdrop, glass, p.glass, radiusDp = 16f)
                .padding(18.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            T("发现新版本 ${info.versionName}", 18, bold = true)
            Column(
                Modifier.fillMaxWidth(),
                verticalArrangement = Arrangement.spacedBy(6.dp),
            ) {
                UpdateNoteSection("优化", info.notes.optimize)
                UpdateNoteSection("删除", info.notes.remove)
                UpdateNoteSection("增加", info.notes.add)
            }
            UpdatePill(
                phase = phase,
                progress = progress,
                onClick = { startDownload() },
                onCancel = {
                    downloader.cancel()
                    Toast.makeText(context, "已取消更新", Toast.LENGTH_SHORT).show()
                },
            )
            if (phase == UpdatePhase.ERROR && error.isNotEmpty()) {
                T(error, 13, Color(0xFFE5484D))
            }
            if (phase == UpdatePhase.IDLE || phase == UpdatePhase.ERROR) {
                ActionButton("稍后再说", false, onClose, Modifier.fillMaxWidth(), backdrop = backdrop)
            }
            if (phase == UpdatePhase.DOWNLOADING) {
                T("正在下载新版本…点 × 可取消", 12, p.muted)
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
