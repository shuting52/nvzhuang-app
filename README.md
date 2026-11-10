# 淑婷试衣间

拍片哥女装 COSTUME-DB-01 原生 Android 图库 App（Kotlin + Compose，不塞 HTML、无 WebView）。

- 三图库共 4226 套：写实 1407 / 动漫 1320 / AI 模特 1499，图片在线加载
- AI 写穿搭文案、AI 试穿（Gemini / 豆包 Seedream / OpenAI 兼容，Key 只存手机本地）
- 全界面液态玻璃（boli 同款 Backdrop），软件更新走水晶版 Update 按钮

## 构建

JDK 21 + Android SDK（compileSdk 36），离线构建：

```bash
./gradlew :app:signReleaseV3 --offline --no-daemon
# 产物：app/build/outputs/apk/release/app-release.apk
```

`signReleaseV3` 会在 assembleRelease 之后用 apksigner 显式重签 v1+v2+v3
（AGP 9.3.2 默认只出 v2）。

首次从仓库克隆后需补两样东西（有意不进仓库）：
- 启动图标：`python3 tools/make_icon.py <参考图.jpg>`，参考图是个人照片、不提交
- Gradle wrapper jar：`gradle wrapper` 可重新生成，或直接用已装的 Gradle 9.8.2 跑上面那条命令

## 发新版（更新通道）

1. 改 `app/build.gradle.kts` 的 `versionCode` / `versionName`（每次 +1）
2. 构建出包，`signReleaseV3` 验签（v2、v3 为 true，证书须与旧版同一把）
3. 在 GitHub 发 Release（tag `vX.Y.Z`），把 APK 作为附件上传
4. 更新仓库根目录的 `app-version.json`（versionName / versionCode / apkUrl / notes）
   - notes 按“优化 / 删除 / 增加”三段写，空段写“无”
5. App 启动时自动查 `app-version.json`，有新版就弹更新窗；关于页也可手动检查更新

## 签名

- 签名文件 `debug.keystore`（alias `nvzhuang`）**绝不进仓库**，只备份在本地
- 每次发版必须用同一把 keystore，否则旧版无法覆盖安装

## 数据来源

COSTUME-DB-01（MIT 开源协议），原站 paipiange.github.io/nvzhuang。

提示词库：noahvisualsx.com/zh/prompts（177 条，写进 assets/prompts.json 做成本地数据，配图在线加载）。
