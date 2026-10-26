package com.vyoma.app

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import android.widget.Toast
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
    StarterTemplate("Habit tracker", "Build consistent daily habits", "❋", Color(0xFFFFE3EA), "Create a habit tracker with daily check-ins, reminders and visual streak cards."),
    StarterTemplate("Study flashcards", "Learn faster every day", "✦", Color(0xFFE8E8FF), "Create a flashcard learning app with quick quizzes, progress tracking and score card."),
    StarterTemplate("Store catalogue", "Browse product inventory", "▦", Color(0xFFE0F0F7), "Create a shopping catalogue with product cards, search, filters and cart demo.")
)

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            MaterialTheme(
                colorScheme = lightColorScheme(
                    primary = Navy,
                    secondary = Green,
                    background = Cream,
                    surface = Color.White
                )
            ) {
                VyomaApp()
            }
        }
    }
}

@Composable
private fun VyomaApp() {
    val context = LocalContext.current
    var activeWorkspaceBlueprint by remember { mutableStateOf<ApprovedBlueprint?>(null) }
    var isViewingSettings by remember { mutableStateOf(false) }

    val approvedBlueprints = remember(context) {
        mutableStateListOf<ApprovedBlueprint>().apply {
            addAll(ProjectStore.loadBlueprints(context))
        }
    }

    // 1. If a project is selected, open The Unified Studio immediately
    if (activeWorkspaceBlueprint != null) {
        WorkspaceScreen(
            initialBlueprint = activeWorkspaceBlueprint!!,
            onBack = { activeWorkspaceBlueprint = null },
            onOpenSettings = {
                activeWorkspaceBlueprint = null
                isViewingSettings = true
            }
        )
        return
    }

    // 2. If Settings is selected, show Settings View
    if (isViewingSettings) {
        SettingsScreen(
            onBack = { isViewingSettings = false }
        )
        return
    }

    // 3. Otherwise, show the Minimal Projects Hub
    ProjectsHubScreen(
        blueprints = approvedBlueprints,
        onOpenProject = { blueprint ->
            activeWorkspaceBlueprint = blueprint
        },
        onCreateAndOpen = { name, goal ->
            val cleanName = name.take(30).ifBlank { "My Android App" }
            val newBp = ApprovedBlueprint(
                name = cleanName,
                goal = goal.ifBlank { "Android native app for $cleanName" },
                screens = "Home, Details, Settings",
                data = "User items, preferences",
                integrations = "None",
                permissions = "None",
                edgeCases = "Offline use, empty state",
                acceptanceTests = "App opens, core actions work",
                mcpTools = "GitHub MCP, SQLite DB, Compose M3"
            )
            approvedBlueprints.add(newBp)
            ProjectStore.saveBlueprints(context, approvedBlueprints)
            activeWorkspaceBlueprint = newBp
        },
        onDeleteProject = { bp ->
            approvedBlueprints.remove(bp)
            ProjectStore.saveBlueprints(context, approvedBlueprints)
            Toast.makeText(context, "Project removed", Toast.LENGTH_SHORT).show()
        },
        onOpenSettings = { isViewingSettings = true }
    )
}

