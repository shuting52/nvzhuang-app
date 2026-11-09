package com.nvzhuang.app

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent { NvzhuangApp() }
    }

    override fun onResume() {
        super.onResume()
        // 从“安装未知应用”设置页回来：已授权就自动接着装下载好的包，不用再点一次更新
        getPendingInstall(this)?.takeIf { it.exists() }?.let { file ->
            if (canInstallPackages(this)) {
                setPendingInstall(this, null)
                installApk(this, file)
            }
        }
    }
}
