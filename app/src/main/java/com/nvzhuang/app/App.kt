package com.nvzhuang.app

import android.net.Uri
import android.widget.MediaController
import android.widget.Toast
import android.widget.VideoView
import androidx.activity.compose.BackHandler
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.horizontalScroll
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
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.itemsIndexed
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicText
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import coil.compose.AsyncImage
import com.kyant.backdrop.Backdrop
import com.kyant.backdrop.backdrops.layerBackdrop
import com.kyant.backdrop.backdrops.rememberLayerBackdrop
import com.kyant.backdrop.catalog.components.LiquidBottomTab
import com.kyant.backdrop.catalog.components.LiquidBottomTabs
import com.kyant.shapes.RoundedRectangle
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.roundToInt
import kotlin.math.sin
import kotlin.random.Random

// ---------- 通用小组件（主题见 Theme.kt，取自 boli） ----------

@Composable
fun T(text: String, size: Int = 14, color: Color? = null, bold: Boolean = false, modifier: Modifier = Modifier) {
    val p = LocalPalette.current
    BasicText(
        text,
        modifier = modifier,
        style = TextStyle(
            color = color ?: p.text, fontSize = size.sp,
            fontWeight = if (bold) FontWeight.Bold else FontWeight.Normal,
        ),
    )
}

@Composable
fun Chip(text: String, selected: Boolean, backdrop: Backdrop, onClick: () -> Unit) {
    val p = LocalPalette.current
    val glass = LocalGlassSettings.current
    Box(
        Modifier
            .clip(RoundedRectangle(20.dp))
            .glass(backdrop, glass, p.glass, radiusDp = 20f)
            .then(if (selected) Modifier.border(1.dp, p.accent, RoundedRectangle(20.dp)) else Modifier)
            .clickable { onClick() }
            .padding(horizontal = 14.dp, vertical = 8.dp),
        contentAlignment = Alignment.Center,
    ) {
        T(text, 13, if (selected) p.accent else p.text, bold = selected)
    }
}

@Composable
fun ActionButton(text: String, primary: Boolean, onClick: () -> Unit, modifier: Modifier = Modifier, backdrop: Backdrop) {
    val p = LocalPalette.current
    val glass = LocalGlassSettings.current
    Box(
        modifier
            .clip(RoundedRectangle(12.dp))
            .glass(backdrop, glass, p.glass, radiusDp = 12f)
            .then(if (primary) Modifier.border(1.dp, p.accent, RoundedRectangle(12.dp)) else Modifier)
            .clickable { onClick() }
            .padding(vertical = 12.dp),
        contentAlignment = Alignment.Center,
    ) {
        T(text, 14, if (primary) p.accent else p.text, bold = primary)
    }
}

// 原生日夜开关（Ludion toggle-daylight 的原生近似版：WebGL 像素场景进不了 APK）：
// 药丸 + 滑动的太阳/月亮 + 夜晚星星，点一下切换深浅主题。
@Composable
fun DaylightToggle(dark: Boolean, onToggle: () -> Unit) {
    val frac by animateFloatAsState(if (dark) 1f else 0f, tween(220), label = "daylight")
    val sky = if (dark) Color(0xFF1B2146) else Color(0xFFF0E7D4)
    val ring = if (dark) Color(0xFF2A3259) else Color(0xFFD6CDBA)
    Canvas(
        Modifier.width(64.dp).height(32.dp)
            .clip(RoundedCornerShape(50))
            .clickable { onToggle() },
    ) {
        val w = size.width
        val h = size.height
        drawRoundRect(sky, Offset.Zero, Size(w, h), CornerRadius(h / 2, h / 2))
        drawRoundRect(ring, Offset.Zero, Size(w, h), CornerRadius(h / 2, h / 2), style = Stroke(1.dp.toPx()))
        if (dark) {
            val stars = listOf(0.20f to 0.30f, 0.36f to 0.65f, 0.66f to 0.28f, 0.80f to 0.62f)
            for ((fx, fy) in stars) {
                drawCircle(Color.White.copy(alpha = 0.85f), 1.6.dp.toPx(), Offset(fx * w, fy * h))
            }
        }
        // 滑块：左 = 太阳（昼），右 = 月亮（夜）
        val r = 11.dp.toPx()
        val cx = 4.dp.toPx() + r + frac * (w - 8.dp.toPx() - 2 * r)
        val cy = h / 2
        if (dark) {
            drawCircle(Color(0xFFF0E7D4), r, Offset(cx, cy))
            drawCircle(Color(0xFFD6CDBA), 3.dp.toPx(), Offset(cx + 3.dp.toPx(), cy - 2.dp.toPx()))
            drawCircle(Color(0xFFD6CDBA), 2.dp.toPx(), Offset(cx - 3.dp.toPx(), cy + 3.dp.toPx()))
        } else {
            for (i in 0 until 8) {
                val a = (i * 45f) * (PI / 180f)
                val dx = cos(a).toFloat() * (r + 2.dp.toPx())
                val dy = sin(a).toFloat() * (r + 2.dp.toPx())
                val ex = cos(a).toFloat() * (r + 5.dp.toPx())
                val ey = sin(a).toFloat() * (r + 5.dp.toPx())
                drawLine(
                    Color(0xFFEFA02C), Offset(cx + dx, cy + dy), Offset(cx + ex, cy + ey),
                    strokeWidth = 2.dp.toPx(), cap = StrokeCap.Round,
                )
            }
            drawCircle(Color(0xFFEFA02C), r, Offset(cx, cy))
        }
    }
}

// ---------- 主界面 ----------

