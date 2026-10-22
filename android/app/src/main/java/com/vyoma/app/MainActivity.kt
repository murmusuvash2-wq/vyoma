package com.vyoma.app

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.background
import androidx.compose.foundation.horizontalScroll
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
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.ArrowForward
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.Build
import androidx.compose.material.icons.filled.ChatBubbleOutline
import androidx.compose.material.icons.filled.FolderOpen
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

internal val Mint = Color(0xFFBDF4D3)
internal val Green = Color(0xFF247451)
internal val Navy = Color(0xFF132B3A)
internal val Cream = Color(0xFFF8F8F2)
internal val Muted = Color(0xFF6E7C82)
internal val Line = Color(0xFFE5EAE6)

private data class StarterTemplate(
    val title: String,
    val subtitle: String,
    val symbol: String,
    val tint: Color,
    val prompt: String
)

private val templates = listOf(
    StarterTemplate("Expense tracker", "Track spending simply", "₹", Color(0xFFE1F5E8), "Create a personal expense tracker with categories, monthly budgets and spending charts."),
    StarterTemplate("Learning app", "Learn a little every day", "✦", Color(0xFFE8E8FF), "Create a learning app with short lessons, quizzes, progress and streaks."),
    StarterTemplate("Bus tracker", "Routes and live updates", "↗", Color(0xFFFFE9D5), "Create a bus route app with saved routes, stops, verified timings and service updates."),
    StarterTemplate("Habit garden", "Build consistent habits", "❋", Color(0xFFFFE3EA), "Create a habit tracker with daily check-ins, reminders and a visual progress garden."),
    StarterTemplate("Shop app", "Browse a product catalogue", "▦", Color(0xFFE0F0F7), "Create a shopping catalogue with product cards, search, filters and a cart demo."),
    StarterTemplate("Chat app", "A simple messaging UI", "••", Color(0xFFF4E8CF), "Create a messaging app UI with conversations, message bubbles and profile details.")
)

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            androidx.compose.material3.MaterialTheme(
                colorScheme = lightColorScheme(
                    primary = Navy,
                    secondary = Green,
                    background = Cream,
                    surface = Color.White
                )
            ) { EntryFlow { VyomaApp() } }
        }
    }
}


@Composable
private fun VyomaApp() {
    var selectedTab by remember { mutableIntStateOf(0) }
    var idea by remember { mutableStateOf("") }
    var draftPrompt by remember { mutableStateOf<String?>(null) }
    var selectedTemplate by remember { mutableStateOf<StarterTemplate?>(null) }
    var blueprintIdea by remember { mutableStateOf("") }
    val localProjects = remember { mutableStateListOf<String>() }
    val approvedBlueprints = remember { mutableStateListOf<ApprovedBlueprint>() }

    Scaffold(
        containerColor = Cream,
        bottomBar = {
            NavigationBar(containerColor = Color.White, tonalElevation = 8.dp) {
                val labels = listOf("Home", "Projects", "Blueprint", "Preview", "Settings")
                val icons = listOf(Icons.Default.Home, Icons.Default.FolderOpen, Icons.Default.AutoAwesome, Icons.Default.Visibility, Icons.Default.Settings)
                labels.forEachIndexed { index, label ->
                    NavigationBarItem(
                        selected = selectedTab == index,
                        onClick = { selectedTab = index },
                        icon = { Icon(icons[index], contentDescription = label) },
                        label = { Text(label, fontSize = 11.sp) }
                    )
                }
            }
        }
    ) { insets ->
        when (selectedTab) {
            0 -> HomeScreen(
                modifier = Modifier.padding(insets),
                idea = idea,
                onIdeaChange = { idea = it },
                onStart = {
                    if (idea.isNotBlank()) {
                        blueprintIdea = idea.trim()
                        selectedTab = 2
                    }
                },
                onTemplatePreview = { selectedTemplate = it; selectedTab = 3 },
                onUseTemplate = {
                    blueprintIdea = it.prompt
                    selectedTab = 2
                },
                onSeeProjects = { selectedTab = 1 }
            )
            1 -> ProjectsScreen(
                modifier = Modifier.padding(insets),
                projects = localProjects,
                approvedBlueprints = approvedBlueprints,
                draftPrompt = draftPrompt,
                onCreateFromDraft = {
                    val name = draftPrompt?.take(36)?.ifBlank { "New project" } ?: "New project"
                    if (localProjects.none { it == name }) localProjects.add(name)
                    draftPrompt = null
                },
                onBackHome = { selectedTab = 0 }
            )
            2 -> BlueprintScreen(
                modifier = Modifier.padding(insets),
                initialIdea = blueprintIdea,
                onBack = { selectedTab = 0 },
                onApproved = { blueprint ->
                    approvedBlueprints.add(blueprint)
                    localProjects.add(blueprint.name)
                    blueprintIdea = ""
                    selectedTab = 1
                }
            )
            3 -> PreviewScreen(Modifier.padding(insets), selectedTemplate)
            else -> SettingsScreen(Modifier.padding(insets))
        }
    }
}

