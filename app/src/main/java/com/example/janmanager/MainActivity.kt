package com.example.janmanager

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.navigation.compose.rememberNavController
import com.example.janmanager.ui.navigation.JanManagerNavGraph
import com.example.janmanager.ui.theme.JanManagerTheme
import dagger.hilt.android.AndroidEntryPoint

@AndroidEntryPoint
class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            JanManagerTheme {
                val navController = rememberNavController()
                JanManagerNavGraph(navController = navController)
            }
        }
    }
}