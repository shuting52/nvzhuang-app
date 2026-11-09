package com.nvzhuang.app

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.RoundRect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.unit.dp
import androidx.compose.ui.util.lerp
import kotlin.math.PI
import kotlin.math.sin

/**
 * 磁性流体更新下载进度条——Ludion progress-ferro 的原生 Compose 复刻（不塞 HTML、无 WebView，
 * WebGL 光线追踪做不到 1:1，只能取它的神：横长玻璃管 + 镜面黑流体 + 前缘尖刺冠 + 跑满收拢成球）。
 *
 * 画法：一根圆角玻璃管（细描边 + 上沿高光），黑流体从左往右灌，前缘一个圆头 blob，
 * blob 后方 4 根小尖刺随时间起伏晃动（越靠前越高）；progress 到 1 后流体向中间收拢成一个球，
 * 尖刺随之消失。深色主题下流体改浅灰 + 加强光泽，保证在深底上看得见。
 * progress 由 UpdateDownloader 的真实下载进度驱动（0f..1f），不是演示动画。
 */
@Composable
fun FerroProgress(
    progress: Float,
    done: Boolean,
    modifier: Modifier = Modifier,
) {
    val p = LocalPalette.current
    val dark = p == DarkPalette
    val fluid = if (dark) Color(0xFF33333D) else Color(0xFF0D0D10)
    val fluidGloss = Color.White.copy(alpha = if (dark) 0.28f else 0.20f)
    val tubeStroke = if (dark) Color.White.copy(alpha = 0.32f) else Color.Black.copy(alpha = 0.25f)
    val tubeFill = if (dark) Color.White.copy(alpha = 0.05f) else Color.Black.copy(alpha = 0.04f)

    // 跑满后收拢成球
    val gather by animateFloatAsState(
        targetValue = if (done) 1f else 0f,
        animationSpec = tween(650),
        label = "ferroGather",
    )
    // 尖刺晃动：时间 0→2π 循环（Animatable 循环，复刻 liquid 流动感）
    val wobble = remember { Animatable(0f) }
    LaunchedEffect(Unit) {
        while (true) {
            wobble.animateTo((2 * PI).toFloat(), tween(2200, easing = LinearEasing))
            wobble.snapTo(0f)
        }
    }
    val time = wobble.value

    Column(modifier, verticalArrangement = Arrangement.spacedBy(6.dp)) {
        Canvas(Modifier.fillMaxWidth().height(30.dp)) {
            val w = size.width
            val h = size.height
            val r = h / 2f
            val strokeW = 1.dp.toPx()
            // 玻璃管
            drawRoundRect(tubeFill, Offset.Zero, Size(w, h), CornerRadius(r, r))
            drawRoundRect(
                tubeStroke, Offset.Zero, Size(w, h), CornerRadius(r, r),
                style = Stroke(strokeW),
            )
            // 管子上沿高光
            drawLine(
                Color.White.copy(alpha = 0.35f),
                Offset(r, 2.5.dp.toPx()),
                Offset(w - r, 2.5.dp.toPx()),
                1.5.dp.toPx(), StrokeCap.Round,
            )
            // 流体左右缘：收拢时向中间合
            val ballR = h * 0.78f
            val fillX = progress.coerceIn(0f, 1f) * w
            val left = lerp(0f, w / 2f - ballR, gather)
            val right = lerp(fillX, w / 2f + ballR, gather)
            if (right - left > 1f) {
                // 尖刺：前缘后方 4 根，高度随时间晃动，越靠前越高；收拢时消失
                if (gather < 1f) {
                    for (i in 0..3) {
                        val sx = right - i * h * 0.42f - h * 0.15f
                        if (sx < left + 2f) break
                        val env = 1f - i * 0.18f
                        val wob = 0.55f + 0.45f * sin(time + i * 1.3f)
                        val sh = h * 0.30f * env * wob * (1f - gather)
                        if (sh < 1f) continue
                        val spikeW = h * 0.16f
                        val spike = Path().apply {
                            moveTo(sx - spikeW, h * 0.32f)
                            lineTo(sx, h * 0.32f - sh)
                            lineTo(sx + spikeW, h * 0.32f)
                            close()
                        }
                        drawPath(spike, fluid)
                    }
                }
                // 流体主体：圆角矩形 + 前缘圆头
                drawPath(
                    Path().apply {
                        addRoundRect(RoundRect(left, 0f, right, h, r, r))
                    },
                    fluid,
                )
                if (gather < 1f) {
                    drawCircle(fluid, radius = h * 0.56f, center = Offset(right, h / 2f))
                }
                // 流体上沿光泽
                drawLine(
                    fluidGloss,
                    Offset(left + r * 0.5f, h * 0.30f),
                    Offset(right - r * 0.3f, h * 0.30f),
                    2.dp.toPx(), StrokeCap.Round,
                )
            }
        }
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            T(if (done) "下载完成" else "下载中", 13, p.muted)
            T("${(progress.coerceIn(0f, 1f) * 100).toInt()}%", 15, p.text, bold = true)
        }
    }
}