@Composable
private fun HomeScreen(
    modifier: Modifier,
    idea: String,
    onIdeaChange: (String) -> Unit,
    onStart: () -> Unit,
    onTemplatePreview: (StarterTemplate) -> Unit,
    onUseTemplate: (StarterTemplate) -> Unit,
    onSeeProjects: () -> Unit
) {
    Column(
        modifier = modifier.fillMaxSize().padding(horizontal = 20.dp),
    ) {
        Column(
            modifier = Modifier.weight(1f).verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(22.dp)
        ) {
            Spacer(Modifier.height(10.dp))
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.SpaceBetween, modifier = Modifier.fillMaxWidth()) {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    Surface(shape = RoundedCornerShape(14.dp), color = Navy, modifier = Modifier.size(42.dp)) {
                        Box(contentAlignment = Alignment.Center) {
                            Icon(Icons.Default.AutoAwesome, contentDescription = null, tint = Mint, modifier = Modifier.size(23.dp))
                        }
                    }
                    Column {
                        Text("VYOMA", color = Navy, fontSize = 15.sp, fontWeight = FontWeight.ExtraBold, letterSpacing = 2.sp)
                        Text("Ideas into apps", color = Muted, fontSize = 12.sp)
                    }
                }
                Surface(color = Mint, shape = CircleShape) {
                    Text("BETA", modifier = Modifier.padding(horizontal = 12.dp, vertical = 7.dp), color = Navy, fontWeight = FontWeight.Bold, fontSize = 11.sp)
                }
            }

            Column(verticalArrangement = Arrangement.spacedBy(7.dp)) {
                Text("Dream it.\nBuild it.", color = Navy, fontSize = 34.sp, lineHeight = 38.sp, fontWeight = FontWeight.ExtraBold)
                Text("Tell Vyoma your idea. We’ll shape the plan together before building anything.", color = Muted, fontSize = 15.sp, lineHeight = 22.sp)
            }

            Card(
                shape = RoundedCornerShape(26.dp),
                colors = CardDefaults.cardColors(containerColor = Color.White),
                border = androidx.compose.foundation.BorderStroke(1.dp, Line),
                elevation = CardDefaults.cardElevation(defaultElevation = 1.dp)
            ) {
                Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(13.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        Surface(color = Color(0xFFE5F7EC), shape = RoundedCornerShape(10.dp), modifier = Modifier.size(34.dp)) {
                            Box(contentAlignment = Alignment.Center) {
                                Icon(Icons.Default.ChatBubbleOutline, contentDescription = null, tint = Green, modifier = Modifier.size(19.dp))
                            }
                        }
                        Column {
                            Text("Start with an idea", color = Navy, fontWeight = FontWeight.Bold, fontSize = 15.sp)
                            Text("AI planning · no code needed", color = Muted, fontSize = 11.sp)
                        }
                    }
                    Box(
                        modifier = Modifier.fillMaxWidth().height(94.dp).background(Cream, RoundedCornerShape(16.dp)).padding(13.dp)
                    ) {
                        if (idea.isEmpty()) {
                            Text("I want to build an app that…", color = Muted, fontSize = 14.sp)
                        }
                        BasicTextField(
                            value = idea,
                            onValueChange = onIdeaChange,
                            modifier = Modifier.fillMaxSize(),
                            textStyle = androidx.compose.ui.text.TextStyle(color = Navy, fontSize = 14.sp, lineHeight = 20.sp),
                            decorationBox = { inner -> inner() }
                        )
                    }
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.SpaceBetween, modifier = Modifier.fillMaxWidth()) {
                        Text("You’ll review the blueprint first", color = Muted, fontSize = 11.sp, modifier = Modifier.weight(1f))
                        Button(
                            onClick = onStart,
                            enabled = idea.isNotBlank(),
                            shape = RoundedCornerShape(14.dp),
                            colors = ButtonDefaults.buttonColors(containerColor = Navy, contentColor = Color.White),
                            contentPadding = androidx.compose.foundation.layout.PaddingValues(horizontal = 15.dp, vertical = 11.dp)
                        ) {
                            Text("Plan idea")
                            Spacer(Modifier.width(7.dp))
                            Icon(Icons.Default.ArrowForward, contentDescription = null, modifier = Modifier.size(16.dp))
                        }
                    }
                }
            }

            Column(verticalArrangement = Arrangement.spacedBy(11.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.SpaceBetween, modifier = Modifier.fillMaxWidth()) {
                    Column {
                        Text("Need a starting point?", color = Navy, fontWeight = FontWeight.Bold, fontSize = 19.sp)
                        Text("Pick a template and make it yours", color = Muted, fontSize = 12.sp)
                    }
                    Surface(color = Color(0xFFEAF0EB), shape = RoundedCornerShape(10.dp)) {
                        Text("6 STARTERS", color = Green, fontSize = 10.sp, fontWeight = FontWeight.Bold, modifier = Modifier.padding(horizontal = 8.dp, vertical = 6.dp))
                    }
                }
                Row(
                    modifier = Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),
                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    templates.forEach { item ->
                        TemplateCard(item, onPreview = { onTemplatePreview(item) }, onUse = { onUseTemplate(item) })
                    }
                }
            }

            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.SpaceBetween, modifier = Modifier.fillMaxWidth()) {
                Column {
                    Text("Your projects", color = Navy, fontWeight = FontWeight.Bold, fontSize = 19.sp)
                    Text("Your ideas, all in one place", color = Muted, fontSize = 12.sp)
                }
                OutlinedButton(onClick = onSeeProjects, shape = RoundedCornerShape(12.dp)) {
                    Text("View all", fontSize = 12.sp)
                }
            }
            Surface(color = Color.White, shape = RoundedCornerShape(18.dp), border = androidx.compose.foundation.BorderStroke(1.dp, Line)) {
                Row(Modifier.fillMaxWidth().padding(15.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    Surface(color = Color(0xFFE8E8FF), shape = RoundedCornerShape(12.dp), modifier = Modifier.size(42.dp)) {
                        Box(contentAlignment = Alignment.Center) { Icon(Icons.Default.FolderOpen, contentDescription = null, tint = Navy) }
                    }
                    Column(Modifier.weight(1f)) {
                        Text("Your next big idea starts here", color = Navy, fontWeight = FontWeight.SemiBold, fontSize = 13.sp)
                        Text("Create a project or start from a template.", color = Muted, fontSize = 11.sp)
                    }
                    Icon(Icons.Default.ArrowForward, contentDescription = null, tint = Muted)
                }
            }
            Text("Starter UI only · AI, cloud builds and project sync are not connected yet.", color = Muted, fontSize = 10.sp, modifier = Modifier.padding(bottom = 12.dp))
        }
    }
}