@Composable
fun NvzhuangApp() {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var repo by remember { mutableStateOf<Repo?>(null) }
    var dark by remember { mutableStateOf(loadDark(context)) }
    var tab by remember { mutableIntStateOf(0) } // 0 图库 1 试穿 2 提示词库 3 关于
    var tryOnGarment by remember { mutableStateOf<Item?>(null) }
    var glassSettings by remember {
        migrateGlassSettings138(context)
        mutableStateOf(loadGlassSettings(context))
    }
    var mode by remember { mutableStateOf(Mode.COSTUME) }
    var cat by remember { mutableStateOf("全部") }
    var sec by remember { mutableStateOf("全部") }
    var query by remember { mutableStateOf("") }
    var shuffled by remember { mutableStateOf(false) }
    var shuffleSeed by remember { mutableIntStateOf(0) }
    var favs by remember { mutableStateOf(loadFavs(context)) }
    var tryOnSettings by remember { mutableStateOf(loadTryOnSettings(context)) }
    var wallpaperId by remember { mutableStateOf(loadWallpaperId(context)) }
    var prompts by remember { mutableStateOf<List<PromptItem>>(emptyList()) }
    var detailList by remember { mutableStateOf<List<Item>>(emptyList()) }
    var detailIndex by remember { mutableIntStateOf(-1) }
    var updateInfo by remember { mutableStateOf<UpdateInfo?>(null) }

    LaunchedEffect(Unit) {
        repo = withContext(Dispatchers.IO) { loadRepo(context) }
        prompts = withContext(Dispatchers.IO) { loadPrompts(context) }
        // 启动时自动查一次更新，有新版就弹更新窗
        fetchUpdateInfo().onSuccess {
            if (it.versionCode > currentVersionCode(context)) updateInfo = it
        }
    }

    val p = if (dark) DarkPalette else LightPalette
    androidx.compose.runtime.CompositionLocalProvider(
        LocalPalette provides p,
        LocalGlassSettings provides glassSettings,
        LocalWallpaperStyle provides wallpaperStyleById(wallpaperId),
    ) {
        val backdrop = rememberLayerBackdrop()
        Box(Modifier.fillMaxSize().background(p.bg)) {
            // boli 壁纸层：玻璃组件采样这层做折射
            Box(Modifier.fillMaxSize().layerBackdrop(backdrop)) {
                Wallpaper()
            }
            if (repo == null) {
                Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    T("正在加载图库数据…", 15, p.muted)
                }
            } else {
                val r = repo!!
                val filtered = remember(mode, cat, sec, query, shuffled, shuffleSeed, r) {
                    val base = r.filter(mode, query, cat, sec)
                    if (shuffled) base.shuffled(Random(shuffleSeed)) else base
                }
                Column(Modifier.fillMaxSize()) {
                    // 顶栏
                    Row(
                        Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 12.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Box(
                            Modifier.size(40.dp).clip(CircleShape).background(BrandGradient),
                            contentAlignment = Alignment.Center,
                        ) { T("淑", 17, Color.White, bold = true) }
                        Spacer(Modifier.width(10.dp))
                        Column(Modifier.weight(1f)) {
                            T("淑婷试衣间", 22, bold = true)
                            T("AI 服装参考图集 · COSTUME-DB-01", 12, p.muted)
                        }
                        DaylightToggle(dark = dark) {
                            dark = !dark
                            saveDark(context, dark)
                        }
                    }
                    when (tab) {
                        0 -> GalleryTab(
                            modifier = Modifier.weight(1f),
                            repo = r, backdrop = backdrop, mode = mode, onMode = { mode = it; cat = "全部"; sec = "全部" },
                            cat = cat, onCat = { cat = it },
                            sec = sec, onSec = { sec = it },
                            query = query, onQuery = { query = it },
                            filtered = filtered,
                            shuffled = shuffled,
                            onShuffle = { shuffled = true; shuffleSeed = Random.nextInt() },
                            onDefaultOrder = { shuffled = false },
                            favs = favs,
                            onOpen = { index ->
                                detailList = filtered
                                detailIndex = index
                            },
                        )
                        1 -> TryOnTab(
                            repo = r, garment = tryOnGarment, backdrop = backdrop,
                            settings = tryOnSettings,
                            onGarmentChange = { tryOnGarment = it },
                            modifier = Modifier.weight(1f),
                        )
                        2 -> FavTab(
                            modifier = Modifier.weight(1f),
                            backdrop = backdrop, prompts = prompts,
                        )
                        3 -> AboutTab(
                            Modifier.weight(1f),
                            backdrop = backdrop,
                            settings = tryOnSettings, onSettingsChange = { tryOnSettings = it },
                            onGlassChange = { glassSettings = it; saveGlassSettings(context, it) },
                            wallpaperId = wallpaperId, onWallpaperChange = { wallpaperId = it; saveWallpaperId(context, it) },
                        )
                    }
                    Spacer(Modifier.height(96.dp))
                }
            }
            // 底部悬浮玻璃胶囊 Tab（boli 同款 LiquidBottomTabs：点按切换，也可长按拖动指示块切换）
            val tabLabels = listOf("图库" to "🖼️", "AI 试穿" to "👗", "提示词库" to "📝", "关于" to "ℹ️")
            LiquidBottomTabs(
                selectedTabIndex = { tab },
                onTabSelected = { tab = it },
                backdrop = backdrop,
                tabsCount = tabLabels.size,
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .navigationBarsPadding()
                    .padding(horizontal = 28.dp, vertical = 12.dp),
            ) {
                tabLabels.forEachIndexed { i, (label, icon) ->
                    LiquidBottomTab({ tab = i }) {
                        BasicText(icon, style = TextStyle(fontSize = 20.sp))
                        BasicText(label, style = TextStyle(p.text, 11.sp))
                    }
                }
            }
            // 详情全屏
            if (detailIndex >= 0 && detailIndex < detailList.size) {
                val item = detailList[detailIndex]
                DetailScreen(
                    item = item,
                    isFav = favs.contains(item.key),
                    onToggleFav = {
                        favs = if (favs.contains(item.key)) favs - item.key else favs + item.key
                        saveFavs(context, favs)
                    },
                    onPrev = { if (detailIndex > 0) detailIndex-- },
                    onNext = { if (detailIndex < detailList.size - 1) detailIndex++ },
                    hasPrev = detailIndex > 0,
                    hasNext = detailIndex < detailList.size - 1,
                    position = "${detailIndex + 1} / ${detailList.size}",
                    onClose = { detailIndex = -1 },
                    onNeedKey = { detailIndex = -1; tab = 3 },
                    onTryOn = { detailIndex = -1; tryOnGarment = it; tab = 1 },
                    onDownload = {
                        scope.launch {
                            val msg = downloadImage(context, item)
                            Toast.makeText(context, msg, Toast.LENGTH_LONG).show()
                        }
                    },
                )
            }
            // 软件更新弹窗（启动自动查到新版时弹出）
            updateInfo?.let { UpdateDialog(it, backdrop) { updateInfo = null } }
        }
    }
}

