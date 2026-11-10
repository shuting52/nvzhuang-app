plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.compose)
}

android {
    namespace = "com.nvzhuang.app"
    compileSdk = 36
    buildToolsVersion = "36.0.0"

    defaultConfig {
        applicationId = "com.nvzhuang.app"
        minSdk = 26
        targetSdk = 36
        versionCode = 18
        versionName = "1.4.4"
    }

    signingConfigs {
        create("nvzhuang") {
            storeFile = file("../debug.keystore")
            storePassword = "android"
            keyAlias = "nvzhuang"
            keyPassword = "android"
            // 覆盖安装的前提：签名方案 v1+v2+v3 全开，且以后每个版本都用同一把 keystore
            // （AGP 9.3.2 默认只出 v2，v3 由下面的 signReleaseV3 任务重签补上；v1 经 jarsigner 核对有效）
            isV1SigningEnabled = true
            isV2SigningEnabled = true
        }
    }

    buildTypes {
        release {
            isMinifyEnabled = false
            signingConfig = signingConfigs.getByName("nvzhuang")
        }
        debug {
            signingConfig = signingConfigs.getByName("nvzhuang")
        }
    }
    buildFeatures { compose = true }
    // 发布包不跑 lint（离线环境无 lint-gradle 缓存，debug 包本来也不跑）
    lint { checkReleaseBuilds = false }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_11
        targetCompatibility = JavaVersion.VERSION_11
    }
}

// AGP 9.3.2 不再暴露 v3 签名开关，默认只出 v2；覆盖安装要求 v1+v2+v3 全开，
// 所以发布包打完后用 apksigner 显式重签一次（同一把 keystore，证书不变）。
tasks.register<Exec>("signReleaseV3") {
    dependsOn("assembleRelease")
    val sdkDir = File(System.getenv("ANDROID_HOME") ?: error("ANDROID_HOME 未设置"))
    val apksigner = File(sdkDir, "build-tools/36.0.0/apksigner")
    val apk = layout.buildDirectory.file("outputs/apk/release/app-release.apk")
    val tmp = layout.buildDirectory.file("outputs/apk/release/app-release-v3.apk")
    val keystore = rootProject.file("debug.keystore")
    environment("PATH", System.getProperty("java.home") + "/bin:" + System.getenv("PATH"))
    commandLine(
        apksigner.absolutePath, "sign",
        "--ks", keystore.absolutePath,
        "--ks-pass", "pass:android",
        "--key-pass", "pass:android",
        "--v1-signing-enabled", "true",
        "--v2-signing-enabled", "true",
        "--v3-signing-enabled", "true",
        "--out", tmp.get().asFile.absolutePath,
        apk.get().asFile.absolutePath,
    )
    doLast {
        tmp.get().asFile.copyTo(apk.get().asFile, overwrite = true)
        tmp.get().asFile.delete()
    }
}

dependencies {
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.activity.compose)
    implementation(libs.compose.foundation)
    implementation(libs.compose.ui)
    implementation(libs.compose.ui.graphics)
    implementation(libs.coil.compose)
    implementation(libs.kotlinx.coroutines.android)
    implementation(libs.kyant.backdrop)
    implementation(libs.kyant.shapes)
}
