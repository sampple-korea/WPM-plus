package com.sampple.wifivaultrestore

import android.os.Bundle
import android.view.WindowManager
import androidx.activity.compose.setContent
import androidx.appcompat.app.AppCompatActivity
import com.sampple.wifivaultrestore.ui.WifiVaultApp
import com.sampple.wifivaultrestore.ui.theme.WifiVaultTheme

class MainActivity : AppCompatActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        window.setFlags(WindowManager.LayoutParams.FLAG_SECURE, WindowManager.LayoutParams.FLAG_SECURE)
        setContent {
            WifiVaultTheme {
                WifiVaultApp()
            }
        }
    }
}
