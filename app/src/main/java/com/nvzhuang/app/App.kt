package com.nvzhuang.app

import android.widget.Toast
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
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
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.itemsIndexed
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicText
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
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
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import com.kyant.backdrop.Backdrop
import com.kyant.backdrop.backdrops.layerBackdrop
import com.kyant.backdrop.backdrops.rememberLayerBackdrop
import com.kyant.backdrop.catalog.components.LiquidBottomTab
import com.kyant.backdrop.catalog.components.LiquidBottomTabs
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
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
            .clip(RoundedCornerShape(20.dp))
            .then(
                if (selected) Modifier.background(BrandGradient)
                else Modifier.glass(backdrop, glass, p.glass, radiusDp = 20f),
            )
            .clickable { onClick() }
            .padding(horizontal = 14.dp, vertical = 8.dp),
        contentAlignment = Alignment.Center,
    ) {
        T(text, 13, if (selected) Color.White else p.text, bold = selected)
    }
}

@Composable
fun ActionButton(text: String, primary: Boolean, onClick: () -> Unit, modifier: Modifier = Modifier, backdrop: Backdrop? = null) {
    val p = LocalPalette.current
    val glass = LocalGlassSettings.current
    Box(
        modifier
            .clip(RoundedCornerShape(12.dp))
            .then(
                when {
                    primary -> Modifier.background(BrandGradient)
                    backdrop != null -> Modifier.glass(backdrop, glass, p.glass, radiusDp = 12f)
                    else -> Modifier.background(p.chipBg)
                },
            )
            .clickable { onClick() }
            .padding(vertical = 12.dp),
        contentAlignment = Alignment.Center,
    ) {
        T(text, 14, if (primary) Color.White else p.text, bold = primary)
    }
}

// ---------- 主界面 ----------

