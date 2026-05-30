package com.sampple.wifivaultrestore

import android.os.Bundle
import androidx.activity.compose.setContent
import androidx.appcompat.app.AppCompatActivity
import com.sampple.wifivaultrestore.ui.WifiVaultApp
import com.sampple.wifivaultrestore.ui.theme.WifiVaultTheme

class MainActivity : AppCompatActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            WifiVaultTheme {
                WifiVaultApp()
            }
        }
    }
}