@Composable
fun GalleryTab(
    modifier: Modifier = Modifier,
    repo: Repo, backdrop: Backdrop, mode: Mode, onMode: (Mode) -> Unit,
    cat: String, onCat: (String) -> Unit,
    sec: String, onSec: (String) -> Unit,
    query: String, onQuery: (String) -> Unit,
    filtered: List<Item>, shuffled: Boolean,
    onShuffle: () -> Unit, onDefaultOrder: () -> Unit,
    favs: Set<String>, onOpen: (Int) -> Unit,
) {
    val p = LocalPalette.current
    val glass = LocalGlassSettings.current
    Column(modifier.fillMaxWidth()) {
        // 模式切换
        Row(Modifier.fillMaxWidth().padding(horizontal = 16.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            for (m in Mode.entries) {
                val total = repo.all(m).size
                Box(
                    Modifier.weight(1f).clip(RoundedRectangle(12.dp))
                        .glass(backdrop, glass, p.glass, radiusDp = 12f)
                        .then(if (mode == m) Modifier.border(1.dp, p.accent, RoundedRectangle(12.dp)) else Modifier)
                        .clickable { onMode(m) }
                        .padding(vertical = 10.dp),
                    contentAlignment = Alignment.Center,
                ) { T("${m.label} $total", 14, if (mode == m) p.accent else p.text, bold = mode == m) }
            }
        }
        Spacer(Modifier.height(10.dp))
        // 搜索框
        Box(
            Modifier.fillMaxWidth().padding(horizontal = 16.dp)
                .clip(RoundedRectangle(12.dp)).glass(backdrop, glass, p.glass, radiusDp = 12f)
                .padding(horizontal = 14.dp, vertical = 11.dp),
        ) {
            if (query.isEmpty()) T("搜 ID 或关键词，如 denim、蕾丝、白…", 14, p.muted)
            BasicTextField(
                value = query, onValueChange = onQuery,
                modifier = Modifier.fillMaxWidth(),
                textStyle = TextStyle(color = p.text, fontSize = 14.sp),
                singleLine = true,
            )
        }
        Spacer(Modifier.height(10.dp))
        // 分类
        Row(
            Modifier.fillMaxWidth().horizontalScroll(rememberScrollState())
                .padding(horizontal = 16.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Chip("全部", cat == "全部", backdrop) { onCat("全部") }
            for (c in catsFor(mode)) Chip(catZh(c), cat == c, backdrop) { onCat(c) }
        }
        if (mode == Mode.PORTRAIT) {
            Spacer(Modifier.height(8.dp))
            Row(
                Modifier.fillMaxWidth().horizontalScroll(rememberScrollState())
                    .padding(horizontal = 16.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                Chip("全部形式", sec == "全部", backdrop) { onSec("全部") }
                Chip("脸部特写", sec == "face", backdrop) { onSec("face") }
                Chip("整体造型", sec == "styling", backdrop) { onSec("styling") }
            }
        }
        Spacer(Modifier.height(10.dp))
        // 计数 + 排序
        Row(
            Modifier.fillMaxWidth().padding(horizontal = 16.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            T("共 ${repo.all(mode).size} 套 · 已显示 ${filtered.size} 套", 13, p.muted, modifier = Modifier.weight(1f))
            T(if (shuffled) "↺ 默认顺序" else "🔀 随机换一批", 13, p.accent, bold = true, modifier = Modifier.clickable {
                if (shuffled) onDefaultOrder() else onShuffle()
            })
        }
        Spacer(Modifier.height(8.dp))
        if (filtered.isEmpty()) {
            Box(Modifier.fillMaxWidth().weight(1f), contentAlignment = Alignment.Center) {
                T("没有找到，换个关键词试试", 14, p.muted)
            }
        } else {
            LazyVerticalGrid(
                columns = GridCells.Fixed(2),
                modifier = Modifier.fillMaxWidth().weight(1f),
                contentPadding = PaddingValues(horizontal = 16.dp, vertical = 4.dp),
                horizontalArrangement = Arrangement.spacedBy(10.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                itemsIndexed(filtered, key = { _, it -> it.key }) { index, item ->
                    GalleryCard(item, favs.contains(item.key), backdrop) { onOpen(index) }
                }
            }
        }
    }
}

@Composable
fun GalleryCard(item: Item, isFav: Boolean, backdrop: Backdrop, onClick: () -> Unit) {
    val p = LocalPalette.current
    val glass = LocalGlassSettings.current
    Column(
        Modifier.clip(RoundedRectangle(glassRadiusDp(glass).dp)).glass(backdrop, glass, p.glass)
            .clickable { onClick() },
    ) {
        Box(Modifier.fillMaxWidth().aspectRatio(2f / 3f).background(p.chipBg)) {
            AsyncImage(
                model = item.thumb, contentDescription = item.id,
                modifier = Modifier.fillMaxSize(), contentScale = ContentScale.Crop,
            )
            if (isFav) {
                Box(
                    Modifier.align(Alignment.TopEnd).padding(6.dp)
                        .clip(CircleShape).background(Color(0xCC000000)).padding(6.dp),
                ) { T("♥", 12, Color(0xFFFF6B81), bold = true) }
            }
        }
        Row(
            Modifier.fillMaxWidth().padding(horizontal = 10.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            T(item.id, 13, bold = true, modifier = Modifier.weight(1f))
            T(if (item.mode == Mode.PORTRAIT) item.secZh else catZh(item.cat), 12, p.muted)
        }
    }
}

@Composable
fun FavTab(modifier: Modifier = Modifier, backdrop: Backdrop, prompts: List<PromptItem>) {
    val p = LocalPalette.current
    val glass = LocalGlassSettings.current
    var promptGroup by remember { mutableStateOf("全部") }
    var promptQuery by remember { mutableStateOf("") }
    var expandedId by remember { mutableStateOf<String?>(null) }
    var playingVideo by remember { mutableStateOf<PromptItem?>(null) }
    val filteredPrompts = remember(prompts, promptGroup, promptQuery) {
        val q = promptQuery.trim()
        prompts.filter { item ->
            (promptGroup == "全部" || item.group == promptGroup) &&
                (q.isEmpty() || item.title.contains(q) || item.prompt.contains(q) || item.tags.any { it.contains(q) })
        }
    }
    Box(modifier.fillMaxWidth()) {
        Column(Modifier.fillMaxSize()) {
            // 提示词库：搜索框
            Box(
                Modifier.fillMaxWidth().padding(horizontal = 16.dp)
                    .clip(RoundedRectangle(12.dp)).glass(backdrop, glass, p.glass, radiusDp = 12f)
                    .padding(horizontal = 14.dp, vertical = 11.dp),
            ) {
                if (promptQuery.isEmpty()) T("搜标题或提示词，如 街拍、贝雷帽、胶片…", 14, p.muted)
                BasicTextField(
                    value = promptQuery, onValueChange = { promptQuery = it },
                    modifier = Modifier.fillMaxWidth(),
                    textStyle = TextStyle(color = p.text, fontSize = 14.sp),
                    singleLine = true,
                )
            }
            Spacer(Modifier.height(10.dp))
            // 分类
            Row(
                Modifier.fillMaxWidth().horizontalScroll(rememberScrollState())
                    .padding(horizontal = 16.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                for (g in listOf("全部", "AI 生图", "AI 视频", "其他")) {
                    Chip(g, promptGroup == g, backdrop) { promptGroup = g }
                }
            }
            Spacer(Modifier.height(10.dp))
            T("共 ${prompts.size} 条 · 已显示 ${filteredPrompts.size} 条", 13, p.muted, modifier = Modifier.padding(horizontal = 16.dp))
            Spacer(Modifier.height(8.dp))
            if (filteredPrompts.isEmpty()) {
                Box(Modifier.fillMaxWidth().weight(1f), contentAlignment = Alignment.Center) {
                    T("没有找到，换个关键词试试", 14, p.muted)
                }
            } else {
                LazyColumn(
                    modifier = Modifier.fillMaxWidth().weight(1f),
                    contentPadding = PaddingValues(horizontal = 16.dp, vertical = 4.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    items(filteredPrompts, key = { it.id }) { item ->
                        PromptCard(item, expanded = expandedId == item.id, backdrop, onToggle = {
                            expandedId = if (expandedId == item.id) null else item.id
                        }, onPlay = { playingVideo = it })
                    }
                }
            }
            T("来源：noahvisualsx.com 提示词库", 12, p.muted, modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp))
        }
        playingVideo?.let { VideoPlayerDialog(it, backdrop) { playingVideo = null } }
    }
}

@Composable
fun PromptCard(item: PromptItem, expanded: Boolean, backdrop: Backdrop, onToggle: () -> Unit, onPlay: (PromptItem) -> Unit = {}) {
    val p = LocalPalette.current
    val glass = LocalGlassSettings.current
    val context = LocalContext.current
    Column(
        Modifier.clip(RoundedRectangle(glassRadiusDp(glass).dp)).glass(backdrop, glass, p.glass)
            .clickable { onToggle() },
    ) {
        if (item.image.isNotEmpty()) {
            Box(
                Modifier.fillMaxWidth().aspectRatio(16f / 9f).background(p.chipBg)
                    .clickable { if (item.video.isNotEmpty()) onPlay(item) },
            ) {
                AsyncImage(
                    model = item.image, contentDescription = item.title,
                    modifier = Modifier.fillMaxSize(), contentScale = ContentScale.Crop,
                )
                if (item.video.isNotEmpty()) {
                    // ▶ 播放角标：点封面就播原站示例视频
                    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                        Box(
                            Modifier.size(52.dp).clip(CircleShape).background(Color(0xCC000000)),
                            contentAlignment = Alignment.Center,
                        ) {
                            T("▶", 20, Color.White, bold = true)
                        }
                    }
                }
            }
        }
        Column(Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 10.dp)) {
            T(item.title, 14, bold = true)
            if (item.note.isNotEmpty()) {
                Spacer(Modifier.height(4.dp))
                T(item.note, 12, p.muted)
            }
            Spacer(Modifier.height(6.dp))
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                T(item.kind, 12, p.accent, bold = true)
                if (item.ratio.isNotEmpty()) T(" · ${item.ratio}", 12, p.muted)
                if (item.tags.isNotEmpty()) T(" · #" + item.tags.joinToString(" #"), 12, p.muted)
            }
            Spacer(Modifier.height(8.dp))
            BasicText(
                item.prompt,
                modifier = Modifier.fillMaxWidth(),
                style = TextStyle(color = p.text, fontSize = 13.sp),
                maxLines = if (expanded) Int.MAX_VALUE else 3,
                overflow = TextOverflow.Ellipsis,
            )
            if (expanded && item.video.isNotEmpty()) {
                Spacer(Modifier.height(10.dp))
                ActionButton("▶ 播放视频", false, { onPlay(item) }, Modifier.fillMaxWidth(), backdrop = backdrop)
            }
            Spacer(Modifier.height(8.dp))
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                T(if (expanded) "收起 ↑" else "展开全文 ↓", 12, p.accent, bold = true)
                ActionButton("复制提示词", true, { copyText(context, item.prompt, "提示词已复制") }, backdrop = backdrop)
            }
        }
    }
}

/** AI 视频提示词的示例视频播放（原站 mp4，在线播放；系统 VideoView，不引新依赖）。 */
@Composable
fun VideoPlayerDialog(item: PromptItem, backdrop: Backdrop, onClose: () -> Unit) {
    val context = LocalContext.current
    val p = LocalPalette.current
    val glass = LocalGlassSettings.current
    val ratio = item.ratio
    // 9:16 竖视频用窄一点的框，不然放不下；横视频用宽框
    val widthFrac = if (ratio == "9:16" || ratio.isEmpty()) 0.72f else 0.92f
    val aspect = when (ratio) {
        "16:9" -> 16f / 9f
        "4:3" -> 4f / 3f
        else -> 9f / 16f
    }
    Box(
        Modifier.fillMaxSize().background(Color(0xCC000000)).clickable { onClose() },
        contentAlignment = Alignment.Center,
    ) {
        Column(
            Modifier.fillMaxWidth(widthFrac).clip(RoundedRectangle(16.dp)).glass(backdrop, glass, p.glass, radiusDp = 16f)
                .padding(14.dp)
                // 卡片自己吃掉点击，别透传到遮罩把弹窗关了（不然点视频调进度时会误关）
                .clickable(
                    interactionSource = remember { MutableInteractionSource() },
                    indication = null,
                ) { },
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            T(item.title, 15, bold = true)
            Box(Modifier.fillMaxWidth().aspectRatio(aspect).clip(RoundedRectangle(10.dp)).background(Color.Black)) {
                AndroidView(
                    factory = { ctx ->
                        VideoView(ctx).apply {
                            setVideoURI(Uri.parse(item.video))
                            setMediaController(MediaController(ctx).also { it.setAnchorView(this) })
                            setOnPreparedListener { mp -> mp.isLooping = true; start() }
                            setOnErrorListener { _, _, _ ->
                                Toast.makeText(ctx, "视频播不了，换个网络试试", Toast.LENGTH_SHORT).show()
                                true
                            }
                        }
                    },
                    modifier = Modifier.fillMaxSize(),
                    onRelease = { it.stopPlayback() },
                )
            }
            T("在线播放 · 来源 noahvisualsx.com，点视频可调进度", 12, p.muted)
            ActionButton("关闭", false, onClose, Modifier.fillMaxWidth(), backdrop = backdrop)
        }
    }
}
@Composable
fun AboutTab(modifier: Modifier = Modifier, backdrop: Backdrop, settings: TryOnSettings, onSettingsChange: (TryOnSettings) -> Unit, onGlassChange: (GlassSettings) -> Unit, wallpaperId: String, onWallpaperChange: (String) -> Unit) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val p = LocalPalette.current
    val glass = LocalGlassSettings.current
    var checking by remember { mutableStateOf(false) }
    var checkMsg by remember { mutableStateOf("") }
    var manualUpdate by remember { mutableStateOf<UpdateInfo?>(null) }
    Box(modifier.fillMaxWidth()) {
        Column(
            Modifier.fillMaxWidth().verticalScroll(rememberScrollState())
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp),
        ) {
        T("关于这个 App", 18, bold = true)
        AboutBlock(
            "AI 试穿",
            "试穿已上线：上传照片、选一件衣服就能生成。在下面填自己的生图模型 Key（Gemini Nano Banana / 豆包 Seedream / GPT Image 兼容）。需要生图模型，纯文字 Key（如 DeepSeek）做不了；Key 只存手机本地。",
            backdrop, glass,
        )
        ActionButton("在浏览器打开原站", true, { openUrl(context, "https://paipiange.github.io/nvzhuang/") }, Modifier.fillMaxWidth(), backdrop = backdrop)
        AiSettingsBlock(backdrop)
        TryOnSettingsSection("AI 试穿模型设置（自定义 Key）", settings, onSettingsChange, backdrop)
        WallpaperSettingsBlock(backdrop, wallpaperId, onWallpaperChange)
        GlassSettingsBlock(backdrop, onGlassChange)
        T("淑婷试衣间 v1.4.6 · 原生 App", 12, p.muted)
        // 软件更新
        Column(
            Modifier.fillMaxWidth().clip(RoundedRectangle(glassRadiusDp(glass).dp)).glass(backdrop, glass, p.glass)
                .padding(14.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            T("软件更新", 15, bold = true)
            ActionButton(if (checking) "检查中…" else "检查更新", false, {
                checking = true
                checkMsg = ""
                scope.launch {
                    val r = fetchUpdateInfo()
                    checking = false
                    r.onSuccess { info ->
                        if (info.versionCode > currentVersionCode(context)) manualUpdate = info
                        else checkMsg = "已是最新版 v${currentVersionName(context)}"
                    }.onFailure { checkMsg = "检查失败：${it.message ?: "网络异常"}" }
                }
            }, Modifier.fillMaxWidth(), backdrop = backdrop)
            if (checkMsg.isNotEmpty()) T(checkMsg, 13, p.muted)
        }
        Spacer(Modifier.height(96.dp))
        }
        manualUpdate?.let { UpdateDialog(it, backdrop) { manualUpdate = null } }
    }
}

@Composable
fun WallpaperSettingsBlock(backdrop: Backdrop, wallpaperId: String, onWallpaperChange: (String) -> Unit) {
    val p = LocalPalette.current
    val glass = LocalGlassSettings.current
    Column(
        Modifier.fillMaxWidth().clip(RoundedRectangle(glassRadiusDp(glass).dp)).glass(backdrop, glass, p.glass)
            .padding(14.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        T("背景", 15, bold = true)
        T("给液态玻璃换个底：全是柔和光晕，没有硬边和亮色块，玻璃折射才看得出来。点一下立即换，全 App 都生效。", 13, p.muted)
        for (row in WALLPAPER_STYLES.chunked(3)) {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                for (style in row) {
                    val selected = style.id == wallpaperId
                    Column(
                        Modifier.weight(1f),
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.spacedBy(4.dp),
                    ) {
                        Box(
                            Modifier.fillMaxWidth().aspectRatio(1.5f)
                                .clip(RoundedRectangle(10.dp))
                                .border(
                                    if (selected) 2.dp else 1.dp,
                                    if (selected) p.accent else p.muted.copy(alpha = 0.4f),
                                    RoundedRectangle(10.dp),
                                )
                                .clickable { onWallpaperChange(style.id) },
                        ) {
                            WallpaperPreview(style, Modifier.fillMaxSize())
                        }
                        T(if (selected) "✓ ${style.name}" else style.name, 12, if (selected) p.accent else p.muted)
                    }
                }
                repeat(3 - row.size) { Spacer(Modifier.weight(1f)) }
            }
        }
    }
}

@Composable
fun GlassSettingsBlock(backdrop: Backdrop, onGlassChange: (GlassSettings) -> Unit) {
    val context = LocalContext.current
    val p = LocalPalette.current
    val glass = LocalGlassSettings.current
    var s by remember { mutableStateOf(loadGlassSettings(context)) }
    // 拖动时只实时预览（不写磁盘），松手再保存，避免每帧写 SharedPreferences
    fun preview(ns: GlassSettings) {
        s = ns
        onGlassChange(ns)
    }
    fun commit() { saveGlassSettings(context, s) }
    Column(
        Modifier.fillMaxWidth().clip(RoundedRectangle(glassRadiusDp(glass).dp)).glass(backdrop, glass, p.glass)
            .padding(14.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        T("液态玻璃设置", 15, bold = true)
        T("调卡片的玻璃效果，和 boli 一套（圆角 / 模糊 / 折射高度 / 折射强度 / 色散）。", 13, p.muted)
        GlassSlider("圆角", s.corner, 0f, 1f, 0.05f, { preview(s.copy(corner = it)) }, { commit() })
        GlassSlider("模糊", s.blurDp, 0f, 12f, 1f, { preview(s.copy(blurDp = it)) }, { commit() })
        GlassSlider("折射高度", s.height, 0f, 1f, 0.05f, { preview(s.copy(height = it)) }, { commit() })
        GlassSlider("折射强度", s.amount, 0f, 1f, 0.05f, { preview(s.copy(amount = it)) }, { commit() })
        GlassSlider("色散", s.chroma, 0f, 1f, 0.1f, { preview(s.copy(chroma = it)) }, { commit() })
    }
}

// 液态玻璃设置的拖动进度条：点轨道跳转、横拖连续调，按原步进吸附，松手保存。
// 不用 material3 Slider（工程没引该依赖，离线编不过），纯 Canvas + 手势手写。
// 1.3.6/1.3.7 的 bug 教训：value 必须用 rememberUpdatedState 包一层再进 pointerInput，
// 否则 pointerInput(Unit) 只在首次组合运行，onDragStart 取到的是第一次的旧值，第二次拖动会跳回去。
@Composable
fun GlassSlider(
    label: String,
    value: Float,
    min: Float,
    max: Float,
    step: Float,
    onChange: (Float) -> Unit,
    onCommit: () -> Unit,
) {
    val p = LocalPalette.current
    val valueState = rememberUpdatedState(value)
    val onChangeState = rememberUpdatedState(onChange)
    val onCommitState = rememberUpdatedState(onCommit)
    var trackW by remember { mutableFloatStateOf(0f) }
    var dragStartValue by remember { mutableFloatStateOf(value) }
    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        T(label, 13, modifier = Modifier.width(76.dp))
        Box(
            Modifier.weight(1f).height(28.dp)
                .onSizeChanged { trackW = it.width.toFloat() }
                .pointerInput(Unit) {
                    detectTapGestures(onTap = { pos ->
                        val w = trackW
                        if (w > 0f) {
                            val ratio = (pos.x / w).coerceIn(0f, 1f)
                            val raw = min + ratio * (max - min)
                            val snapped = ((raw / step).roundToInt() * step).coerceIn(min, max)
                            onChangeState.value(snapped)
                            onCommitState.value()
                        }
                    })
                }
                .pointerInput(Unit) {
                    detectHorizontalDragGestures(
                        onDragStart = { dragStartValue = valueState.value },
                        onDragEnd = { onCommitState.value() },
                        onDragCancel = { onCommitState.value() },
                        onHorizontalDrag = { _, dragAmount ->
                            val w = trackW
                            if (w > 0f) {
                                val delta = dragAmount / w * (max - min)
                                val v = (dragStartValue + delta).coerceIn(min, max)
                                val snapped = ((v / step).roundToInt() * step).coerceIn(min, max)
                                onChangeState.value(snapped)
                            }
                        },
                    )
                },
        ) {
            Canvas(Modifier.fillMaxSize()) {
                val w = size.width
                val h = size.height
                val cy = h / 2
                val trackH = 6.dp.toPx()
                val ratio = ((value - min) / (max - min)).coerceIn(0f, 1f)
                val thumbX = ratio * w
                drawRoundRect(
                    p.chipBg, Offset(0f, cy - trackH / 2), Size(w, trackH),
                    CornerRadius(trackH / 2, trackH / 2),
                )
                if (thumbX > 0f) {
                    drawRoundRect(
                        BrandGradient, Offset(0f, cy - trackH / 2), Size(thumbX, trackH),
                        CornerRadius(trackH / 2, trackH / 2),
                    )
                }
                drawCircle(Color.White, 10.dp.toPx(), Offset(thumbX, cy))
                drawCircle(p.chipBg, 10.dp.toPx(), Offset(thumbX, cy), style = Stroke(2.dp.toPx()))
            }
        }
        Box(Modifier.width(52.dp), contentAlignment = Alignment.CenterEnd) {
            T(String.format("%.2f", value), 13)
        }
    }
}

@Composable
fun AiSettingsBlock(backdrop: Backdrop) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val p = LocalPalette.current
    val glass = LocalGlassSettings.current
    val initial = remember { loadAiSettings(context) }
    var baseUrl by remember { mutableStateOf(initial.baseUrl) }
    var model by remember { mutableStateOf(initial.model) }
    var apiKey by remember { mutableStateOf(initial.apiKey) }
    var status by remember { mutableStateOf("") }
    var testing by remember { mutableStateOf(false) }
    Column(
        Modifier.fillMaxWidth().clip(RoundedRectangle(glassRadiusDp(glass).dp)).glass(backdrop, glass, p.glass)
            .padding(14.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        T("AI 文案设置", 15, bold = true)
        T("用你自己的 OpenAI 兼容接口写穿搭文案（默认 AMD 免费接口 DeepSeek-V4-Flash，纯文字模型，只写文案不出图）。Key 只存在这台手机里，不进安装包、不上传。", 13, p.muted)
        AiField("Base URL", baseUrl, false) { baseUrl = it }
        AiField("模型名", model, false) { model = it }
        AiField("API Key", apiKey, true) { apiKey = it }
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            ActionButton("保存设置", true, {
                saveAiSettings(context, AiSettings(baseUrl, model, apiKey))
                Toast.makeText(context, "已保存", Toast.LENGTH_SHORT).show()
            }, Modifier.weight(1f), backdrop = backdrop)
            ActionButton(if (testing) "测试中…" else "测试连接", false, {
                saveAiSettings(context, AiSettings(baseUrl, model, apiKey))
                testing = true
                status = ""
                scope.launch {
                    val r = testAiConnection(context)
                    testing = false
                    status = r.fold({ "连接成功：$it" }, { "连接失败：${it.message ?: "未知错误"}" })
                }
            }, Modifier.weight(1f), backdrop = backdrop)
        }
        if (status.isNotEmpty()) T(status, 13, p.muted)
    }
}

@Composable
fun AiField(label: String, value: String, password: Boolean, onChange: (String) -> Unit) {
    val p = LocalPalette.current
    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
        T(label, 13, p.muted)
        Box(
            Modifier.fillMaxWidth().clip(RoundedCornerShape(10.dp)).background(p.fieldBg)
                .border(1.dp, p.line, RoundedCornerShape(10.dp))
                .padding(horizontal = 12.dp, vertical = 10.dp),
        ) {
            BasicTextField(
                value = value, onValueChange = onChange,
                modifier = Modifier.fillMaxWidth(),
                textStyle = TextStyle(color = p.text, fontSize = 14.sp),
                singleLine = true,
                visualTransformation = if (password) PasswordVisualTransformation() else VisualTransformation.None,
            )
        }
    }
}