@Composable
fun NvzhuangApp() {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var repo by remember { mutableStateOf<Repo?>(null) }
    var dark by remember { mutableStateOf(loadDark(context)) }
    var tab by remember { mutableIntStateOf(0) } // 0 图库 1 试穿 2 收藏 3 关于
    var tryOnGarment by remember { mutableStateOf<Item?>(null) }
    var glassSettings by remember { mutableStateOf(loadGlassSettings(context)) }
    var mode by remember { mutableStateOf(Mode.COSTUME) }
    var cat by remember { mutableStateOf("全部") }
    var sec by remember { mutableStateOf("全部") }
    var query by remember { mutableStateOf("") }
    var shuffled by remember { mutableStateOf(false) }
    var shuffleSeed by remember { mutableIntStateOf(0) }
    var favs by remember { mutableStateOf(loadFavs(context)) }
    var detailList by remember { mutableStateOf<List<Item>>(emptyList()) }
    var detailIndex by remember { mutableIntStateOf(-1) }
    var updateInfo by remember { mutableStateOf<UpdateInfo?>(null) }

    LaunchedEffect(Unit) {
        repo = withContext(Dispatchers.IO) { loadRepo(context) }
        // 启动时自动查一次更新，有新版就弹更新窗
        fetchUpdateInfo().onSuccess {
            if (it.versionCode > currentVersionCode(context)) updateInfo = it
        }
    }

    val p = if (dark) DarkPalette else LightPalette
    androidx.compose.runtime.CompositionLocalProvider(
        LocalPalette provides p,
        LocalGlassSettings provides glassSettings,
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
                        Box(
                            Modifier.clip(CircleShape)
                                .glass(backdrop, glassSettings, p.glass, radiusDp = 24f)
                                .clickable {
                                    dark = !dark
                                    saveDark(context, dark)
                                }.padding(horizontal = 12.dp, vertical = 8.dp),
                        ) { T(if (dark) "☀️ 浅色" else "🌙 深色", 13) }
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
                            onGarmentChange = { tryOnGarment = it },
                            modifier = Modifier.weight(1f),
                        )
                        2 -> FavTab(
                            modifier = Modifier.weight(1f),
                            repo = r, favs = favs, backdrop = backdrop,
                            onOpen = { list, index -> detailList = list; detailIndex = index },
                        )
                        3 -> AboutTab(
                            Modifier.weight(1f),
                            backdrop = backdrop,
                            onGlassChange = { glassSettings = it; saveGlassSettings(context, it) },
                        )
                    }
                    Spacer(Modifier.height(96.dp))
                }
            }
            // 底部悬浮玻璃胶囊 Tab（boli 同款 LiquidBottomTabs：点按切换，也可长按拖动指示块切换）
            val tabLabels = listOf("图库" to "🖼️", "AI 试穿" to "👗", "收藏" to "♥️", "关于" to "ℹ️")
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
                    Modifier.weight(1f).clip(RoundedCornerShape(12.dp))
                        .then(
                            if (mode == m) Modifier.background(BrandGradient)
                            else Modifier.glass(backdrop, glass, p.glass, radiusDp = 12f),
                        )
                        .clickable { onMode(m) }
                        .padding(vertical = 10.dp),
                    contentAlignment = Alignment.Center,
                ) { T("${m.label} $total", 14, if (mode == m) Color.White else p.text, bold = mode == m) }
            }
        }
        Spacer(Modifier.height(10.dp))
        // 搜索框
        Box(
            Modifier.fillMaxWidth().padding(horizontal = 16.dp)
                .clip(RoundedCornerShape(12.dp)).glass(backdrop, glass, p.glass, radiusDp = 12f)
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
        Modifier.clip(RoundedCornerShape(12.dp)).glass(backdrop, glass, p.glass)
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
fun FavTab(modifier: Modifier = Modifier, repo: Repo, backdrop: Backdrop, favs: Set<String>, onOpen: (List<Item>, Int) -> Unit) {
    val p = LocalPalette.current
    val list = remember(favs, repo) { favs.mapNotNull { repo.byKey(it) } }
    Column(modifier.fillMaxWidth()) {
        T("我的收藏（${list.size}）", 16, bold = true, modifier = Modifier.padding(16.dp))
        if (list.isEmpty()) {
            Box(Modifier.fillMaxWidth().weight(1f), contentAlignment = Alignment.Center) {
                T("还没有收藏，在图库点开一套，点 ♥ 收藏", 14, p.muted)
            }
        } else {
            LazyVerticalGrid(
                columns = GridCells.Fixed(2),
                modifier = Modifier.fillMaxWidth().weight(1f),
                contentPadding = PaddingValues(horizontal = 16.dp, vertical = 4.dp),
                horizontalArrangement = Arrangement.spacedBy(10.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                itemsIndexed(list, key = { _, it -> it.key }) { index, item ->
                    GalleryCard(item, true, backdrop) { onOpen(list, index) }
                }
            }
        }
    }
}

@Composable
fun AboutTab(modifier: Modifier = Modifier, backdrop: Backdrop, onGlassChange: (GlassSettings) -> Unit) {
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
            "内容说明",
            "全部图像均为 AI 生成的虚构服装，并非真实商品照片。写实/动漫版是“没有人物的纯服装图像”（2:3）；AI 模特库是 AI 生成的虚拟模特脸部与造型参考。\n请勿用于表现真实人物或未成年人的用途。",
            backdrop, glass,
        )
        AboutBlock(
            "图库规模",
            "写实服装 1,407 套（春 215 / 夏 254 / 秋 244 / 冬 253 / 内衣 199 / 派对 48 / 泳装 54 / 性感 140）\n动漫服装 1,320 套\nAI 模特 1,499 位（脸部特写 893 / 整体造型 606）\n图片在线加载自原站，不占手机空间；没网时看不了图。",
            backdrop, glass,
        )
        AboutBlock(
            "下载说明",
            "实测发现：原站写实版“原尺寸 PNG”链接目前全部 404（打不开），所以本 App 写实版下载的是可用的高清 webp 原图；动漫版、AI 模特下载的是外站原尺寸 PNG。下载的图片保存在相册 Pictures/淑婷试衣间。",
            backdrop, glass,
        )
        AboutBlock(
            "AI 试穿",
            "试穿已上线：底部“AI 试穿”Tab 里填自己的生图模型 Key（Gemini Nano Banana / 豆包 Seedream / GPT Image 兼容），上传照片、选一件衣服就能生成。需要生图模型，纯文字 Key（如 DeepSeek）做不了；Key 只存手机本地。",
            backdrop, glass,
        )
        ActionButton("在浏览器打开原站", true, { openUrl(context, "https://paipiange.github.io/nvzhuang/") }, Modifier.fillMaxWidth())
        AiSettingsBlock(backdrop)
        GlassSettingsBlock(backdrop, onGlassChange)
        T("淑婷试衣间 v1.3.3 · 原生 App", 12, p.muted)
        // 软件更新
        Column(
            Modifier.fillMaxWidth().clip(RoundedCornerShape(12.dp)).glass(backdrop, glass, p.glass)
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
fun GlassSettingsBlock(backdrop: Backdrop, onGlassChange: (GlassSettings) -> Unit) {
    val context = LocalContext.current
    val p = LocalPalette.current
    val glass = LocalGlassSettings.current
    var s by remember { mutableStateOf(loadGlassSettings(context)) }
    fun update(ns: GlassSettings) {
        s = ns
        saveGlassSettings(context, ns)
        onGlassChange(ns)
    }
    Column(
        Modifier.fillMaxWidth().clip(RoundedCornerShape(12.dp)).glass(backdrop, glass, p.glass)
            .padding(14.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        T("液态玻璃设置", 15, bold = true)
        T("调卡片的玻璃效果，和 boli 一套（圆角 / 模糊 / 折射高度 / 折射强度 / 色散）。", 13, p.muted)
        GlassStepper("圆角", s.corner, 0f, 1f, 0.05f) { update(s.copy(corner = it)) }
        GlassStepper("模糊", s.blurDp, 0f, 12f, 1f) { update(s.copy(blurDp = it)) }
        GlassStepper("折射高度", s.height, 0f, 1f, 0.05f) { update(s.copy(height = it)) }
        GlassStepper("折射强度", s.amount, 0f, 1f, 0.05f) { update(s.copy(amount = it)) }
        GlassStepper("色散", s.chroma, 0f, 1f, 0.1f) { update(s.copy(chroma = it)) }
    }
}

@Composable
fun GlassStepper(label: String, value: Float, min: Float, max: Float, step: Float, onChange: (Float) -> Unit) {
    val p = LocalPalette.current
    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        T(label, 13, modifier = Modifier.width(76.dp))
        Box(
            Modifier.clip(CircleShape).background(p.chipBg)
                .clickable { onChange((value - step).coerceIn(min, max)) }
                .padding(horizontal = 14.dp, vertical = 6.dp),
        ) { T("−", 14, bold = true) }
        Box(Modifier.weight(1f), contentAlignment = Alignment.Center) {
            T(String.format("%.2f", value), 13)
        }
        Box(
            Modifier.clip(CircleShape).background(p.chipBg)
                .clickable { onChange((value + step).coerceIn(min, max)) }
                .padding(horizontal = 14.dp, vertical = 6.dp),
        ) { T("+", 14, bold = true) }
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
        Modifier.fillMaxWidth().clip(RoundedCornerShape(12.dp)).glass(backdrop, glass, p.glass)
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
            }, Modifier.weight(1f))
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
        Modifier.fillMaxWidth().clip(RoundedCornerShape(12.dp)).glass(backdrop, glass, p.glass)
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
    fun runAi() {
        if (loadAiSettings(context).apiKey.isBlank()) {
            Toast.makeText(context, "先在 关于 → AI 文案设置 里填 API Key", Toast.LENGTH_LONG).show()
            onNeedKey()
            return
        }
        aiLoading = true
        aiError = ""
        aiText = ""
        scope.launch {
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
                        .then(
                            if (isFav) Modifier.background(Color(0xFFFF6B81))
                            else Modifier.glass(detailBackdrop, glass, p.glass, radiusDp = 24f),
                        )
                        .clickable { onToggleFav() }.padding(horizontal = 14.dp, vertical = 8.dp),
                ) { T(if (isFav) "♥ 已收藏" else "♡ 收藏", 14, if (isFav) Color.White else p.text, bold = isFav) }
            }
            // 大图
            Box(
                Modifier.fillMaxWidth().padding(horizontal = 16.dp)
                    .clip(RoundedCornerShape(14.dp)).glass(detailBackdrop, glass, p.glass, radiusDp = 14f),
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
                                Modifier.clip(RoundedCornerShape(20.dp))
                                    .glass(detailBackdrop, glass, p.glass, radiusDp = 20f)
                                    .padding(horizontal = 12.dp, vertical = 6.dp),
                            ) { T(tag, 13) }
                        }
                    }
                }
                T("英文关键词：${item.kw}", 12, p.muted)
                // 操作
                ActionButton("⬇ 下载原图", true, onDownload, Modifier.fillMaxWidth())
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
                    Modifier.fillMaxWidth(0.88f).clip(RoundedCornerShape(16.dp))
                        .glass(detailBackdrop, glass, p.glass, radiusDp = 16f)
                        .padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp),
                ) {
                    T("AI 穿搭文案 · ${item.id}", 16, bold = true)
                    Box(Modifier.fillMaxWidth().verticalScroll(rememberScrollState())) {
                        when {
                            aiLoading -> T("正在写文案…", 14, p.muted)
                            aiError.isNotEmpty() -> T(aiError, 13, Color(0xFFE5484D))
                            aiText.isNotEmpty() -> T(aiText, 14)
                            else -> T("", 14)
                        }
                    }
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        if (aiText.isNotEmpty()) {
                            ActionButton("复制文案", true, {
                                copyText(context, aiText, "文案已复制")
                            }, Modifier.weight(1f))
                        }
                        ActionButton("再写一条", false, { runAi() }, Modifier.weight(1f), backdrop = detailBackdrop)
                        ActionButton("关闭", false, { showAi = false }, Modifier.weight(1f), backdrop = detailBackdrop)
                    }
                }
            }
        }
    }
}
