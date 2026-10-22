package com.vyoma.app

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.FolderOpen
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.Person
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

private val Mint = Color(0xFFB9F3D0)
private val Navy = Color(0xFF142B3B)
private val Cream = Color(0xFFFFFBF2)
private val Muted = Color(0xFF687781)

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            MaterialTheme(colorScheme = lightColorScheme(
                primary = Navy,
                secondary = Color(0xFF267A59),
                background = Cream,
                surface = Color.White
            )) {
                VyomaHome()
            }
        }
    }
}

@Composable
private fun VyomaHome() {
    var selectedTab by remember { mutableIntStateOf(0) }

    Scaffold(
        containerColor = Cream,
        bottomBar = {
            NavigationBar(containerColor = Color.White) {
                NavigationBarItem(
                    selected = selectedTab == 0,
                    onClick = { selectedTab = 0 },
                    icon = { Icon(Icons.Default.Home, contentDescription = "Home") },
                    label = { Text("Home") }
                )
                NavigationBarItem(
                    selected = selectedTab == 1,
                    onClick = { selectedTab = 1 },
                    icon = { Icon(Icons.Default.FolderOpen, contentDescription = "Projects") },
                    label = { Text("Projects") }
                )
                NavigationBarItem(
                    selected = selectedTab == 2,
                    onClick = { selectedTab = 2 },
                    icon = { Icon(Icons.Default.Person, contentDescription = "Profile") },
                    label = { Text("Profile") }
                )
            }
        }
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(horizontal = 20.dp, vertical = 18.dp),
            verticalArrangement = Arrangement.spacedBy(18.dp)
        ) {
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                Column {
                    Text("VYOMA", fontSize = 13.sp, fontWeight = FontWeight.Bold, color = Muted, letterSpacing = 2.sp)
                    Spacer(Modifier.height(4.dp))
                    Text("Your Ideas. Your Apps.", fontSize = 25.sp, fontWeight = FontWeight.Bold, color = Navy)
                }
                Surface(color = Mint, shape = RoundedCornerShape(16.dp)) {
                    Text("BETA", modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp), color = Navy, fontWeight = FontWeight.Bold)
                }
            }

            Card(
                colors = CardDefaults.cardColors(containerColor = Navy),
                shape = RoundedCornerShape(24.dp)
            ) {
                Column(modifier = Modifier.padding(20.dp)) {
                    Text("What do you want to build?", color = Mint, fontWeight = FontWeight.SemiBold)
                    Spacer(Modifier.height(8.dp))
                    Text(
                        "Describe your app idea. Vyoma will help you plan it before code is generated.",
                        color = Color.White,
                        fontSize = 16.sp
                    )
                    Spacer(Modifier.height(18.dp))
                    Button(
                        onClick = { },
                        colors = ButtonDefaults.buttonColors(containerColor = Mint, contentColor = Navy),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text("Start a project")
                    }
                }
            }

            Text("Your workspace", fontSize = 20.sp, fontWeight = FontWeight.Bold, color = Navy)
            Card(
                colors = CardDefaults.cardColors(containerColor = Color.White),
                shape = RoundedCornerShape(20.dp)
            ) {
                Column(modifier = Modifier.padding(18.dp)) {
                    Text("Plan first. Build with confidence.", fontWeight = FontWeight.SemiBold, color = Navy)
                    Spacer(Modifier.height(6.dp))
                    Text(
                        "Review screens, navigation, data, permissions, and tests in a blueprint before code changes begin.",
                        color = Muted
                    )
                    Spacer(Modifier.height(14.dp))
                    Row {
                        Surface(color = Mint, shape = RoundedCornerShape(10.dp)) {
                            Text("Blueprint", modifier = Modifier.padding(horizontal = 10.dp, vertical = 7.dp), color = Navy, fontSize = 12.sp)
                        }
                        Spacer(Modifier.width(8.dp))
                        Surface(color = Color(0xFFF0F2F4), shape = RoundedCornerShape(10.dp)) {
                            Text("Native Android", modifier = Modifier.padding(horizontal = 10.dp, vertical = 7.dp), color = Navy, fontSize = 12.sp)
                        }
                    }
                }
            }

            Spacer(Modifier.height(2.dp))
            Text(
                "Prototype screen · AI, project saving, GitHub, and APK builds are not connected yet.",
                color = Muted,
                fontSize = 12.sp
            )
        }
    }
}