@Composable
fun AboutBlock(title: String, body: String, backdrop: Backdrop, glass: GlassSettings) {
    val p = LocalPalette.current
    Column(
        Modifier.fillMaxWidth().clip(RoundedRectangle(glassRadiusDp(glass).dp)).glass(backdrop, glass, p.glass)
            .padding(14.dp),
        verticalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        T(title, 15, bold = true)
        T(body, 13, p.muted)
    }
}

@Composable
fun DetailScreen(
    item: Item, isFav: Boolean, onToggleFav: () -> Unit,
    onPrev: () -> Unit, onNext: () -> Unit, hasPrev: Boolean, hasNext: Boolean,
    position: String, onClose: () -> Unit, onNeedKey: () -> Unit, onTryOn: (Item) -> Unit, onDownload: () -> Unit,
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val p = LocalPalette.current
    var showAi by remember { mutableStateOf(false) }
    var aiText by remember { mutableStateOf("") }
    var aiLoading by remember { mutableStateOf(false) }
    var aiError by remember { mutableStateOf("") }
    var aiJob by remember { mutableStateOf<Job?>(null) }
    fun runAi() {
        if (loadAiSettings(context).apiKey.isBlank()) {
            Toast.makeText(context, "先在 关于 → AI 文案设置 里填 API Key", Toast.LENGTH_LONG).show()
            onNeedKey()
            return
        }
        aiJob?.cancel()
        aiLoading = true
        aiError = ""
        aiText = ""
        aiJob = scope.launch {
            val result = generateCopy(context, item)
            aiLoading = false
            result.onSuccess { aiText = it }.onFailure { aiError = it.message ?: "生成失败" }
        }
    }
    BackHandler { onClose() }
    val detailBackdrop = rememberLayerBackdrop()
    val glass = LocalGlassSettings.current
    Box(Modifier.fillMaxSize().background(p.bg)) {
        // 详情页自己的壁纸层 + 半透明遮罩（保证文字可读，玻璃仍透出壁纸）
        Box(Modifier.fillMaxSize().layerBackdrop(detailBackdrop)) {
            Wallpaper()
        }
        Box(Modifier.fillMaxSize().background(p.bg.copy(alpha = 0.5f)))
        Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState())) {
            // 顶栏
            Row(
                Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 10.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Box(
                    Modifier.clip(CircleShape)
                        .glass(detailBackdrop, glass, p.glass, radiusDp = 24f)
                        .clickable { onClose() }
                        .padding(horizontal = 14.dp, vertical = 8.dp),
                ) { T("✕ 关闭", 14) }
                Spacer(Modifier.weight(1f))
                T(position, 13, p.muted)
                Spacer(Modifier.width(8.dp))
                Box(
                    Modifier.clip(CircleShape)
                        .glass(detailBackdrop, glass, p.glass, radiusDp = 24f)
                        .then(if (isFav) Modifier.border(1.dp, Color(0xFFFF6B81), CircleShape) else Modifier)
                        .clickable { onToggleFav() }.padding(horizontal = 14.dp, vertical = 8.dp),
                ) { T(if (isFav) "♥ 已收藏" else "♡ 收藏", 14, if (isFav) Color(0xFFFF6B81) else p.text, bold = isFav) }
            }
            // 大图
            Box(
                Modifier.fillMaxWidth().padding(horizontal = 16.dp)
                    .clip(RoundedRectangle(14.dp)).glass(detailBackdrop, glass, p.glass, radiusDp = 14f),
                contentAlignment = Alignment.Center,
            ) {
                AsyncImage(
                    model = item.img, contentDescription = item.id,
                    modifier = Modifier.fillMaxWidth().aspectRatio(2f / 3f),
                    contentScale = ContentScale.Fit,
                )
            }
            Spacer(Modifier.height(12.dp))
            // 信息
            Column(Modifier.fillMaxWidth().padding(horizontal = 16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    T(item.id, 20, bold = true, modifier = Modifier.weight(1f))
                    T(
                        if (item.mode == Mode.PORTRAIT) "${item.hairZh} · ${item.secZh}" else catZh(item.cat),
                        14, p.muted,
                    )
                }
                if (item.displayTags.isNotEmpty()) {
                    Row(
                        Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        for (tag in item.displayTags) {
                            Box(
                                Modifier.clip(RoundedRectangle(20.dp))
                                    .glass(detailBackdrop, glass, p.glass, radiusDp = 20f)
                                    .padding(horizontal = 12.dp, vertical = 6.dp),
                            ) { T(tag, 13) }
                        }
                    }
                }
                T("英文关键词：${item.kw}", 12, p.muted)
                // 操作
                ActionButton("⬇ 下载原图", true, onDownload, Modifier.fillMaxWidth(), backdrop = detailBackdrop)
                ActionButton("👗 试穿这套", false, { onTryOn(item) }, Modifier.fillMaxWidth(), backdrop = detailBackdrop)
                ActionButton("✨ AI 写穿搭文案", false, { showAi = true; runAi() }, Modifier.fillMaxWidth(), backdrop = detailBackdrop)
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    ActionButton("复制关键词", false, {
                        copyText(context, item.displayTags.joinToString(" "), "关键词已复制")
                    }, Modifier.weight(1f), backdrop = detailBackdrop)
                    ActionButton("淘宝搜同款", false, { shopSearch(context, item, "taobao") }, Modifier.weight(1f), backdrop = detailBackdrop)
                    ActionButton("拼多多搜同款", false, { shopSearch(context, item, "pdd") }, Modifier.weight(1f), backdrop = detailBackdrop)
                }
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    ActionButton("‹ 上一套", false, onPrev, Modifier.weight(1f), backdrop = detailBackdrop)
                    ActionButton("下一套 ›", false, onNext, Modifier.weight(1f), backdrop = detailBackdrop)
                }
                if (!hasPrev) T("已经是第一套了", 12, p.muted)
                if (!hasNext) T("已经是最后一套了", 12, p.muted)
                Spacer(Modifier.height(16.dp))
            }
        }
        // AI 文案弹窗
        if (showAi) {
            Box(
                Modifier.fillMaxSize().background(Color(0x99000000))
                    .clickable { showAi = false },
                contentAlignment = Alignment.Center,
            ) {
                Column(
                    Modifier.fillMaxWidth(0.88f).clip(RoundedRectangle(16.dp))
                        .glass(detailBackdrop, glass, p.glass, radiusDp = 16f)
                        .padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp),
                ) {
                    T("AI 穿搭文案 · ${item.id}", 16, bold = true)
                    Box(Modifier.fillMaxWidth().verticalScroll(rememberScrollState())) {
                        when {
                            aiLoading -> T("正在写文案…\n一般十几秒，超过 75 秒会自动停并告诉你原因。", 14, p.muted)
                            aiError.isNotEmpty() -> T(aiError, 13, Color(0xFFE5484D))
                            aiText.isNotEmpty() -> T(aiText, 14)
                            else -> T("", 14)
                        }
                    }
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        if (aiLoading) {
                            ActionButton("取消", false, {
                                aiJob?.cancel()
                                aiJob = null
                                aiLoading = false
                            }, Modifier.weight(1f), backdrop = detailBackdrop)
                        } else {
                            if (aiText.isNotEmpty()) {
                                ActionButton("复制文案", true, {
                                    copyText(context, aiText, "文案已复制")
                                }, Modifier.weight(1f), backdrop = detailBackdrop)
                            }
                            ActionButton("再写一条", false, { runAi() }, Modifier.weight(1f), backdrop = detailBackdrop)
                        }
                        ActionButton("关闭", false, { showAi = false }, Modifier.weight(1f), backdrop = detailBackdrop)
                    }
                }
            }
        }
    }
}
