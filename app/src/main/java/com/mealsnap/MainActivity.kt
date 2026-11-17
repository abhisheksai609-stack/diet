package com.mealsnap

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import com.mealsnap.ui.MealSnapRoot
import com.mealsnap.ui.theme.MealSnapTheme

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            MealSnapTheme {
                MealSnapRoot()
            }
        }
    }
}
