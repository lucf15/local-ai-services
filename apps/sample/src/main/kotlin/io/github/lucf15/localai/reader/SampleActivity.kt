package io.github.lucf15.localai.reader

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import io.github.lucf15.localai.design.LocalAiTheme
import io.github.lucf15.localai.design.installLocalAiSplash

class SampleActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        installLocalAiSplash()
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent { LocalAiTheme { SampleApp() } }
    }
}
