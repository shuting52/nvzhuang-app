package com.nvzhuang.app

import android.content.Context
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import com.kyant.backdrop.Backdrop
import com.kyant.backdrop.drawBackdrop
import com.kyant.backdrop.effects.blur
import com.kyant.backdrop.effects.lens
import com.kyant.backdrop.effects.vibrancy
import com.kyant.backdrop.highlight.Highlight
import com.kyant.shapes.RoundedRectangle

/**
 * 主题取自 boli（shuting52/boli，LandezhaoleGlass）：
 * 浅色 #FDFDFD / 深色 #121212（默认深色），品牌绿 #16A34A → 蓝 #0EA5E9 渐变，
 * 绿→蓝→靛对角渐变壁纸 + 彩点 + 网格，液态玻璃卡片，悬浮玻璃胶囊底栏。
 * 玻璃默认值同 boli：圆角 0.5、模糊 4dp、折射高度 0.2、折射强度 0.25、色散 1.0。
 */

data class Palette(
    val bg: Color, val card: Color, val text: Color, val muted: Color,
    val accent: Color, val chipBg: Color, val line: Color, val fieldBg: Color,
    val glass: Color,
)

val LightPalette = Palette(
    Color(0xFFFDFDFD), Color.White, Color(0xFF0D0D0D), Color(0xFF6C6C6C),
    Color(0xFF0073E5), Color(0xFFE8EEF9), Color(0xFFDCE2EB), Color.White,
    Color.White.copy(alpha = 0.5f),
)
val DarkPalette = Palette(
    Color(0xFF121212), Color(0xFF181818), Color.White, Color(0xB3CACACA),
    Color(0xFF55CFFF), Color(0xFF232A35), Color(0xFF272C34), Color(0xFF1A1E24),
    Color(0xFF121212).copy(alpha = 0.4f),
)
val LocalPalette = staticCompositionLocalOf { LightPalette }
val BrandGradient = Brush.linearGradient(listOf(Color(0xFF16A34A), Color(0xFF0EA5E9)))

/** 玻璃设置（boli GlassSettings，5 条滑杆），存在手机本地。 */
data class GlassSettings(
    val corner: Float = 0.5f,
    val blurDp: Float = 4f,
    val height: Float = 0.2f,
    val amount: Float = 0.25f,
    val chroma: Float = 1f,
)

val LocalGlassSettings = compositionLocalOf { GlassSettings() }

private const val THEME_PREFS = "nvzhuang"

fun loadDark(context: Context): Boolean =
    context.getSharedPreferences(THEME_PREFS, Context.MODE_PRIVATE).getBoolean("dark", true)

fun saveDark(context: Context, dark: Boolean) {
    context.getSharedPreferences(THEME_PREFS, Context.MODE_PRIVATE).edit().putBoolean("dark", dark).apply()
}

fun loadGlassSettings(context: Context): GlassSettings {
    val sp = context.getSharedPreferences(THEME_PREFS, Context.MODE_PRIVATE)
    return GlassSettings(
        corner = sp.getFloat("glass_corner", 0.5f),
        blurDp = sp.getFloat("glass_blur", 4f),
        height = sp.getFloat("glass_height", 0.2f),
        amount = sp.getFloat("glass_amount", 0.25f),
        chroma = sp.getFloat("glass_chroma", 1f),
    )
}

fun saveGlassSettings(context: Context, s: GlassSettings) {
    context.getSharedPreferences(THEME_PREFS, Context.MODE_PRIVATE).edit()
        .putFloat("glass_corner", s.corner)
        .putFloat("glass_blur", s.blurDp)
        .putFloat("glass_height", s.height)
        .putFloat("glass_amount", s.amount)
        .putFloat("glass_chroma", s.chroma)
        .apply()
}

/** boli 壁纸：绿→蓝→靛对角渐变 + 14 个彩点 + 白色网格线。 */
@Composable
fun Wallpaper(modifier: Modifier = Modifier) {
    Canvas(modifier.fillMaxSize()) {
        drawRect(
            Brush.linearGradient(
                listOf(Color(0xFF16A34A), Color(0xFF0EA5E9), Color(0xFF6366F1)),
                Offset.Zero, Offset(size.width, size.height),
            )
        )
        val colors = listOf(
            Color(0xFFFFD60A), Color(0xFFFF375F), Color(0xFF30D158),
            Color(0xFFFFFFFF), Color(0xFFFF9F0A),
        )
        for (i in 0 until 14) {
            val x = ((i * 137) % 100) / 100f * size.width
            val y = ((i * 71) % 100) / 100f * size.height
            drawCircle(colors[i % colors.size].copy(alpha = 0.8f), radius = 30f + (i % 5) * 28f, center = Offset(x, y))
        }
        val step = 48.dp.toPx()
        var gx = 0f
        while (gx < size.width) {
            drawLine(Color.White.copy(alpha = 0.25f), Offset(gx, 0f), Offset(gx, size.height), 2f)
            gx += step
        }
    }
}

/** boli 的玻璃面板写法：vibrancy + blur + lens，圆角/模糊/折射跟玻璃设置走。
 *  surface 用半透明色（如 p.glass）时，壁纸会透过折射进来；用 p.card 则接近实色玻璃。
 *  radiusDp 给定时用它，否则按玻璃设置的圆角换算。 */
fun Modifier.glass(backdrop: Backdrop, settings: GlassSettings, surface: Color, radiusDp: Float? = null) =
    this.drawBackdrop(
        backdrop = backdrop,
        shape = { RoundedRectangle((radiusDp ?: (8f + settings.corner * 40f)).dp) },
        effects = {
            vibrancy()
            blur(settings.blurDp.dp.toPx())
            val m = size.minDimension
            lens(
                settings.height * m * 0.5f, settings.amount * m,
                chromaticAberration = settings.chroma > 0.5f,
            )
        },
        highlight = { Highlight.Plain },
        onDrawSurface = { drawRect(surface) },
    )
