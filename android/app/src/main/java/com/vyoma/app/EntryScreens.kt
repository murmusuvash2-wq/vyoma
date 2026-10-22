package com.vyoma.app

import android.content.Context
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowForward
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.delay

@Composable
internal fun EntryFlow(onFinished: @Composable () -> Unit) {
    val context = LocalContext.current
    val preferences = remember { context.getSharedPreferences("vyoma_onboarding", Context.MODE_PRIVATE) }
    var entryState by remember { mutableStateOf("splash") }
    var page by remember { mutableIntStateOf(0) }

    LaunchedEffect(Unit) {
        delay(1100)
        entryState = if (preferences.getBoolean("completed", false)) "home" else "onboarding"
    }

    when (entryState) {
        "splash" -> SplashScreen()
        "onboarding" -> OnboardingScreen(
            page = page,
            onNext = {
                if (page < 2) page += 1 else {
                    preferences.edit().putBoolean("completed", true).apply()
                    entryState = "home"
                }
            },
            onSkip = {
                preferences.edit().putBoolean("completed", true).apply()
                entryState = "home"
            }
        )
        else -> onFinished()
    }
}

@Composable
internal fun SplashScreen() {
    Surface(modifier = Modifier.fillMaxSize(), color = Navy) {
        Column(
            modifier = Modifier.fillMaxSize().padding(28.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            Surface(color = Mint, shape = RoundedCornerShape(25.dp), modifier = Modifier.size(82.dp)) {
                Box(contentAlignment = Alignment.Center) {
                    Icon(Icons.Default.AutoAwesome, contentDescription = "Vyoma", tint = Navy, modifier = Modifier.size(42.dp))
                }
            }
            Spacer(Modifier.height(22.dp))
            Text("VYOMA", color = Color.White, fontSize = 28.sp, fontWeight = FontWeight.ExtraBold, letterSpacing = 5.sp)
            Spacer(Modifier.height(8.dp))
            Text("Ideas into apps", color = Mint, fontSize = 15.sp)
            Spacer(Modifier.height(42.dp))
            Text("A little idea can go a long way.", color = Color.White.copy(alpha = 0.75f), fontSize = 13.sp)
        }
    }
}

@Composable
internal fun OnboardingScreen(page: Int, onNext: () -> Unit, onSkip: () -> Unit) {
    val titles = listOf("Your idea is the starting point", "Make a plan before building", "Build, preview, improve")
    val descriptions = listOf(
        "Describe the app you imagine in everyday words. You don't need to know how to code.",
        "Turn your idea into a clear blueprint. Review the features and decisions before you approve anything.",
        "Keep your project organised, review changes and iterate towards your Android app."
    )
    val symbols = listOf("✦", "▤", "↗")
    val tints = listOf(Color(0xFFE1F5E8), Color(0xFFE8E8FF), Color(0xFFFFE9D5))

    Surface(modifier = Modifier.fillMaxSize(), color = Cream) {
        Column(
            modifier = Modifier.fillMaxSize().padding(horizontal = 24.dp, vertical = 20.dp),
            verticalArrangement = Arrangement.SpaceBetween
        ) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.SpaceBetween, modifier = Modifier.fillMaxWidth()) {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(9.dp)) {
                    Surface(color = Navy, shape = RoundedCornerShape(11.dp), modifier = Modifier.size(36.dp)) {
                        Box(contentAlignment = Alignment.Center) {
                            Icon(Icons.Default.AutoAwesome, contentDescription = null, tint = Mint, modifier = Modifier.size(20.dp))
                        }
                    }
                    Text("VYOMA", color = Navy, fontWeight = FontWeight.ExtraBold, letterSpacing = 2.sp, fontSize = 14.sp)
                }
                OutlinedButton(onClick = onSkip, shape = RoundedCornerShape(12.dp)) {
                    Text("Skip", color = Navy)
                }
            }

            Column(horizontalAlignment = Alignment.Start, verticalArrangement = Arrangement.spacedBy(18.dp)) {
                Surface(color = tints[page], shape = RoundedCornerShape(30.dp), modifier = Modifier.fillMaxWidth().height(270.dp)) {
                    Box(contentAlignment = Alignment.Center) {
                        Surface(color = Color.White.copy(alpha = 0.78f), shape = CircleShape, modifier = Modifier.size(154.dp)) {
                            Box(contentAlignment = Alignment.Center) {
                                Text(symbols[page], color = Navy, fontSize = 78.sp, fontWeight = FontWeight.Bold)
                            }
                        }
                        Surface(color = Navy, shape = RoundedCornerShape(14.dp), modifier = Modifier.align(Alignment.BottomEnd).padding(18.dp)) {
                            Text(
                                when (page) { 0 -> "YOUR IDEA"; 1 -> "YOUR BLUEPRINT"; else -> "YOUR WORKSPACE" },
                                color = Mint, fontWeight = FontWeight.Bold, fontSize = 10.sp,
                                modifier = Modifier.padding(horizontal = 12.dp, vertical = 9.dp)
                            )
                        }
                    }
                }
                Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    repeat(3) { index ->
                        Box(Modifier.width(if (index == page) 28.dp else 8.dp).height(7.dp).background(if (index == page) Green else Line, CircleShape))
                    }
                }
                Text(titles[page], color = Navy, fontSize = 29.sp, lineHeight = 35.sp, fontWeight = FontWeight.ExtraBold)
                Text(descriptions[page], color = Muted, fontSize = 15.sp, lineHeight = 23.sp)
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(9.dp)) {
                    Icon(Icons.Default.AutoAwesome, contentDescription = null, tint = Green, modifier = Modifier.size(19.dp))
                    Text(
                        when (page) { 0 -> "Start simple. Refine as you go."; 1 -> "You stay in control of the plan."; else -> "Review changes before accepting them." },
                        color = Green, fontSize = 12.sp, fontWeight = FontWeight.SemiBold
                    )
                }
            }

            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Button(
                    onClick = onNext,
                    modifier = Modifier.fillMaxWidth().height(54.dp),
                    shape = RoundedCornerShape(17.dp),
                    colors = ButtonDefaults.buttonColors(containerColor = Navy)
                ) {
                    Text(if (page == 2) "Get started" else "Continue", fontSize = 15.sp, fontWeight = FontWeight.Bold)
                    Spacer(Modifier.width(8.dp))
                    Icon(Icons.Default.ArrowForward, contentDescription = null, modifier = Modifier.size(17.dp))
                }
                Text("Android app builder · Early prototype", color = Muted, fontSize = 11.sp, modifier = Modifier.align(Alignment.CenterHorizontally))
            }
        }
    }
}