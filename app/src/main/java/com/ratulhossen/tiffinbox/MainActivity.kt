package com.ratulhossen.tiffinbox

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import com.ratulhossen.tiffinbox.ui.navigation.TiffinNavGraph
import com.ratulhossen.tiffinbox.ui.theme.TiffinBoxTheme

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            TiffinBoxApp()
        }
    }
}

@Composable
fun TiffinBoxApp() {
    TiffinBoxTheme {
        Surface(modifier = Modifier.fillMaxSize()) {
            TiffinNavGraph()
        }
    }
}
