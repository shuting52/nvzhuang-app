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
 * 主题取自 Ludion toggle-daylight（ludion.ai/p/toggle-daylight，MIT）：
 * 浅色（昼）暖纸 #F5ECD9 / 深色（夜）藏青 #080A2D（默认深色），品牌琥珀 #FBAB3B → #EFA02C 渐变，
 * 极光壁纸（深底 + 柔和光晕，无网格），液态玻璃卡片，悬浮玻璃胶囊底栏。
 * 5 个 token × 4 档 OKLab 明度 = 20 色，见 toggle-daylight 原代码。
 * 玻璃默认值同 boli：圆角 0.5、模糊 4dp、折射高度 0.2、折射强度 0.25、色散 1.0。
 */

data class Palette(
    val bg: Color, val card: Color, val text: Color, val muted: Color,
    val accent: Color, val chipBg: Color, val line: Color, val fieldBg: Color,
    val glass: Color,
)

val LightPalette = Palette(
    Color(0xFFF5ECD9), Color(0xFFFFFAEE), Color(0xFF1B2146), Color(0xFF7F97B3),
    Color(0xFFEFA02C), Color(0xFFF0E7D4), Color(0xFFD6CDBA), Color(0xFFFFFAEE),
    Color.White.copy(alpha = 0.5f),
)
val DarkPalette = Palette(
    Color(0xFF080A2D), Color(0xFF181D42), Color(0xFFF0E7D4), Color(0xFF95ADCA),
    Color(0xFFFBAB3B), Color(0xFF2A3259), Color(0xFF2A3259), Color(0xFF181D42),
    Color(0xFF181D42).copy(alpha = 0.4f),
)
val LocalPalette = staticCompositionLocalOf { LightPalette }
val BrandGradient = Brush.linearGradient(listOf(Color(0xFFFBAB3B), Color(0xFFEFA02C)))

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

/** 极光壁纸：暖纸底（昼）/ 藏青底（夜）+ 大面积柔和径向光晕，没有网格、没有硬边圆点，给液态玻璃做折射底。
 *  玻璃要“透”，底就不能是平铺的亮色：柔和光晕，模糊和折射才看得出来。 */
@Composable
fun Wallpaper(modifier: Modifier = Modifier) {
    val dark = LocalPalette.current == DarkPalette
    Canvas(modifier.fillMaxSize()) {
        drawRect(
            Brush.verticalGradient(
                if (dark) listOf(Color(0xFF080A2D), Color(0xFF0C1238), Color(0xFF080A2D))
                else listOf(Color(0xFFF5ECD9), Color(0xFFF0E7D4), Color(0xFFFFFAEE)),
            )
        )
        // 每团光晕：中心色 → 透明，径向渐变天然柔和
        val blobs = listOf(
            Blob(0.18f, 0.16f, Color(0xFF7C5CFF), if (dark) 0.55f else 0.45f, 0.62f),
            Blob(0.86f, 0.32f, Color(0xFF22D3EE), if (dark) 0.45f else 0.40f, 0.55f),
            Blob(0.12f, 0.78f, Color(0xFF34D399), if (dark) 0.42f else 0.38f, 0.55f),
            Blob(0.84f, 0.86f, Color(0xFFFB7185), if (dark) 0.36f else 0.32f, 0.50f),
            Blob(0.52f, 0.52f, Color(0xFFFBBF24), if (dark) 0.26f else 0.22f, 0.38f),
        )
        for (b in blobs) {
            val center = Offset(b.x * size.width, b.y * size.height)
            val radius = b.r * size.maxDimension
            drawCircle(
                Brush.radialGradient(
                    listOf(b.color.copy(alpha = b.alpha), Color.Transparent),
                    center, radius,
                ),
                radius = radius, center = center,
            )
        }
    }
}

private data class Blob(val x: Float, val y: Float, val color: Color, val alpha: Float, val r: Float)

/** 玻璃圆角（dp）：glass() 的 shape 和外层 clip 必须用同一值。
 *  之前 About/设置类卡片 clip 写死 12dp、glass() 默认按 8+圆角*40 算，
 *  两者不一致时四角会出现壁纸透进来的缺角（圆角 0.2 时玻璃 16dp、clip 12dp）。 */
fun glassRadiusDp(settings: GlassSettings, radiusDp: Float? = null): Float =
    radiusDp ?: (8f + settings.corner * 40f)

/** boli 的玻璃面板写法：vibrancy + blur + lens，圆角/模糊/折射跟玻璃设置走。
 *  surface 用半透明色（如 p.glass）时，壁纸会透过折射进来；用 p.card 则接近实色玻璃。
 *  radiusDp 给定时用它，否则按玻璃设置的圆角换算。 */
fun Modifier.glass(backdrop: Backdrop, settings: GlassSettings, surface: Color, radiusDp: Float? = null) =
    this.drawBackdrop(
        backdrop = backdrop,
        shape = { RoundedRectangle(glassRadiusDp(settings, radiusDp).dp) },
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