@Composable
private fun TemplateCard(item: StarterTemplate, onPreview: () -> Unit, onUse: () -> Unit) {
    Card(
        modifier = Modifier.width(224.dp),
        shape = RoundedCornerShape(22.dp),
        colors = CardDefaults.cardColors(containerColor = Color.White),
        border = androidx.compose.foundation.BorderStroke(1.dp, Line)
    ) {
        Column(Modifier.padding(13.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Surface(color = item.tint, shape = RoundedCornerShape(15.dp), modifier = Modifier.fillMaxWidth().height(94.dp)) {
                Box(contentAlignment = Alignment.Center) {
                    Text(item.symbol, color = Navy, fontSize = 42.sp, fontWeight = FontWeight.Bold)
                    Surface(color = Color.White.copy(alpha = 0.85f), shape = RoundedCornerShape(9.dp), modifier = Modifier.align(Alignment.BottomEnd).padding(8.dp)) {
                        Text("DEMO", color = Muted, fontSize = 9.sp, fontWeight = FontWeight.Bold, modifier = Modifier.padding(horizontal = 7.dp, vertical = 4.dp))
                    }
                }
            }
            Text(item.title, color = Navy, fontSize = 16.sp, fontWeight = FontWeight.Bold)
            Text(item.subtitle, color = Muted, fontSize = 12.sp)
            Row(horizontalArrangement = Arrangement.spacedBy(7.dp), modifier = Modifier.fillMaxWidth()) {
                OutlinedButton(onClick = onPreview, modifier = Modifier.weight(1f), shape = RoundedCornerShape(11.dp), contentPadding = androidx.compose.foundation.layout.PaddingValues(horizontal = 8.dp, vertical = 7.dp)) {
                    Text("Preview", fontSize = 11.sp)
                }
                Button(onClick = onUse, modifier = Modifier.weight(1f), shape = RoundedCornerShape(11.dp), colors = ButtonDefaults.buttonColors(containerColor = Navy), contentPadding = androidx.compose.foundation.layout.PaddingValues(horizontal = 8.dp, vertical = 7.dp)) {
                    Icon(Icons.Default.Add, contentDescription = null, modifier = Modifier.size(14.dp))
                    Spacer(Modifier.width(4.dp))
                    Text("Use", fontSize = 11.sp)
                }
            }
        }
    }
}

@Composable
private fun ProjectsScreen(modifier: Modifier, projects: List<String>, approvedBlueprints: List<ApprovedBlueprint>, draftPrompt: String?, onCreateFromDraft: () -> Unit, onBackHome: () -> Unit) {
    Column(modifier.fillMaxSize().padding(20.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
        Text("Projects", color = Navy, fontSize = 29.sp, fontWeight = FontWeight.ExtraBold)
        Text("Your app ideas and workspaces", color = Muted, fontSize = 14.sp)
        if (draftPrompt != null) {
            Card(colors = CardDefaults.cardColors(containerColor = Color.White), shape = RoundedCornerShape(22.dp), border = androidx.compose.foundation.BorderStroke(1.dp, Line)) {
                Column(Modifier.padding(17.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    Text("Idea ready to plan", color = Green, fontWeight = FontWeight.Bold, fontSize = 12.sp)
                    Text(draftPrompt, color = Navy, fontSize = 15.sp)
                    Text("Next step: review a blueprint. AI project creation is not connected in this prototype.", color = Muted, fontSize = 12.sp)
                    Button(onClick = onCreateFromDraft, colors = ButtonDefaults.buttonColors(containerColor = Navy), shape = RoundedCornerShape(13.dp)) {
                        Text("Save local draft")
                    }
                }
            }
        }
        approvedBlueprints.forEach { blueprint ->
            Card(colors = CardDefaults.cardColors(containerColor = Color.White), shape = RoundedCornerShape(18.dp), border = androidx.compose.foundation.BorderStroke(1.dp, Line)) {
                Column(Modifier.fillMaxWidth().padding(15.dp), verticalArrangement = Arrangement.spacedBy(5.dp)) {
                    Text(blueprint.name, color = Navy, fontWeight = FontWeight.Bold)
                    Text("Blueprint approved · Android · local prototype", color = Green, fontSize = 11.sp)
                    Text(blueprint.goal, color = Muted, fontSize = 12.sp)
                }
            }
        }
        if (projects.isEmpty() && approvedBlueprints.isEmpty()) {
            Surface(color = Color.White, shape = RoundedCornerShape(22.dp), border = androidx.compose.foundation.BorderStroke(1.dp, Line)) {
                Column(Modifier.fillMaxWidth().padding(22.dp), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Icon(Icons.Default.FolderOpen, contentDescription = null, tint = Green, modifier = Modifier.size(36.dp))
                    Text("No projects yet", color = Navy, fontWeight = FontWeight.Bold, fontSize = 18.sp)
                    Text("Start with an idea or use a ready-made template.", color = Muted, fontSize = 13.sp)
                    Button(onClick = onBackHome, colors = ButtonDefaults.buttonColors(containerColor = Navy)) { Text("Explore starters") }
                }
            }
        } else {
            projects.forEach { project ->
                Surface(color = Color.White, shape = RoundedCornerShape(18.dp), border = androidx.compose.foundation.BorderStroke(1.dp, Line)) {
                    Row(Modifier.fillMaxWidth().padding(15.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                        Icon(Icons.Default.Build, contentDescription = null, tint = Green)
                        Column {
                            Text(project, color = Navy, fontWeight = FontWeight.SemiBold)
                            Text("Local demo draft · not synced", color = Muted, fontSize = 11.sp)
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun PreviewScreen(modifier: Modifier, selectedTemplate: StarterTemplate?) {
    Column(modifier.fillMaxSize().padding(20.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
        Text("Preview", color = Navy, fontSize = 29.sp, fontWeight = FontWeight.ExtraBold)
        Text("Explore a starter before using it", color = Muted, fontSize = 14.sp)
        val item = selectedTemplate
        Surface(color = item?.tint ?: Color.White, shape = RoundedCornerShape(26.dp), border = androidx.compose.foundation.BorderStroke(1.dp, Line)) {
            Column(Modifier.fillMaxWidth().padding(24.dp), verticalArrangement = Arrangement.spacedBy(12.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                Text(item?.symbol ?: "✦", color = Navy, fontSize = 52.sp, fontWeight = FontWeight.Bold)
                Text(item?.title ?: "Your app preview", color = Navy, fontSize = 23.sp, fontWeight = FontWeight.Bold)
                Text(item?.subtitle ?: "Choose Preview on any starter card to see it here.", color = Muted, fontSize = 13.sp)
                Text("Illustrative template preview", color = Muted, fontSize = 11.sp)
            }
        }
        Text("This is a visual demo, not a running generated app.", color = Muted, fontSize = 12.sp)
    }
}

@Composable
private fun SettingsScreen(modifier: Modifier) {
    Column(modifier.fillMaxSize().padding(20.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
        Text("Settings", color = Navy, fontSize = 29.sp, fontWeight = FontWeight.ExtraBold)
        Text("Your Vyoma workspace preferences", color = Muted, fontSize = 14.sp)
        listOf("Account", "AI provider connection", "GitHub connection", "Build preferences", "Privacy & security", "About Vyoma").forEach { label ->
            Surface(color = Color.White, shape = RoundedCornerShape(16.dp), border = androidx.compose.foundation.BorderStroke(1.dp, Line)) {
                Row(Modifier.fillMaxWidth().padding(17.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.SpaceBetween) {
                    Text(label, color = Navy, fontWeight = FontWeight.Medium)
                    Icon(Icons.Default.ArrowForward, contentDescription = null, tint = Muted)
                }
            }
        }
        Text("Settings are visual placeholders at this stage.", color = Muted, fontSize = 12.sp)
    }
}