@Composable
private fun ProjectsHubScreen(
    blueprints: List<ApprovedBlueprint>,
    onOpenProject: (ApprovedBlueprint) -> Unit,
    onCreateAndOpen: (String, String) -> Unit,
    onDeleteProject: (ApprovedBlueprint) -> Unit,
    onOpenSettings: () -> Unit
) {
    val context = LocalContext.current
    val coroutineScope = rememberCoroutineScope()
    val settings = remember { SettingsStore.loadSettings(context) }

    var appIdeaInput by remember { mutableStateOf("") }
    var isResearching by remember { mutableStateOf(false) }
    var researchReport by remember { mutableStateOf<IdeaResearchReport?>(null) }
    var researchError by remember { mutableStateOf<String?>(null) }

    // In-App OTA Update State
    var updateInfo by remember { mutableStateOf<AppUpdateInfo?>(null) }
    var isDownloadingUpdate by remember { mutableStateOf(false) }
    var downloadProgress by remember { mutableIntStateOf(0) }

    // Silently check GitHub Releases for updates on launch
    LaunchedEffect(Unit) {
        val res = AppUpdateService.checkForUpdates(
            context = context,
            owner = settings.githubOwner.ifBlank { null },
            token = settings.githubToken.ifBlank { null }
        )
        if (res.isSuccess) {
            val info = res.getOrNull()
            if (info != null && info.isUpdateAvailable) {
                updateInfo = info
            }
        }
    }

    Scaffold(
        containerColor = Cream,
        topBar = {
            Surface(color = Color.White, shadowElevation = 1.dp) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 12.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                        Surface(color = Mint, shape = RoundedCornerShape(10.dp), modifier = Modifier.size(34.dp)) {
                            Box(contentAlignment = Alignment.Center) {
                                Text("V", color = Green, fontWeight = FontWeight.ExtraBold, fontSize = 18.sp)
                            }
                        }
                        Column {
                            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                                Text("VYOMA", fontWeight = FontWeight.ExtraBold, fontSize = 16.sp, color = Navy, letterSpacing = 1.sp)
                                Surface(color = Color(0xFFE8F5E9), shape = RoundedCornerShape(6.dp)) {
                                    Text("STUDIO", fontSize = 9.sp, fontWeight = FontWeight.Bold, color = Green, modifier = Modifier.padding(horizontal = 5.dp, vertical = 2.dp))
                                }
                            }
                            Text("AI Native App Builder", fontSize = 11.sp, color = Muted)
                        }
                    }

                    IconButton(onClick = onOpenSettings) {
                        Icon(Icons.Default.Settings, contentDescription = "Settings", tint = Navy)
                    }
                }
            }
        }
    ) { insets ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(insets)
                .verticalScroll(rememberScrollState())
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(18.dp)
        ) {
            // In-App OTA Update Notification Banner (Appears automatically when GitHub release is found)
            if (updateInfo != null && updateInfo!!.isUpdateAvailable) {
                val update = updateInfo!!
                Card(
                    shape = RoundedCornerShape(18.dp),
                    colors = CardDefaults.cardColors(containerColor = Color(0xFFF3FAF6)),
                    border = androidx.compose.foundation.BorderStroke(1.5.dp, Green),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(modifier = Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                Icon(Icons.Default.CloudDownload, contentDescription = null, tint = Green, modifier = Modifier.size(20.dp))
                                Text("New Version Ready: v${update.latestVersionName}", fontWeight = FontWeight.Bold, fontSize = 14.sp, color = Green)
                            }
                            Surface(color = Mint, shape = RoundedCornerShape(6.dp)) {
                                Text("OTA Ready", fontSize = 9.sp, fontWeight = FontWeight.Bold, color = Green, modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp))
                            }
                        }

                        Text(
                            "A newer signed release is published on GitHub. Tap below to update in-app without downloading from browser.",
                            fontSize = 12.sp,
                            color = Navy,
                            lineHeight = 16.sp
                        )

                        if (isDownloadingUpdate) {
                            Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                                LinearProgressIndicator(
                                    progress = { downloadProgress / 100f },
                                    modifier = Modifier.fillMaxWidth(),
                                    color = Green
                                )
                                Text("Downloading APK: $downloadProgress%", fontSize = 11.sp, color = Muted)
                            }
                        } else {
                            Button(
                                onClick = {
                                    val url = update.apkDownloadUrl ?: return@Button
                                    val fileName = update.apkFileName ?: "Vyoma-Update.apk"
                                    isDownloadingUpdate = true
                                    coroutineScope.launch {
                                        val dlRes = AppUpdateService.downloadApk(context, url, fileName) { p ->
                                            downloadProgress = p
                                        }
                                        isDownloadingUpdate = false
                                        if (dlRes.isSuccess) {
                                            val file = dlRes.getOrNull()!!
                                            val instRes = AppUpdateService.promptInstallApk(context, file)
                                            if (instRes.isFailure) {
                                                Toast.makeText(context, instRes.exceptionOrNull()?.message, Toast.LENGTH_LONG).show()
                                            }
                                        } else {
                                            Toast.makeText(context, "Download failed: ${dlRes.exceptionOrNull()?.message}", Toast.LENGTH_LONG).show()
                                        }
                                    }
                                },
                                shape = RoundedCornerShape(10.dp),
                                colors = ButtonDefaults.buttonColors(containerColor = Green)
                            ) {
                                Icon(Icons.Default.CloudDownload, contentDescription = null, modifier = Modifier.size(16.dp))
                                Spacer(Modifier.width(6.dp))
                                Text("Install Update Now", fontWeight = FontWeight.Bold, fontSize = 12.sp)
                            }
                        }
                    }
                }
            }

            // Hero Create Card
            Card(
                shape = RoundedCornerShape(22.dp),
                colors = CardDefaults.cardColors(containerColor = Color.White),
                border = androidx.compose.foundation.BorderStroke(1.dp, Line),
                elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
            ) {
                Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        Icon(Icons.Default.AutoAwesome, contentDescription = null, tint = Green, modifier = Modifier.size(20.dp))
                        Text("What do you want to build?", fontWeight = FontWeight.Bold, fontSize = 16.sp, color = Navy)
                    }
                    Text(
                        "Describe your app idea. Vyoma will open the studio where you can chat with AI, see live preview, and build the APK.",
                        fontSize = 12.sp,
                        color = Muted,
                        lineHeight = 17.sp
                    )

                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(86.dp)
                            .background(Cream, RoundedCornerShape(14.dp))
                            .padding(12.dp)
                    ) {
                        if (appIdeaInput.isBlank()) {
                            Text(
                                "e.g. 'Build a personal finance expense tracker with category budgets' or 'Workout & exercise gym log'...",
                                color = Muted,
                                fontSize = 13.sp,
                                lineHeight = 18.sp
                            )
                        }
                        BasicTextField(
                            value = appIdeaInput,
                            onValueChange = { appIdeaInput = it },
                            modifier = Modifier.fillMaxSize(),
                            textStyle = androidx.compose.ui.text.TextStyle(color = Navy, fontSize = 13.sp, lineHeight = 19.sp)
                        )
                    }

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Button(
                            onClick = {
                                if (appIdeaInput.isNotBlank()) {
                                    val title = appIdeaInput
                                        .split(" ")
                                        .take(3)
                                        .joinToString(" ") { it.replaceFirstChar(Char::titlecase) }
                                    onCreateAndOpen(title, appIdeaInput.trim())
                                }
                            },
                            enabled = appIdeaInput.isNotBlank(),
                            shape = RoundedCornerShape(12.dp),
                            colors = ButtonDefaults.buttonColors(containerColor = Navy),
                            modifier = Modifier.weight(1.3f)
                        ) {
                            Icon(Icons.Default.RocketLaunch, contentDescription = null, modifier = Modifier.size(16.dp))
                            Spacer(Modifier.width(6.dp))
                            Text("Open Studio", fontWeight = FontWeight.Bold, fontSize = 13.sp)
                        }

                        OutlinedButton(
                            onClick = {
                                if (appIdeaInput.isNotBlank()) {
                                    isResearching = true
                                    researchError = null
                                    coroutineScope.launch {
                                        val res = AiService.researchAndValidateIdea(appIdeaInput, settings)
                                        isResearching = false
                                        if (res.isSuccess) {
                                            researchReport = res.getOrNull()
                                        } else {
                                            researchError = res.exceptionOrNull()?.message ?: "Research failed"
                                        }
                                    }
                                }
                            },
                            enabled = appIdeaInput.isNotBlank() && !isResearching,
                            shape = RoundedCornerShape(12.dp),
                            modifier = Modifier.weight(1f)
                        ) {
                            if (isResearching) {
                                CircularProgressIndicator(modifier = Modifier.size(13.dp), strokeWidth = 2.dp)
                            } else {
                                Text("AI Research", fontSize = 12.sp)
                            }
                        }
                    }

                    // Display research report if generated
                    if (researchReport != null) {
                        val report = researchReport!!
                        Surface(
                            color = Color(0xFFF3FAF6),
                            shape = RoundedCornerShape(14.dp),
                            border = androidx.compose.foundation.BorderStroke(1.dp, Color(0xFFD3EEDF)),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Column(modifier = Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Text(report.appName, fontWeight = FontWeight.Bold, fontSize = 13.sp, color = Green)
                                    Surface(color = Green, shape = RoundedCornerShape(6.dp)) {
                                        Text("${report.viabilityScore}% Viable", color = Color.White, fontSize = 10.sp, fontWeight = FontWeight.Bold, modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp))
                                    }
                                }
                                Text(report.honestVerdict, fontSize = 11.sp, color = Navy, lineHeight = 16.sp)

                                Button(
                                    onClick = {
                                        val bp = AiService.createBlueprintFromReport(report, appIdeaInput)
                                        onCreateAndOpen(bp.name, bp.goal)
                                    },
                                    shape = RoundedCornerShape(10.dp),
                                    colors = ButtonDefaults.buttonColors(containerColor = Green),
                                    modifier = Modifier.fillMaxWidth()
                                ) {
                                    Text("Add to Projects & Open Studio", fontSize = 12.sp, fontWeight = FontWeight.Bold)
                                }
                            }
                        }
                    }
                }
            }

            // Quick Starter Inspirations
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text("Quick Starters", fontSize = 13.sp, fontWeight = FontWeight.Bold, color = Muted, letterSpacing = 1.sp)
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .horizontalScroll(rememberScrollState()),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    templates.forEach { tmpl ->
                        Surface(
                            onClick = {
                                onCreateAndOpen(tmpl.title, tmpl.prompt)
                            },
                            shape = RoundedCornerShape(12.dp),
                            color = Color.White,
                            border = androidx.compose.foundation.BorderStroke(1.dp, Line)
                        ) {
                            Row(
                                modifier = Modifier.padding(horizontal = 12.dp, vertical = 10.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                Text(tmpl.symbol, fontSize = 16.sp, fontWeight = FontWeight.Bold, color = Navy)
                                Column {
                                    Text(tmpl.title, fontSize = 12.sp, fontWeight = FontWeight.Bold, color = Navy)
                                    Text(tmpl.subtitle, fontSize = 10.sp, color = Muted)
                                }
                            }
                        }
                    }
                }
            }

            // Your Projects Section
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text("Your Projects (${blueprints.size})", fontSize = 17.sp, fontWeight = FontWeight.Bold, color = Navy)
                }

                if (blueprints.isEmpty()) {
                    Surface(
                        color = Color.White,
                        shape = RoundedCornerShape(16.dp),
                        border = androidx.compose.foundation.BorderStroke(1.dp, Line),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Column(
                            modifier = Modifier.padding(24.dp),
                            horizontalAlignment = Alignment.CenterHorizontally,
                            verticalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            Icon(Icons.Default.FolderOpen, contentDescription = null, tint = Muted, modifier = Modifier.size(36.dp))
                            Text("No projects yet", fontWeight = FontWeight.Bold, fontSize = 14.sp, color = Navy)
                            Text("Type an idea above or pick a starter to open your first studio workspace.", fontSize = 12.sp, color = Muted)
                        }
                    }
                } else {
                    blueprints.forEach { bp ->
                        Card(
                            shape = RoundedCornerShape(16.dp),
                            colors = CardDefaults.cardColors(containerColor = Color.White),
                            border = androidx.compose.foundation.BorderStroke(1.dp, Line),
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable { onOpenProject(bp) }
                        ) {
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(14.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.SpaceBetween
                            ) {
                                Row(
                                    modifier = Modifier.weight(1f),
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                                ) {
                                    Surface(color = Mint, shape = RoundedCornerShape(10.dp), modifier = Modifier.size(38.dp)) {
                                        Box(contentAlignment = Alignment.Center) {
                                            Text(bp.name.take(1).uppercase(), color = Green, fontWeight = FontWeight.Bold, fontSize = 16.sp)
                                        }
                                    }
                                    Column(verticalArrangement = Arrangement.spacedBy(3.dp)) {
                                        Text(bp.name, fontWeight = FontWeight.Bold, fontSize = 14.sp, color = Navy)
                                        Text(bp.goal.take(50) + "...", fontSize = 11.sp, color = Muted)
                                    }
                                }

                                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                                    Button(
                                        onClick = { onOpenProject(bp) },
                                        shape = RoundedCornerShape(10.dp),
                                        colors = ButtonDefaults.buttonColors(containerColor = Navy),
                                        contentPadding = PaddingValues(horizontal = 12.dp, vertical = 6.dp)
                                    ) {
                                        Text("Open Studio", fontSize = 11.sp, fontWeight = FontWeight.Bold)
                                    }

                                    IconButton(
                                        onClick = { onDeleteProject(bp) },
                                        modifier = Modifier.size(30.dp)
                                    ) {
                                        Icon(Icons.Default.Delete, contentDescription = "Delete", tint = Muted, modifier = Modifier.size(16.dp))
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun SettingsScreen(
    onBack: () -> Unit
) {
    val context = LocalContext.current
    val coroutineScope = rememberCoroutineScope()
    val initialSettings = remember { SettingsStore.loadSettings(context) }

    var selectedProvider by remember { mutableStateOf(initialSettings.provider) }
    var apiKey by remember { mutableStateOf(initialSettings.apiKey) }
    var isApiKeyVisible by remember { mutableStateOf(false) }
    var modelName by remember { mutableStateOf(initialSettings.modelName) }
    var customEndpoint by remember { mutableStateOf(initialSettings.customEndpoint) }

    var githubToken by remember { mutableStateOf(initialSettings.githubToken) }
    var isGithubTokenVisible by remember { mutableStateOf(false) }
    var githubOwner by remember { mutableStateOf(initialSettings.githubOwner) }
    var isTestingGithub by remember { mutableStateOf(false) }
    var githubTestResult by remember { mutableStateOf<String?>(null) }

    // Manual In-App Update State
    var isCheckingUpdate by remember { mutableStateOf(false) }
    var manualUpdateStatus by remember { mutableStateOf<String?>(null) }
    var manualUpdateInfo by remember { mutableStateOf<AppUpdateInfo?>(null) }
    var isManualDownloading by remember { mutableStateOf(false) }

    Scaffold(
        containerColor = Cream,
        topBar = {
            Surface(color = Color.White, shadowElevation = 1.dp) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 12.dp, vertical = 10.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    IconButton(onClick = onBack) {
                        Icon(Icons.Default.ArrowBack, contentDescription = "Back", tint = Navy)
                    }
                    Text("Settings & Connections", color = Navy, fontSize = 18.sp, fontWeight = FontWeight.Bold)
                }
            }
        }
    ) { insets ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(insets)
                .verticalScroll(rememberScrollState())
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            // Section 1: In-App OTA Updates Card
            Card(
                shape = RoundedCornerShape(18.dp),
                colors = CardDefaults.cardColors(containerColor = Color.White),
                border = androidx.compose.foundation.BorderStroke(1.dp, Line),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            Icon(Icons.Default.CloudDownload, contentDescription = null, tint = Green)
                            Text("In-App OTA Updates", color = Navy, fontWeight = FontWeight.Bold, fontSize = 15.sp)
                        }
                        Surface(color = Cream, shape = RoundedCornerShape(8.dp)) {
                            Text("v${AppUpdateService.getCurrentVersionName(context)}", fontSize = 11.sp, fontWeight = FontWeight.Bold, color = Navy, modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp))
                        }
                    }
                    Text(
                        "Checks GitHub Releases for new signed APK builds. When available, update in-app without downloading from browser.",
                        fontSize = 12.sp,
                        color = Muted
                    )

                    OutlinedButton(
                        onClick = {
                            isCheckingUpdate = true
                            manualUpdateStatus = "Checking GitHub..."
                            coroutineScope.launch {
                                val res = AppUpdateService.checkForUpdates(
                                    context = context,
                                    owner = githubOwner.ifBlank { null },
                                    token = githubToken.ifBlank { null }
                                )
                                isCheckingUpdate = false
                                if (res.isSuccess) {
                                    val info = res.getOrNull()
                                    if (info != null && info.isUpdateAvailable) {
                                        manualUpdateInfo = info
                                        manualUpdateStatus = "🎉 New version available: v${info.latestVersionName}"
                                    } else {
                                        manualUpdateStatus = "✅ You're up to date! (v${info?.currentVersionName})"
                                    }
                                } else {
                                    manualUpdateStatus = "Check failed: ${res.exceptionOrNull()?.message}"
                                }
                            }
                        },
                        shape = RoundedCornerShape(10.dp)
                    ) {
                        if (isCheckingUpdate) {
                            CircularProgressIndicator(modifier = Modifier.size(13.dp), strokeWidth = 2.dp)
                            Spacer(Modifier.width(6.dp))
                        }
                        Text("Check for Updates Now", fontSize = 12.sp)
                    }

                    if (manualUpdateStatus != null) {
                        Text(manualUpdateStatus!!, fontSize = 12.sp, color = if (manualUpdateStatus!!.startsWith("✅")) Green else Navy)
                    }

                    if (manualUpdateInfo != null && manualUpdateInfo!!.isUpdateAvailable) {
                        Button(
                            onClick = {
                                val url = manualUpdateInfo?.apkDownloadUrl ?: return@Button
                                val fileName = manualUpdateInfo?.apkFileName ?: "Vyoma-Update.apk"
                                isManualDownloading = true
                                coroutineScope.launch {
                                    val dlRes = AppUpdateService.downloadApk(context, url, fileName) {}
                                    isManualDownloading = false
                                    if (dlRes.isSuccess) {
                                        val file = dlRes.getOrNull()!!
                                        AppUpdateService.promptInstallApk(context, file)
                                    } else {
                                        Toast.makeText(context, "Download failed", Toast.LENGTH_SHORT).show()
                                    }
                                }
                            },
                            colors = ButtonDefaults.buttonColors(containerColor = Green),
                            shape = RoundedCornerShape(10.dp)
                        ) {
                            if (isManualDownloading) {
                                CircularProgressIndicator(modifier = Modifier.size(13.dp), color = Color.White, strokeWidth = 2.dp)
                                Spacer(Modifier.width(6.dp))
                            }
                            Text("Install v${manualUpdateInfo!!.latestVersionName} Now", fontSize = 12.sp, fontWeight = FontWeight.Bold)
                        }
                    }
                }
            }

            // Section 2: AI Provider Selection
            Card(
                shape = RoundedCornerShape(18.dp),
                colors = CardDefaults.cardColors(containerColor = Color.White),
                border = androidx.compose.foundation.BorderStroke(1.dp, Line),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        Icon(Icons.Default.AutoAwesome, contentDescription = null, tint = Green)
                        Text("AI Model Provider", color = Navy, fontWeight = FontWeight.Bold, fontSize = 15.sp)
                    }

                    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        AiProvider.values().forEach { prov ->
                            val isSelected = prov == selectedProvider
                            Surface(
                                onClick = {
                                    selectedProvider = prov
                                    modelName = prov.defaultModel
                                },
                                shape = RoundedCornerShape(12.dp),
                                color = if (isSelected) Mint else Cream,
                                border = if (isSelected) androidx.compose.foundation.BorderStroke(1.5.dp, Green) else null,
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Row(
                                    modifier = Modifier.padding(horizontal = 14.dp, vertical = 10.dp),
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.SpaceBetween
                                ) {
                                    Column {
                                        Text(prov.displayName, color = Navy, fontWeight = FontWeight.Bold, fontSize = 13.sp)
                                        Text("Default: ${prov.defaultModel}", color = Muted, fontSize = 11.sp)
                                    }
                                    if (isSelected) {
                                        Icon(Icons.Default.Check, contentDescription = null, tint = Green, modifier = Modifier.size(18.dp))
                                    }
                                }
                            }
                        }
                    }

                    OutlinedTextField(
                        value = apiKey,
                        onValueChange = { apiKey = it },
                        label = { Text("${selectedProvider.displayName} API Key") },
                        placeholder = { Text("Enter your API key") },
                        visualTransformation = if (isApiKeyVisible) VisualTransformation.None else PasswordVisualTransformation(),
                        trailingIcon = {
                            IconButton(onClick = { isApiKeyVisible = !isApiKeyVisible }) {
                                Icon(if (isApiKeyVisible) Icons.Default.VisibilityOff else Icons.Default.Visibility, contentDescription = null, tint = Muted)
                            }
                        },
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(12.dp)
                    )

                    OutlinedTextField(
                        value = modelName,
                        onValueChange = { modelName = it },
                        label = { Text("Model Name") },
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(12.dp)
                    )

                    if (selectedProvider == AiProvider.CUSTOM) {
                        OutlinedTextField(
                            value = customEndpoint,
                            onValueChange = { customEndpoint = it },
                            label = { Text("Custom Base URL") },
                            modifier = Modifier.fillMaxWidth(),
                            shape = RoundedCornerShape(12.dp)
                        )
                    }
                }
            }

            // Section 3: GitHub Connection
            Card(
                shape = RoundedCornerShape(18.dp),
                colors = CardDefaults.cardColors(containerColor = Color.White),
                border = androidx.compose.foundation.BorderStroke(1.dp, Line),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        Icon(Icons.Default.CloudUpload, contentDescription = null, tint = Green)
                        Text("GitHub Cloud APK Delivery", color = Navy, fontWeight = FontWeight.Bold, fontSize = 15.sp)
                    }
                    Text("Required for pushing project files and compiling APKs in cloud workers.", fontSize = 12.sp, color = Muted)

                    OutlinedTextField(
                        value = githubOwner,
                        onValueChange = { githubOwner = it },
                        label = { Text("GitHub Username / Org") },
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(12.dp)
                    )

                    OutlinedTextField(
                        value = githubToken,
                        onValueChange = { githubToken = it },
                        label = { Text("Personal Access Token (PAT)") },
                        placeholder = { Text("ghp_...") },
                        visualTransformation = if (isGithubTokenVisible) VisualTransformation.None else PasswordVisualTransformation(),
                        trailingIcon = {
                            IconButton(onClick = { isGithubTokenVisible = !isGithubTokenVisible }) {
                                Icon(if (isGithubTokenVisible) Icons.Default.VisibilityOff else Icons.Default.Visibility, contentDescription = null, tint = Muted)
                            }
                        },
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(12.dp)
                    )

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        OutlinedButton(
                            onClick = {
                                if (githubToken.isBlank()) {
                                    Toast.makeText(context, "Please enter token first", Toast.LENGTH_SHORT).show()
                                    return@OutlinedButton
                                }
                                isTestingGithub = true
                                githubTestResult = "Connecting..."
                                coroutineScope.launch {
                                    val res = GitHubService.getAuthenticatedUser(githubToken)
                                    isTestingGithub = false
                                    if (res.isSuccess) {
                                        val user = res.getOrNull()
                                        githubOwner = user?.login ?: githubOwner
                                        githubTestResult = "Connected as @${user?.login}"
                                    } else {
                                        githubTestResult = "Failed: ${res.exceptionOrNull()?.message}"
                                    }
                                }
                            },
                            shape = RoundedCornerShape(10.dp)
                        ) {
                            if (isTestingGithub) {
                                CircularProgressIndicator(modifier = Modifier.size(13.dp), strokeWidth = 2.dp)
                                Spacer(Modifier.width(6.dp))
                            }
                            Text("Test Connection", fontSize = 12.sp)
                        }

                        if (githubTestResult != null) {
                            Text(
                                text = githubTestResult!!,
                                fontSize = 11.sp,
                                color = if (githubTestResult!!.startsWith("Connected")) Green else Color.Red
                            )
                        }
                    }
                }
            }

            // Zero RAM Guarantee Card
            Surface(
                color = Color(0xFFF3FAF6),
                shape = RoundedCornerShape(16.dp),
                border = androidx.compose.foundation.BorderStroke(1.dp, Color(0xFFD3EEDF)),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(modifier = Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        Icon(Icons.Default.BatteryChargingFull, contentDescription = null, tint = Green, modifier = Modifier.size(18.dp))
                        Text("Zero Phone RAM Overhead", fontWeight = FontWeight.Bold, fontSize = 13.sp, color = Green)
                    }
                    Text(
                        "Whether your phone has 3 GB or 12 GB RAM, performance stays buttery-smooth because app builds run on high-performance cloud infrastructure.",
                        fontSize = 12.sp,
                        color = Navy,
                        lineHeight = 17.sp
                    )
                }
            }

            // Save Preferences Button
            Button(
                onClick = {
                    val newSettings = initialSettings.copy(
                        provider = selectedProvider,
                        apiKey = apiKey,
                        modelName = modelName,
                        customEndpoint = customEndpoint,
                        githubToken = githubToken,
                        githubOwner = githubOwner
                    )
                    SettingsStore.saveSettings(context, newSettings)
                    Toast.makeText(context, "Settings saved successfully!", Toast.LENGTH_SHORT).show()
                    onBack()
                },
                shape = RoundedCornerShape(14.dp),
                colors = ButtonDefaults.buttonColors(containerColor = Navy),
                modifier = Modifier
                    .fillMaxWidth()
                    .height(48.dp)
            ) {
                Icon(Icons.Default.Check, contentDescription = null, modifier = Modifier.size(18.dp))
                Spacer(Modifier.width(8.dp))
                Text("Save Preferences", fontWeight = FontWeight.Bold, fontSize = 14.sp)
            }
        }
    }
}
