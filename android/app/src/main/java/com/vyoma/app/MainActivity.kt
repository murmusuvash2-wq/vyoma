package com.vyoma.app

import android.os.Bundle
import android.Manifest
import android.content.pm.PackageManager
import android.os.Build
import androidx.activity.result.contract.ActivityResultContracts
import androidx.core.content.ContextCompat
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
import androidx.compose.material.icons.filled.Menu
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.IconButton
import androidx.compose.material3.ModalNavigationDrawer
import androidx.compose.material3.ModalDrawerSheet
import androidx.compose.material3.NavigationDrawerItem
import androidx.compose.material3.NavigationDrawerItemDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.material3.DrawerValue
import androidx.compose.material3.rememberDrawerState
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
import androidx.compose.ui.platform.LocalContext
import kotlinx.coroutines.launch

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
    private val notificationPermissionLauncher =
        registerForActivityResult(ActivityResultContracts.RequestPermission()) { }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        UpdateCheckScheduler.schedule(this)
        if (
            Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
            ContextCompat.checkSelfPermission(this, Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED
        ) {
            notificationPermissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
        }
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
    val context = LocalContext.current
    var selectedTab by remember { mutableIntStateOf(0) }
    var idea by remember { mutableStateOf("") }
    var draftPrompt by remember { mutableStateOf<String?>(null) }
    var selectedTemplate by remember { mutableStateOf<StarterTemplate?>(null) }
    var blueprintIdea by remember { mutableStateOf("") }
    val localProjects = remember(context) { mutableStateListOf<String>().apply { addAll(ProjectStore.loadLocalProjects(context)) } }
    val approvedBlueprints = remember(context) { mutableStateListOf<ApprovedBlueprint>().apply { addAll(ProjectStore.loadBlueprints(context)) } }

    val drawerState = rememberDrawerState(initialValue = DrawerValue.Closed)
    val scope = rememberCoroutineScope()
    val screenTitle = when (selectedTab) {
        0 -> "VYOMA"
        1 -> "Projects"
        2 -> "Blueprint review"
        3 -> "Preview"
        4 -> "AI connection"
        else -> "Coding Agent"
    }

    ModalNavigationDrawer(
        drawerState = drawerState,
        drawerContent = {
            ModalDrawerSheet(
                drawerContainerColor = Color.White,
                drawerContentColor = Navy
            ) {
                Column(Modifier.fillMaxSize()) {
                    Surface(
                        color = Navy,
                        shape = RoundedCornerShape(bottomStart = 26.dp, bottomEnd = 26.dp)
                    ) {
                        Column(
                            Modifier.fillMaxWidth().padding(start = 20.dp, end = 18.dp, top = 28.dp, bottom = 22.dp),
                            verticalArrangement = Arrangement.spacedBy(14.dp)
                        ) {
                            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                                Surface(color = Mint, shape = RoundedCornerShape(15.dp), modifier = Modifier.size(48.dp)) {
                                    Box(contentAlignment = Alignment.Center) {
                                        Icon(Icons.Default.AutoAwesome, contentDescription = null, tint = Navy, modifier = Modifier.size(25.dp))
                                    }
                                }
                                Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(3.dp)) {
                                    Text("VYOMA", color = Color.White, fontSize = 21.sp, fontWeight = FontWeight.ExtraBold, letterSpacing = 2.sp)
                                    Text("Your AI app studio", color = Color(0xFFD5E3E5), fontSize = 12.sp)
                                }
                                Surface(color = Color(0xFF294653), shape = RoundedCornerShape(9.dp)) {
                                    Text("BETA", color = Mint, fontSize = 10.sp, fontWeight = FontWeight.Bold, modifier = Modifier.padding(horizontal = 9.dp, vertical = 6.dp))
                                }
                            }
                            Text(
                                "Turn your ideas into a plan, one step at a time.",
                                color = Color(0xFFE5EFEC),
                                fontSize = 13.sp,
                                lineHeight = 19.sp
                            )
                        }
                    }

                    Text(
                        "WORKSPACE",
                        color = Muted,
                        fontSize = 10.sp,
                        fontWeight = FontWeight.Bold,
                        letterSpacing = 1.5.sp,
                        modifier = Modifier.padding(start = 22.dp, top = 22.dp, bottom = 8.dp)
                    )
                    NavigationDrawerItem(
                        label = { Text("Home", fontWeight = if (selectedTab == 0) FontWeight.SemiBold else FontWeight.Normal) },
                        selected = selectedTab == 0,
                        icon = { Icon(Icons.Default.Home, contentDescription = null) },
                        onClick = { selectedTab = 0; scope.launch { drawerState.close() } },
                        modifier = Modifier.padding(horizontal = 12.dp, vertical = 3.dp),
                        shape = RoundedCornerShape(14.dp),
                        colors = NavigationDrawerItemDefaults.colors(
                            selectedContainerColor = Mint,
                            selectedIconColor = Green,
                            selectedTextColor = Navy,
                            unselectedIconColor = Muted,
                            unselectedTextColor = Navy
                        )
                    )
                    NavigationDrawerItem(
                        label = { Text("Projects", fontWeight = if (selectedTab == 1) FontWeight.SemiBold else FontWeight.Normal) },
                        selected = selectedTab == 1,
                        icon = { Icon(Icons.Default.FolderOpen, contentDescription = null) },
                        onClick = { selectedTab = 1; scope.launch { drawerState.close() } },
                        modifier = Modifier.padding(horizontal = 12.dp, vertical = 3.dp),
                        shape = RoundedCornerShape(14.dp),
                        colors = NavigationDrawerItemDefaults.colors(
                            selectedContainerColor = Mint,
                            selectedIconColor = Green,
                            selectedTextColor = Navy,
                            unselectedIconColor = Muted,
                            unselectedTextColor = Navy
                        )
                    )

                    Text(
                        "TOOLS",
                        color = Muted,
                        fontSize = 10.sp,
                        fontWeight = FontWeight.Bold,
                        letterSpacing = 1.5.sp,
                        modifier = Modifier.padding(start = 22.dp, top = 18.dp, bottom = 8.dp)
                    )
                    NavigationDrawerItem(
                        label = { Text("Preview", fontWeight = if (selectedTab == 3) FontWeight.SemiBold else FontWeight.Normal) },
                        selected = selectedTab == 3,
                        icon = { Icon(Icons.Default.Visibility, contentDescription = null) },
                        onClick = { selectedTab = 3; scope.launch { drawerState.close() } },
                        modifier = Modifier.padding(horizontal = 12.dp, vertical = 3.dp),
                        shape = RoundedCornerShape(14.dp),
                        colors = NavigationDrawerItemDefaults.colors(
                            selectedContainerColor = Mint,
                            selectedIconColor = Green,
                            selectedTextColor = Navy,
                            unselectedIconColor = Muted,
                            unselectedTextColor = Navy
                        )
                    )
                    NavigationDrawerItem(
                        label = { Text("Settings", fontWeight = if (selectedTab == 4) FontWeight.SemiBold else FontWeight.Normal) },
                        selected = selectedTab == 4,
                        icon = { Icon(Icons.Default.Settings, contentDescription = null) },
                        onClick = { selectedTab = 4; scope.launch { drawerState.close() } },
                        modifier = Modifier.padding(horizontal = 12.dp, vertical = 3.dp),
                        shape = RoundedCornerShape(14.dp),
                        colors = NavigationDrawerItemDefaults.colors(
                            selectedContainerColor = Mint,
                            selectedIconColor = Green,
                            selectedTextColor = Navy,
                            unselectedIconColor = Muted,
                            unselectedTextColor = Navy
                        )
                    )

                    NavigationDrawerItem(
                        label = { Text("Coding Agent", fontWeight = if (selectedTab == 5) FontWeight.SemiBold else FontWeight.Normal) },
                        selected = selectedTab == 5,
                        icon = { Icon(Icons.Default.Build, contentDescription = null) },
                        onClick = { selectedTab = 5; scope.launch { drawerState.close() } },
                        modifier = Modifier.padding(horizontal = 12.dp, vertical = 3.dp),
                        shape = RoundedCornerShape(14.dp),
                        colors = NavigationDrawerItemDefaults.colors(
                            selectedContainerColor = Mint,
                            selectedIconColor = Green,
                            selectedTextColor = Navy,
                            unselectedIconColor = Muted,
                            unselectedTextColor = Navy
                        )
                    )

                    Spacer(Modifier.weight(1f))
                    HorizontalDivider(color = Line)
                    Row(
                        Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 18.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(11.dp)
                    ) {
                        Surface(color = Color(0xFFE7F6EC), shape = CircleShape, modifier = Modifier.size(38.dp)) {
                            Box(contentAlignment = Alignment.Center) {
                                Text("V", color = Green, fontSize = 17.sp, fontWeight = FontWeight.ExtraBold)
                            }
                        }
                        Column(verticalArrangement = Arrangement.spacedBy(3.dp)) {
                            Text("Vyoma workspace", color = Navy, fontSize = 12.sp, fontWeight = FontWeight.SemiBold)
                            Text("Early access · Prototype", color = Muted, fontSize = 10.sp)
                        }
                    }
                }
            }
        }
    ) {
        Scaffold(
            containerColor = Cream,
            topBar = {
                Row(
                    modifier = Modifier.fillMaxWidth().background(Cream).height(58.dp).padding(horizontal = 10.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    IconButton(onClick = { scope.launch { drawerState.open() } }) {
                        Icon(Icons.Default.Menu, contentDescription = "Open navigation menu", tint = Navy)
                    }
                    Text(screenTitle, color = Navy, fontSize = 18.sp, fontWeight = FontWeight.Bold)
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
                onSeeProjects = { selectedTab = 1 },
                onAddResearch = { suggestion ->
                    blueprintIdea = suggestion
                    selectedTab = 2
                }
            )
            1 -> ProjectsScreen(
                modifier = Modifier.padding(insets),
                projects = localProjects,
                approvedBlueprints = approvedBlueprints,
                draftPrompt = draftPrompt,
                onCreateFromDraft = {
                    val name = draftPrompt?.take(36)?.ifBlank { "New project" } ?: "New project"
                    if (localProjects.none { it == name }) {
                        localProjects.add(name)
                        ProjectStore.saveLocalProjects(context, localProjects)
                    }
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
                    ProjectStore.saveBlueprints(context, approvedBlueprints)
                    blueprintIdea = ""
                    selectedTab = 1
                }
            )
            3 -> PreviewScreen(Modifier.padding(insets), selectedTemplate)
            4 -> AiConnectionSettings(Modifier.padding(insets))
            else -> CodingAgentScreen(Modifier.padding(insets))
        }
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
    onSeeProjects: () -> Unit,
    onAddResearch: (String) -> Unit
) {
    var showAttachMenu by remember { mutableStateOf(false) }
    val researchIdeas = listOf(
        "A habit tracker with daily goals and progress charts",
        "A bus timetable app with verified routes and saved stops",
        "A simple expense tracker that works offline"
    )
    Column(
        modifier = modifier.fillMaxSize().padding(horizontal = 18.dp)
    ) {
        Column(
            modifier = Modifier.weight(1f).verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            Spacer(Modifier.height(4.dp))
            Row(
                Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Column(verticalArrangement = Arrangement.spacedBy(3.dp)) {
                    Text("Explore with Vyoma", color = Navy, fontSize = 25.sp, fontWeight = FontWeight.ExtraBold)
                    Text("Research an idea before you build it.", color = Muted, fontSize = 13.sp)
                }
                Surface(color = Mint, shape = CircleShape) {
                    Text("BETA", modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp), color = Navy, fontWeight = FontWeight.Bold, fontSize = 10.sp)
                }
            }

            Card(
                shape = RoundedCornerShape(24.dp),
                colors = CardDefaults.cardColors(containerColor = Navy),
                elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
            ) {
                Column(Modifier.fillMaxWidth().padding(18.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                        Surface(color = Mint, shape = RoundedCornerShape(16.dp), modifier = Modifier.size(54.dp)) {
                            Box(contentAlignment = Alignment.Center) {
                                Icon(Icons.Default.AutoAwesome, contentDescription = null, tint = Navy, modifier = Modifier.size(29.dp))
                            }
                        }
                        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(3.dp)) {
                            Text("Demo project", color = Color.White, fontSize = 18.sp, fontWeight = FontWeight.Bold)
                            Text("GoalGarden · Habit tracker", color = Color(0xFFD5E3E5), fontSize = 12.sp)
                        }
                    }
                    Text("Explore a sample app idea, its screens and the kind of blueprint Vyoma can help you prepare.", color = Color(0xFFE5EFEC), fontSize = 13.sp, lineHeight = 19.sp)
                    Row(horizontalArrangement = Arrangement.spacedBy(9.dp)) {
                        OutlinedButton(
                            onClick = { onTemplatePreview(templates.first()) },
                            shape = RoundedCornerShape(12.dp),
                            colors = ButtonDefaults.outlinedButtonColors(contentColor = Color.White),
                            contentPadding = androidx.compose.foundation.layout.PaddingValues(horizontal = 12.dp, vertical = 8.dp)
                        ) {
                            Icon(Icons.Default.Visibility, contentDescription = null, modifier = Modifier.size(15.dp))
                            Spacer(Modifier.width(6.dp))
                            Text("Preview demo", fontSize = 12.sp)
                        }
                        Button(
                            onClick = { onUseTemplate(templates.first()) },
                            shape = RoundedCornerShape(12.dp),
                            colors = ButtonDefaults.buttonColors(containerColor = Mint, contentColor = Navy),
                            contentPadding = androidx.compose.foundation.layout.PaddingValues(horizontal = 12.dp, vertical = 8.dp)
                        ) {
                            Text("Use as starting point", fontSize = 12.sp)
                        }
                    }
                }
            }

            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Text("Ideas to explore", color = Navy, fontSize = 19.sp, fontWeight = FontWeight.Bold)
                Text("Research first. Add an idea to a project when you're ready.", color = Muted, fontSize = 12.sp)
                researchIdeas.forEachIndexed { index, suggestion ->
                    Surface(
                        color = Color.White,
                        shape = RoundedCornerShape(18.dp),
                        border = androidx.compose.foundation.BorderStroke(1.dp, Line),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Row(
                            Modifier.fillMaxWidth().padding(14.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(11.dp)
                        ) {
                            Surface(
                                color = listOf(Color(0xFFE5F7EC), Color(0xFFE8E8FF), Color(0xFFFFF0D9))[index],
                                shape = RoundedCornerShape(12.dp),
                                modifier = Modifier.size(40.dp)
                            ) {
                                Box(contentAlignment = Alignment.Center) {
                                    Icon(Icons.Default.AutoAwesome, contentDescription = null, tint = Navy, modifier = Modifier.size(19.dp))
                                }
                            }
                            Text(suggestion, color = Navy, fontSize = 13.sp, lineHeight = 18.sp, modifier = Modifier.weight(1f))
                            OutlinedButton(
                                onClick = { onAddResearch(suggestion) },
                                shape = RoundedCornerShape(11.dp),
                                contentPadding = androidx.compose.foundation.layout.PaddingValues(horizontal = 9.dp, vertical = 7.dp)
                            ) {
                                Icon(Icons.Default.Add, contentDescription = null, modifier = Modifier.size(14.dp))
                                Spacer(Modifier.width(3.dp))
                                Text("Add", fontSize = 11.sp)
                            }
                        }
                    }
                }
            }

            Row(
                Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Column(verticalArrangement = Arrangement.spacedBy(3.dp)) {
                    Text("Your projects", color = Navy, fontSize = 18.sp, fontWeight = FontWeight.Bold)
                    Text("Continue where you left off", color = Muted, fontSize = 12.sp)
                }
                OutlinedButton(onClick = onSeeProjects, shape = RoundedCornerShape(12.dp)) {
                    Text("View all", fontSize = 12.sp)
                }
            }
            Surface(color = Color.White, shape = RoundedCornerShape(16.dp), border = androidx.compose.foundation.BorderStroke(1.dp, Line)) {
                Row(Modifier.fillMaxWidth().padding(14.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    Icon(Icons.Default.FolderOpen, contentDescription = null, tint = Green, modifier = Modifier.size(25.dp))
                    Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(3.dp)) {
                        Text("No recent project selected", color = Navy, fontWeight = FontWeight.SemiBold, fontSize = 13.sp)
                        Text("Ideas you add will start in blueprint review.", color = Muted, fontSize = 11.sp)
                    }
                }
            }
        }

        Surface(
            color = Color.White,
            shape = RoundedCornerShape(topStart = 20.dp, topEnd = 20.dp),
            border = androidx.compose.foundation.BorderStroke(1.dp, Line),
            modifier = Modifier.fillMaxWidth().padding(top = 10.dp, bottom = 8.dp)
        ) {
            Column(Modifier.fillMaxWidth().padding(10.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                if (showAttachMenu) {
                    Column(verticalArrangement = Arrangement.spacedBy(2.dp), modifier = Modifier.fillMaxWidth()) {
                        listOf(
                            "Files" to "Attach a file or project ZIP",
                            "Image" to "Add a screenshot or design reference",
                            "Web search" to "Research information on the web",
                            "Plugin" to "Use a connected plugin or MCP tool"
                        ).forEach { (title, subtitle) ->
                            Row(
                                Modifier.fillMaxWidth().background(Cream, RoundedCornerShape(12.dp)).padding(horizontal = 12.dp, vertical = 10.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(10.dp)
                            ) {
                                Text("+", color = Green, fontSize = 20.sp, fontWeight = FontWeight.Bold)
                                Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                                    Text(title, color = Navy, fontSize = 13.sp, fontWeight = FontWeight.SemiBold)
                                    Text(subtitle, color = Muted, fontSize = 10.sp)
                                }
                            }
                        }
                        Text("Attachment menu concept · connections are not active yet", color = Muted, fontSize = 10.sp, modifier = Modifier.padding(start = 4.dp, top = 3.dp))
                    }
                }
                Box(
                    modifier = Modifier.fillMaxWidth().height(76.dp).background(Cream, RoundedCornerShape(15.dp)).padding(12.dp)
                ) {
                    if (idea.isEmpty()) {
                        Text("Ask Vyoma to research an app idea…", color = Muted, fontSize = 14.sp)
                    }
                    BasicTextField(
                        value = idea,
                        onValueChange = onIdeaChange,
                        modifier = Modifier.fillMaxSize(),
                        textStyle = androidx.compose.ui.text.TextStyle(color = Navy, fontSize = 14.sp, lineHeight = 20.sp),
                        decorationBox = { inner -> inner() }
                    )
                }
                Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    IconButton(onClick = { showAttachMenu = !showAttachMenu }) {
                        Icon(Icons.Default.Add, contentDescription = "Open attachment options", tint = Green, modifier = Modifier.size(25.dp))
                    }
                    Text("Research", color = Muted, fontSize = 11.sp, modifier = Modifier.weight(1f))
                    Button(
                        onClick = onStart,
                        enabled = idea.isNotBlank(),
                        shape = RoundedCornerShape(13.dp),
                        colors = ButtonDefaults.buttonColors(containerColor = Navy, contentColor = Color.White),
                        contentPadding = androidx.compose.foundation.layout.PaddingValues(horizontal = 16.dp, vertical = 10.dp)
                    ) {
                        Text("Send")
                        Spacer(Modifier.width(6.dp))
                        Icon(Icons.Default.ArrowForward, contentDescription = null, modifier = Modifier.size(15.dp))
                    }
                }
            }
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
        UpdateSettingsCard()
        Text("Settings are visual placeholders at this stage.", color = Muted, fontSize = 12.sp)
    }
}
