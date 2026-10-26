package com.vyoma.app

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.widget.Toast
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
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
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.launch

private val DarkCodeBg = Color(0xFF181C24)
private val DarkCodeSurface = Color(0xFF222734)
private val CodeGreen = Color(0xFF7CE38B)
private val CodeBlue = Color(0xFF79B8FF)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun WorkspaceScreen(
    initialBlueprint: ApprovedBlueprint,
    onBack: () -> Unit,
    onOpenSettings: () -> Unit
) {
    val context = LocalContext.current
    val coroutineScope = rememberCoroutineScope()
    val settings = remember { SettingsStore.loadSettings(context) }

    var currentBlueprint by remember { mutableStateOf(initialBlueprint) }
    var selectedPalette by remember { mutableStateOf(ThemePalettes.getById(settings.selectedPaletteId)) }
    
    // Unified 3-Pillar Switcher: 0: Chat, 1: Preview, 2: Files
    var selectedTab by remember { mutableIntStateOf(1) } // Default to Live Preview for instant tactile delight

    // Chat state
    val messages = remember {
        mutableStateListOf(
            ChatMessage(
                isUser = false,
                text = "Welcome to **${initialBlueprint.name}**! I'm your AI builder. Ask me to add screens, change colors, or build features. You can switch between **Chat**, **Preview**, and **Files** anytime above."
            )
        )
    }
    var inputText by remember { mutableStateOf("") }
    var isAiGenerating by remember { mutableStateOf(false) }

    // Code files state
    var generatedFiles by remember {
        mutableStateOf(AiService.generateStarterCodeFiles(initialBlueprint, selectedPalette))
    }
    var selectedFileKey by remember {
        mutableStateOf(generatedFiles.keys.firstOrNull() ?: "")
    }

    // GitHub Push & Cloud Build state
    var isPushingToGitHub by remember { mutableStateOf(false) }
    var pushStatusMessage by remember { mutableStateOf<String?>(null) }
    var workflowRuns by remember { mutableStateOf<List<WorkflowRunSummary>>(emptyList()) }
    var isCheckingRuns by remember { mutableStateOf(false) }
    var showBuildSheet by remember { mutableStateOf(false) }

    val appRepoName = remember(currentBlueprint.name) {
        val sanitized = currentBlueprint.name.lowercase().replace("[^a-z0-9-]".toRegex(), "-")
        "${settings.defaultRepoPrefix}$sanitized".take(40)
    }

    // Helper function to trigger cloud build directly from anywhere
    fun triggerCloudBuild() {
        if (settings.githubToken.isBlank()) {
            Toast.makeText(context, "Please set GitHub Token in Settings to build APK", Toast.LENGTH_LONG).show()
            onOpenSettings()
            return
        }

        val owner = settings.githubOwner.ifBlank {
            Toast.makeText(context, "Please set GitHub Username in Settings", Toast.LENGTH_LONG).show()
            onOpenSettings()
            return
        }

        isPushingToGitHub = true
        pushStatusMessage = "Creating/verifying GitHub repo '$appRepoName'..."

        coroutineScope.launch {
            val repoRes = GitHubService.ensureRepository(settings.githubToken, owner, appRepoName)
            if (repoRes.isFailure) {
                isPushingToGitHub = false
                pushStatusMessage = "Repo error: " + (repoRes.exceptionOrNull()?.message ?: "Failed")
                Toast.makeText(context, pushStatusMessage, Toast.LENGTH_LONG).show()
                return@launch
            }

            pushStatusMessage = "Pushing project files to GitHub..."
            val pushRes = GitHubService.pushMultipleFiles(
                settings.githubToken,
                owner,
                appRepoName,
                generatedFiles
            ) { current, total, name ->
                pushStatusMessage = "Pushing ($current/$total): $name"
            }

            isPushingToGitHub = false
            if (pushRes.isSuccess) {
                pushStatusMessage = "✅ Code pushed! Cloud APK build triggered."
                Toast.makeText(context, "Cloud APK build running on GitHub Actions!", Toast.LENGTH_LONG).show()
                // Refresh runs
                isCheckingRuns = true
                val runs = GitHubService.getWorkflowRuns(settings.githubToken, owner, appRepoName)
                isCheckingRuns = false
                if (runs.isSuccess) {
                    workflowRuns = runs.getOrDefault(emptyList())
                }
            } else {
                pushStatusMessage = "❌ Push failed: " + (pushRes.exceptionOrNull()?.message ?: "Error")
                Toast.makeText(context, pushStatusMessage, Toast.LENGTH_LONG).show()
            }
        }
    }

    Scaffold(
        containerColor = Cream,
        topBar = {
            Surface(
                color = Color.White,
                shadowElevation = 2.dp
            ) {
                Column {
                    // Header Bar
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 10.dp, vertical = 8.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            IconButton(onClick = onBack) {
                                Icon(Icons.Default.ArrowBack, contentDescription = "Back", tint = Navy)
                            }
                            Column {
                                Text(
                                    text = currentBlueprint.name,
                                    fontSize = 16.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = Navy
                                )
                                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                                    Surface(
                                        color = Mint,
                                        shape = RoundedCornerShape(6.dp)
                                    ) {
                                        Text(
                                            "Compose M3",
                                            fontSize = 9.sp,
                                            fontWeight = FontWeight.Bold,
                                            color = Green,
                                            modifier = Modifier.padding(horizontal = 5.dp, vertical = 2.dp)
                                        )
                                    }
                                    Text(
                                        "✦ ${selectedPalette.name}",
                                        fontSize = 10.sp,
                                        color = Muted
                                    )
                                }
                            }
                        }

                        // Right Actions: Universal Build Pill & Settings
                        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                            // Universal Cloud Build Pill
                            Surface(
                                onClick = {
                                    if (isPushingToGitHub) {
                                        showBuildSheet = true
                                    } else {
                                        triggerCloudBuild()
                                    }
                                },
                                shape = RoundedCornerShape(12.dp),
                                color = if (isPushingToGitHub) Color(0xFFFFF3CD) else if (workflowRuns.firstOrNull()?.conclusion == "success") Mint else Navy
                            ) {
                                Row(
                                    modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp),
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(4.dp)
                                ) {
                                    if (isPushingToGitHub || isCheckingRuns) {
                                        CircularProgressIndicator(modifier = Modifier.size(13.dp), strokeWidth = 2.dp, color = Navy)
                                        Text("Building...", fontSize = 11.sp, fontWeight = FontWeight.Bold, color = Navy)
                                    } else if (workflowRuns.firstOrNull()?.conclusion == "success") {
                                        Icon(Icons.Default.CheckCircle, contentDescription = null, tint = Green, modifier = Modifier.size(14.dp))
                                        Text("Get APK", fontSize = 11.sp, fontWeight = FontWeight.Bold, color = Green)
                                    } else {
                                        Icon(Icons.Default.CloudUpload, contentDescription = null, tint = Mint, modifier = Modifier.size(14.dp))
                                        Text("Build APK", fontSize = 11.sp, fontWeight = FontWeight.Bold, color = Color.White)
                                    }
                                }
                            }

                            // Build Status Info Button
                            IconButton(onClick = { showBuildSheet = true }) {
                                Icon(Icons.Default.Info, contentDescription = "Build Status", tint = Muted, modifier = Modifier.size(20.dp))
                            }

                            IconButton(onClick = onOpenSettings) {
                                Icon(Icons.Default.Settings, contentDescription = "Settings", tint = Muted, modifier = Modifier.size(20.dp))
                            }
                        }
                    }

                    // ================= UNIFIED 3-PILLAR SWITCHER =================
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 12.dp, vertical = 6.dp),
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        UnifiedPill(
                            title = "Chat",
                            icon = Icons.Default.ChatBubbleOutline,
                            isSelected = selectedTab == 0,
                            modifier = Modifier.weight(1f),
                            onClick = { selectedTab = 0 }
                        )
                        UnifiedPill(
                            title = "Preview",
                            icon = Icons.Default.Smartphone,
                            isSelected = selectedTab == 1,
                            modifier = Modifier.weight(1f),
                            onClick = { selectedTab = 1 }
                        )
                        UnifiedPill(
                            title = "Files (${generatedFiles.size})",
                            icon = Icons.Default.Code,
                            isSelected = selectedTab == 2,
                            modifier = Modifier.weight(1f),
                            onClick = { selectedTab = 2 }
                        )
                    }
                }
            }
        }
    ) { padding ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
        ) {
            when (selectedTab) {
                // 1. CHAT PILLAR
                0 -> UnifiedChatTab(
                    messages = messages,
                    inputText = inputText,
                    onInputTextChange = { inputText = it },
                    isGenerating = isAiGenerating,
                    onSendMessage = { prompt ->
                        if (prompt.isNotBlank()) {
                            val userMsg = ChatMessage(isUser = true, text = prompt)
                            messages.add(userMsg)
                            inputText = ""
                            isAiGenerating = true

                            coroutineScope.launch {
                                val replyRes = AiService.sendMessage(settings, messages, currentBlueprint)
                                isAiGenerating = false
                                if (replyRes.isSuccess) {
                                    val reply = replyRes.getOrNull() ?: ""
                                    messages.add(ChatMessage(isUser = false, text = reply))
                                    generatedFiles = AiService.generateStarterCodeFiles(currentBlueprint, selectedPalette)
                                } else {
                                    val err = replyRes.exceptionOrNull()?.message ?: "Error contacting AI"
                                    messages.add(ChatMessage(isUser = false, text = "⚠️ $err"))
                                }
                            }
                        }
                    },
                    onQuickPrompt = { prompt -> inputText = prompt },
                    onNavigateToPreview = { selectedTab = 1 },
                    onNavigateToFiles = { selectedTab = 2 },
                    onTriggerBuild = { triggerCloudBuild() }
                )

                // 2. PREVIEW PILLAR
                1 -> UnifiedPreviewTab(
                    blueprint = currentBlueprint,
                    palette = selectedPalette,
                    onSelectPalette = { newPalette ->
                        selectedPalette = newPalette
                        generatedFiles = AiService.generateStarterCodeFiles(currentBlueprint, newPalette)
                        SettingsStore.saveSettings(context, settings.copy(selectedPaletteId = newPalette.id))
                        Toast.makeText(context, "Applied ${newPalette.name} theme!", Toast.LENGTH_SHORT).show()
                    },
                    onTriggerBuild = { triggerCloudBuild() },
                    onNavigateToChat = { selectedTab = 0 },
                    onNavigateToFiles = { selectedTab = 2 }
                )

                // 3. FILES PILLAR
                2 -> UnifiedFilesTab(
                    files = generatedFiles,
                    selectedFile = selectedFileKey,
                    onSelectFile = { selectedFileKey = it },
                    onTriggerBuild = { triggerCloudBuild() }
                )
            }
        }

        // Build Status Bottom Sheet / Dialog
        if (showBuildSheet) {
            AlertDialog(
                onDismissRequest = { showBuildSheet = false },
                title = {
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        Icon(Icons.Default.CloudUpload, contentDescription = null, tint = Green)
                        Text("Cloud APK Pipeline", fontWeight = FontWeight.Bold, fontSize = 17.sp, color = Navy)
                    }
                },
                text = {
                    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                        Text(
                            "Builds run completely on cloud workers. Zero phone battery or RAM usage.",
                            fontSize = 12.sp,
                            color = Muted
                        )
                        Surface(
                            color = Cream,
                            shape = RoundedCornerShape(8.dp),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Text(
                                text = "Target: github.com/${settings.githubOwner.ifBlank { "..." }}/$appRepoName",
                                fontSize = 11.sp,
                                fontFamily = FontFamily.Monospace,
                                color = Navy,
                                modifier = Modifier.padding(8.dp)
                            )
                        }

                        if (pushStatusMessage != null) {
                            Text(
                                text = pushStatusMessage!!,
                                fontSize = 12.sp,
                                color = if (pushStatusMessage!!.startsWith("❌")) Color.Red else Green
                            )
                        }

                        if (workflowRuns.isNotEmpty()) {
                            Text("Recent Build Runs:", fontWeight = FontWeight.Bold, fontSize = 12.sp, color = Navy)
                            workflowRuns.take(2).forEach { run ->
                                Surface(
                                    color = Color(0xFFF7F9F8),
                                    shape = RoundedCornerShape(8.dp),
                                    modifier = Modifier.fillMaxWidth()
                                ) {
                                    Row(
                                        modifier = Modifier.padding(8.dp),
                                        verticalAlignment = Alignment.CenterVertically,
                                        horizontalArrangement = Arrangement.SpaceBetween
                                    ) {
                                        Column {
                                            Text(run.name, fontWeight = FontWeight.SemiBold, fontSize = 12.sp, color = Navy)
                                            Text("Status: ${run.status} · Conclusion: ${run.conclusion ?: "Running..."}", fontSize = 10.sp, color = Muted)
                                        }
                                        if (run.conclusion == "success") {
                                            Button(
                                                onClick = {
                                                    val browserIntent = Intent(Intent.ACTION_VIEW, Uri.parse(run.htmlUrl))
                                                    context.startActivity(browserIntent)
                                                },
                                                shape = RoundedCornerShape(6.dp),
                                                colors = ButtonDefaults.buttonColors(containerColor = Green),
                                                contentPadding = PaddingValues(horizontal = 8.dp, vertical = 4.dp)
                                            ) {
                                                Text("Get APK", fontSize = 10.sp)
                                            }
                                        }
                                    }
                                }
                            }
                        }
                    }
                },
                confirmButton = {
                    Button(
                        onClick = {
                            showBuildSheet = false
                            triggerCloudBuild()
                        },
                        colors = ButtonDefaults.buttonColors(containerColor = Navy)
                    ) {
                        Text("Trigger New Build")
                    }
                },
                dismissButton = {
                    TextButton(onClick = { showBuildSheet = false }) {
                        Text("Close")
                    }
                }
            )
        }
    }
}

@Composable
private fun UnifiedPill(
    title: String,
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    isSelected: Boolean,
    modifier: Modifier = Modifier,
    onClick: () -> Unit
) {
    Surface(
        onClick = onClick,
        shape = RoundedCornerShape(12.dp),
        color = if (isSelected) Navy else Color(0xFFF0F4F2),
        modifier = modifier.height(38.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxSize(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.Center
        ) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                tint = if (isSelected) Mint else Muted,
                modifier = Modifier.size(15.dp)
            )
            Spacer(Modifier.width(4.dp))
            Text(
                text = title,
                fontSize = 12.sp,
                fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium,
                color = if (isSelected) Color.White else Navy
            )
        }
    }
}

// ================= PILLAR 1: CHAT =================
@Composable
private fun UnifiedChatTab(
    messages: List<ChatMessage>,
    inputText: String,
    onInputTextChange: (String) -> Unit,
    isGenerating: Boolean,
    onSendMessage: (String) -> Unit,
    onQuickPrompt: (String) -> Unit,
    onNavigateToPreview: () -> Unit,
    onNavigateToFiles: () -> Unit,
    onTriggerBuild: () -> Unit
) {
    val listState = rememberLazyListState()

    LaunchedEffect(messages.size) {
        if (messages.isNotEmpty()) {
            listState.animateScrollToItem(messages.size - 1)
        }
    }

    Column(modifier = Modifier.fillMaxSize()) {
        // Quick Prompts Row
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .horizontalScroll(rememberScrollState())
                .padding(horizontal = 12.dp, vertical = 6.dp),
            horizontalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            listOf(
                "✨ Switch to Fintech theme",
                "➕ Add user profile screen",
                "🎨 Make cards rounded",
                "⚡ Add dynamic search bar",
                "🚀 Build cloud APK now"
            ).forEach { prompt ->
                Surface(
                    onClick = {
                        if (prompt.contains("Build cloud APK")) {
                            onTriggerBuild()
                        } else {
                            onQuickPrompt(prompt)
                        }
                    },
                    shape = RoundedCornerShape(16.dp),
                    color = Color.White,
                    border = androidx.compose.foundation.BorderStroke(1.dp, Line)
                ) {
                    Text(
                        text = prompt,
                        fontSize = 11.sp,
                        color = Navy,
                        modifier = Modifier.padding(horizontal = 10.dp, vertical = 5.dp)
                    )
                }
            }
        }

        // Messages List
        LazyColumn(
            state = listState,
            modifier = Modifier
                .weight(1f)
                .fillMaxWidth()
                .padding(horizontal = 12.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            items(messages) { msg ->
                UnifiedChatMessageBubble(
                    message = msg,
                    onNavigateToPreview = onNavigateToPreview,
                    onNavigateToFiles = onNavigateToFiles,
                    onTriggerBuild = onTriggerBuild
                )
            }

            if (isGenerating) {
                item {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(8.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        CircularProgressIndicator(modifier = Modifier.size(16.dp), color = Green, strokeWidth = 2.dp)
                        Text("Vyoma AI is reasoning & updating Compose files...", fontSize = 12.sp, color = Muted)
                    }
                }
            }
        }

        // Input Field Bar
        Surface(
            color = Color.White,
            shadowElevation = 8.dp
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 10.dp, vertical = 8.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Box(
                    modifier = Modifier
                        .weight(1f)
                        .background(Cream, RoundedCornerShape(20.dp))
                        .padding(horizontal = 14.dp, vertical = 10.dp)
                ) {
                    if (inputText.isBlank()) {
                        Text("Ask AI to change design, add screens, or features...", color = Muted, fontSize = 13.sp)
                    }
                    BasicTextField(
                        value = inputText,
                        onValueChange = onInputTextChange,
                        modifier = Modifier.fillMaxWidth(),
                        textStyle = androidx.compose.ui.text.TextStyle(color = Navy, fontSize = 13.sp)
                    )
                }

                IconButton(
                    onClick = { onSendMessage(inputText) },
                    enabled = inputText.isNotBlank() && !isGenerating,
                    modifier = Modifier
                        .background(if (inputText.isNotBlank()) Green else Color.LightGray, CircleShape)
                        .size(40.dp)
                ) {
                    Icon(
                        Icons.Default.ArrowForward,
                        contentDescription = "Send",
                        tint = Color.White,
                        modifier = Modifier.size(18.dp)
                    )
                }
            }
        }
    }
}

@Composable
private fun UnifiedChatMessageBubble(
    message: ChatMessage,
    onNavigateToPreview: () -> Unit,
    onNavigateToFiles: () -> Unit,
    onTriggerBuild: () -> Unit
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = if (message.isUser) Arrangement.End else Arrangement.Start
    ) {
        if (!message.isUser) {
            Surface(
                color = Mint,
                shape = CircleShape,
                modifier = Modifier
                    .size(28.dp)
                    .padding(top = 2.dp)
            ) {
                Box(contentAlignment = Alignment.Center) {
                    Icon(Icons.Default.AutoAwesome, contentDescription = null, tint = Navy, modifier = Modifier.size(16.dp))
                }
            }
            Spacer(Modifier.width(6.dp))
        }

        Surface(
            color = if (message.isUser) Navy else Color.White,
            shape = RoundedCornerShape(
                topStart = 16.dp,
                topEnd = 16.dp,
                bottomStart = if (message.isUser) 16.dp else 4.dp,
                bottomEnd = if (message.isUser) 4.dp else 16.dp
            ),
            border = if (!message.isUser) androidx.compose.foundation.BorderStroke(1.dp, Line) else null,
            modifier = Modifier.widthIn(max = 300.dp)
        ) {
            Column(modifier = Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                Text(
                    text = message.text,
                    fontSize = 13.sp,
                    lineHeight = 19.sp,
                    color = if (message.isUser) Color.White else Navy
                )

                // Inline quick action chips inside assistant bubble
                if (!message.isUser) {
                    Spacer(Modifier.height(2.dp))
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .horizontalScroll(rememberScrollState()),
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        Surface(
                            onClick = onNavigateToPreview,
                            shape = RoundedCornerShape(8.dp),
                            color = Cream,
                            border = androidx.compose.foundation.BorderStroke(1.dp, Line)
                        ) {
                            Row(modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp), verticalAlignment = Alignment.CenterVertically) {
                                Icon(Icons.Default.Smartphone, contentDescription = null, tint = Green, modifier = Modifier.size(12.dp))
                                Spacer(Modifier.width(3.dp))
                                Text("Preview", fontSize = 10.sp, fontWeight = FontWeight.Bold, color = Green)
                            }
                        }

                        Surface(
                            onClick = onNavigateToFiles,
                            shape = RoundedCornerShape(8.dp),
                            color = Cream,
                            border = androidx.compose.foundation.BorderStroke(1.dp, Line)
                        ) {
                            Row(modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp), verticalAlignment = Alignment.CenterVertically) {
                                Icon(Icons.Default.Code, contentDescription = null, tint = Navy, modifier = Modifier.size(12.dp))
                                Spacer(Modifier.width(3.dp))
                                Text("Files", fontSize = 10.sp, fontWeight = FontWeight.Bold, color = Navy)
                            }
                        }

                        Surface(
                            onClick = onTriggerBuild,
                            shape = RoundedCornerShape(8.dp),
                            color = Mint
                        ) {
                            Row(modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp), verticalAlignment = Alignment.CenterVertically) {
                                Icon(Icons.Default.CloudUpload, contentDescription = null, tint = Green, modifier = Modifier.size(12.dp))
                                Spacer(Modifier.width(3.dp))
                                Text("Build APK", fontSize = 10.sp, fontWeight = FontWeight.Bold, color = Green)
                            }
                        }
                    }
                }
            }
        }
    }
}

// ================= PILLAR 2: PREVIEW =================
@Composable
private fun UnifiedPreviewTab(
    blueprint: ApprovedBlueprint,
    palette: ThemePalette,
    onSelectPalette: (ThemePalette) -> Unit,
    onTriggerBuild: () -> Unit,
    onNavigateToChat: () -> Unit,
    onNavigateToFiles: () -> Unit
) {
    val screenNames = remember(blueprint.screens) {
        blueprint.screens.split(",").map { it.trim() }.filter { it.isNotBlank() }.ifEmpty { listOf("Home", "Details", "Settings") }
    }
    var selectedScreenIndex by remember { mutableIntStateOf(0) }
    var isDarkTheme by remember { mutableStateOf(palette.isDark) }

    // Dynamic app customizer states
    var appTitle by remember(blueprint.name) { mutableStateOf(blueprint.name) }
    var appGoal by remember(blueprint.goal) { mutableStateOf(blueprint.goal) }
    var selectedCategoryIcon by remember { mutableStateOf("Rocket") }
    var showCustomizerSheet by remember { mutableStateOf(false) }

    // Interactive mock state
    var demoItems by remember(blueprint.name) {
        mutableStateOf(listOf("Verify feature blueprint", "Configure Room persistence", "Run automated test verification"))
    }
    var checkedItems by remember { mutableStateOf(setOf<Int>(0)) }
    var newItemInput by remember { mutableStateOf("") }

    val phoneBg = if (isDarkTheme) Color(0xFF121212) else palette.background
    val phoneSurface = if (isDarkTheme) Color(0xFF1E1E1E) else palette.surface
    val phoneTextColor = if (isDarkTheme) Color(0xFFE0E0E0) else palette.onSurface
    val phoneSubText = if (isDarkTheme) Color(0xFFA0A0A0) else Muted
    val primaryColor = palette.primary
    val accentColor = palette.accent

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(14.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        // Theme Palettes Bar
        Card(
            shape = RoundedCornerShape(16.dp),
            colors = CardDefaults.cardColors(containerColor = Color.White),
            border = androidx.compose.foundation.BorderStroke(1.dp, Line),
            modifier = Modifier.fillMaxWidth()
        ) {
            Column(modifier = Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        Icon(Icons.Default.Palette, contentDescription = null, tint = primaryColor, modifier = Modifier.size(16.dp))
                        Text("Instant Material 3 Theme", fontWeight = FontWeight.Bold, fontSize = 13.sp, color = Navy)
                    }
                    Surface(color = Mint, shape = RoundedCornerShape(6.dp)) {
                        Text("WCAG AA Pass", fontSize = 9.sp, fontWeight = FontWeight.Bold, color = Green, modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp))
                    }
                }

                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .horizontalScroll(rememberScrollState()),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    ThemePalettes.all.forEach { p ->
                        val isSelected = p.id == palette.id
                        Surface(
                            onClick = {
                                onSelectPalette(p)
                                isDarkTheme = p.isDark
                            },
                            shape = RoundedCornerShape(12.dp),
                            color = if (isSelected) Navy else Color(0xFFF7F9F8),
                            border = androidx.compose.foundation.BorderStroke(1.5.dp, if (isSelected) p.primary else Line)
                        ) {
                            Row(
                                modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(6.dp)
                            ) {
                                Row(horizontalArrangement = Arrangement.spacedBy(2.dp)) {
                                    Surface(color = p.primary, shape = CircleShape, modifier = Modifier.size(11.dp)) {}
                                    Surface(color = p.accent, shape = CircleShape, modifier = Modifier.size(11.dp)) {}
                                }
                                Text(
                                    text = p.name,
                                    fontSize = 11.sp,
                                    fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium,
                                    color = if (isSelected) Color.White else Navy
                                )
                            }
                        }
                    }
                }
            }
        }

        // Tweak Bar
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                Surface(
                    onClick = { showCustomizerSheet = !showCustomizerSheet },
                    shape = RoundedCornerShape(10.dp),
                    color = if (showCustomizerSheet) Mint else Color.White,
                    border = androidx.compose.foundation.BorderStroke(1.dp, Line)
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        Icon(Icons.Default.Edit, contentDescription = null, tint = Green, modifier = Modifier.size(13.dp))
                        Text("Tweak UI", fontSize = 11.sp, fontWeight = FontWeight.Bold, color = Green)
                    }
                }

                Surface(
                    onClick = { isDarkTheme = !isDarkTheme },
                    shape = RoundedCornerShape(10.dp),
                    color = if (isDarkTheme) Navy else Color.White,
                    border = androidx.compose.foundation.BorderStroke(1.dp, Line)
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 6.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        Icon(
                            if (isDarkTheme) Icons.Default.DarkMode else Icons.Default.LightMode,
                            contentDescription = null,
                            tint = if (isDarkTheme) Mint else Navy,
                            modifier = Modifier.size(14.dp)
                        )
                        Text(
                            if (isDarkTheme) "Dark" else "Light",
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Bold,
                            color = if (isDarkTheme) Color.White else Navy
                        )
                    }
                }
            }

            // Screen Switcher
            Row(
                modifier = Modifier.horizontalScroll(rememberScrollState()),
                horizontalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                screenNames.forEachIndexed { idx, name ->
                    val isSelected = idx == selectedScreenIndex
                    Surface(
                        onClick = { selectedScreenIndex = idx },
                        shape = RoundedCornerShape(10.dp),
                        color = if (isSelected) primaryColor else Color.White,
                        border = if (isSelected) null else androidx.compose.foundation.BorderStroke(1.dp, Line)
                    ) {
                        Text(
                            text = name,
                            fontSize = 11.sp,
                            fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium,
                            color = if (isSelected) Color.White else Navy,
                            modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp)
                        )
                    }
                }
            }
        }

        // Tweak Properties Drawer
        AnimatedVisibility(visible = showCustomizerSheet) {
            Card(
                shape = RoundedCornerShape(16.dp),
                colors = CardDefaults.cardColors(containerColor = Color.White),
                border = androidx.compose.foundation.BorderStroke(1.dp, Line),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(modifier = Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Text("🎨 Quick Visual Customizer", fontWeight = FontWeight.Bold, fontSize = 13.sp, color = Navy)

                    OutlinedTextField(
                        value = appTitle,
                        onValueChange = { appTitle = it },
                        label = { Text("App Title") },
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(10.dp)
                    )

                    OutlinedTextField(
                        value = appGoal,
                        onValueChange = { appGoal = it },
                        label = { Text("App Objective") },
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(10.dp)
                    )

                    Text("Brand Icon Category:", fontSize = 11.sp, color = Muted)
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .horizontalScroll(rememberScrollState()),
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        listOf("Rocket", "Finance", "Store", "Heart", "Tasks", "Book").forEach { iconName ->
                            val isSel = selectedCategoryIcon == iconName
                            Surface(
                                onClick = { selectedCategoryIcon = iconName },
                                shape = RoundedCornerShape(8.dp),
                                color = if (isSel) primaryColor else Color(0xFFF2F4F3)
                            ) {
                                Text(
                                    text = iconName,
                                    fontSize = 11.sp,
                                    fontWeight = if (isSel) FontWeight.Bold else FontWeight.Normal,
                                    color = if (isSel) Color.White else Navy,
                                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 5.dp)
                                )
                            }
                        }
                    }
                }
            }
        }

        // ================= SMARTPHONE MOCKUP FRAME =================
        Card(
            shape = RoundedCornerShape(36.dp),
            colors = CardDefaults.cardColors(containerColor = phoneBg),
            border = androidx.compose.foundation.BorderStroke(4.dp, Color(0xFF2C3E50)),
            elevation = CardDefaults.cardElevation(defaultElevation = 8.dp),
            modifier = Modifier
                .width(320.dp)
                .height(550.dp)
        ) {
            Column(modifier = Modifier.fillMaxSize()) {
                // Top Punch Hole & Android Status Bar
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 20.dp, vertical = 8.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text("9:41", fontSize = 11.sp, fontWeight = FontWeight.Bold, color = phoneTextColor)
                    Surface(color = Color.Black, shape = CircleShape, modifier = Modifier.size(10.dp)) {}
                    Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                        Icon(Icons.Default.Wifi, contentDescription = null, tint = phoneTextColor, modifier = Modifier.size(12.dp))
                        Icon(Icons.Default.BatteryChargingFull, contentDescription = null, tint = phoneTextColor, modifier = Modifier.size(12.dp))
                    }
                }

                // App TopAppBar
                Surface(
                    color = phoneSurface,
                    shadowElevation = 2.dp
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 14.dp, vertical = 10.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            Surface(color = accentColor.copy(alpha = 0.35f), shape = CircleShape, modifier = Modifier.size(26.dp)) {
                                Box(contentAlignment = Alignment.Center) {
                                    val iconVector = when (selectedCategoryIcon) {
                                        "Finance" -> Icons.Default.AccountBalanceWallet
                                        "Store" -> Icons.Default.ShoppingCart
                                        "Heart" -> Icons.Default.Favorite
                                        "Book" -> Icons.Default.MenuBook
                                        "Tasks" -> Icons.Default.Check
                                        else -> Icons.Default.AutoAwesome
                                    }
                                    Icon(iconVector, contentDescription = null, tint = primaryColor, modifier = Modifier.size(15.dp))
                                }
                            }
                            Column {
                                Text(appTitle, fontWeight = FontWeight.Bold, fontSize = 14.sp, color = phoneTextColor)
                                Text(screenNames.getOrNull(selectedScreenIndex) ?: "Screen", fontSize = 10.sp, color = phoneSubText)
                            }
                        }
                        Icon(Icons.Default.MoreVert, contentDescription = null, tint = phoneSubText, modifier = Modifier.size(18.dp))
                    }
                }

                // App Body
                Column(
                    modifier = Modifier
                        .weight(1f)
                        .padding(12.dp)
                        .verticalScroll(rememberScrollState()),
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    // Objective Hero Card
                    Card(
                        shape = RoundedCornerShape(16.dp),
                        colors = CardDefaults.cardColors(containerColor = if (isDarkTheme) Color(0xFF242424) else accentColor.copy(alpha = 0.2f)),
                        border = androidx.compose.foundation.BorderStroke(1.dp, primaryColor.copy(alpha = 0.3f))
                    ) {
                        Column(modifier = Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                                Icon(Icons.Default.AutoAwesome, contentDescription = null, tint = primaryColor, modifier = Modifier.size(16.dp))
                                Text("App Objective", fontWeight = FontWeight.Bold, fontSize = 12.sp, color = primaryColor)
                            }
                            Text(appGoal, fontSize = 12.sp, color = phoneTextColor, lineHeight = 16.sp)
                        }
                    }

                    // Interactive Add Item Field
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        Box(
                            modifier = Modifier
                                .weight(1f)
                                .background(phoneSurface, RoundedCornerShape(12.dp))
                                .border(1.dp, if (isDarkTheme) Color(0xFF3A3A3A) else Line, RoundedCornerShape(12.dp))
                                .padding(horizontal = 10.dp, vertical = 8.dp)
                        ) {
                            if (newItemInput.isBlank()) {
                                Text("Add live test item...", fontSize = 12.sp, color = phoneSubText)
                            }
                            BasicTextField(
                                value = newItemInput,
                                onValueChange = { newItemInput = it },
                                modifier = Modifier.fillMaxWidth(),
                                textStyle = androidx.compose.ui.text.TextStyle(color = phoneTextColor, fontSize = 12.sp)
                            )
                        }
                        Button(
                            onClick = {
                                if (newItemInput.isNotBlank()) {
                                    demoItems = demoItems + newItemInput.trim()
                                    newItemInput = ""
                                }
                            },
                            shape = RoundedCornerShape(10.dp),
                            colors = ButtonDefaults.buttonColors(containerColor = primaryColor),
                            contentPadding = PaddingValues(horizontal = 10.dp, vertical = 6.dp)
                        ) {
                            Icon(Icons.Default.Add, contentDescription = "Add", modifier = Modifier.size(16.dp), tint = Color.White)
                        }
                    }

                    // Interactive List Items
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text("ACTIVE ITEMS (${demoItems.size})", fontSize = 10.sp, fontWeight = FontWeight.Bold, color = phoneSubText, letterSpacing = 1.sp)
                        Text("${checkedItems.size} completed", fontSize = 10.sp, color = primaryColor, fontWeight = FontWeight.Medium)
                    }

                    demoItems.forEachIndexed { index, itemText ->
                        val isChecked = checkedItems.contains(index)
                        Surface(
                            shape = RoundedCornerShape(12.dp),
                            color = phoneSurface,
                            border = androidx.compose.foundation.BorderStroke(1.dp, if (isChecked) primaryColor else if (isDarkTheme) Color(0xFF333333) else Line),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Row(
                                modifier = Modifier.padding(horizontal = 10.dp, vertical = 8.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.SpaceBetween
                            ) {
                                Row(
                                    modifier = Modifier
                                        .weight(1f)
                                        .clickable {
                                            checkedItems = if (isChecked) checkedItems - index else checkedItems + index
                                        },
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                                ) {
                                    Icon(
                                        imageVector = if (isChecked) Icons.Default.CheckCircle else Icons.Default.RadioButtonUnchecked,
                                        contentDescription = null,
                                        tint = if (isChecked) primaryColor else phoneSubText,
                                        modifier = Modifier.size(18.dp)
                                    )
                                    Text(
                                        text = itemText,
                                        fontSize = 12.sp,
                                        color = if (isChecked) phoneSubText else phoneTextColor,
                                        textDecoration = if (isChecked) androidx.compose.ui.text.style.TextDecoration.LineThrough else null
                                    )
                                }

                                IconButton(
                                    onClick = {
                                        demoItems = demoItems.filterIndexed { i, _ -> i != index }
                                        checkedItems = checkedItems.filter { it != index }.toSet()
                                    },
                                    modifier = Modifier.size(24.dp)
                                ) {
                                    Icon(Icons.Default.Delete, contentDescription = "Delete", tint = phoneSubText, modifier = Modifier.size(14.dp))
                                }
                            }
                        }
                    }
                }

                // Phone Bottom Bar
                Surface(
                    color = phoneSurface,
                    shadowElevation = 4.dp
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 6.dp),
                        horizontalArrangement = Arrangement.SpaceAround
                    ) {
                        listOf(Icons.Default.Home, Icons.Default.List, Icons.Default.Settings).forEachIndexed { i, icon ->
                            IconButton(onClick = { if (i < screenNames.size) selectedScreenIndex = i }) {
                                Icon(
                                    icon,
                                    contentDescription = null,
                                    tint = if (selectedScreenIndex == i) primaryColor else phoneSubText,
                                    modifier = Modifier.size(20.dp)
                                )
                            }
                        }
                    }
                }
            }
        }

        // Action Buttons
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            OutlinedButton(
                onClick = onNavigateToChat,
                shape = RoundedCornerShape(12.dp),
                modifier = Modifier.weight(1f)
            ) {
                Icon(Icons.Default.ChatBubbleOutline, contentDescription = null, modifier = Modifier.size(16.dp))
                Spacer(Modifier.width(6.dp))
                Text("Chat AI", fontSize = 12.sp)
            }

            Button(
                onClick = onTriggerBuild,
                shape = RoundedCornerShape(12.dp),
                colors = ButtonDefaults.buttonColors(containerColor = Navy),
                modifier = Modifier.weight(1f)
            ) {
                Icon(Icons.Default.CloudUpload, contentDescription = null, modifier = Modifier.size(16.dp))
                Spacer(Modifier.width(6.dp))
                Text("Build Cloud APK", fontSize = 12.sp)
            }
        }
    }
}

// ================= PILLAR 3: FILES =================
@Composable
private fun UnifiedFilesTab(
    files: Map<String, String>,
    selectedFile: String,
    onSelectFile: (String) -> Unit,
    onTriggerBuild: () -> Unit
) {
    val context = LocalContext.current
    val currentContent = files[selectedFile] ?: files.values.firstOrNull() ?: "// No file selected"
    val activePath = if (files.containsKey(selectedFile)) selectedFile else files.keys.firstOrNull() ?: ""

    Column(modifier = Modifier.fillMaxSize()) {
        // Horizontal File Selector Chips
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .background(Color.White)
                .horizontalScroll(rememberScrollState())
                .padding(horizontal = 10.dp, vertical = 8.dp),
            horizontalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            files.keys.forEach { path ->
                val fileName = path.substringAfterLast('/')
                val isSelected = path == activePath
                Surface(
                    onClick = { onSelectFile(path) },
                    shape = RoundedCornerShape(8.dp),
                    color = if (isSelected) Mint else Color(0xFFF2F5F3),
                    border = if (isSelected) androidx.compose.foundation.BorderStroke(1.dp, Green) else null
                ) {
                    Text(
                        text = fileName,
                        fontSize = 11.sp,
                        fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal,
                        color = if (isSelected) Green else Navy,
                        modifier = Modifier.padding(horizontal = 10.dp, vertical = 5.dp)
                    )
                }
            }
        }

        // Code Viewer
        Column(
            modifier = Modifier
                .weight(1f)
                .fillMaxWidth()
                .background(DarkCodeBg)
        ) {
            // Header Bar
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(DarkCodeSurface)
                    .padding(horizontal = 12.dp, vertical = 8.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Text(
                    text = activePath,
                    color = Color.LightGray,
                    fontSize = 11.sp,
                    fontFamily = FontFamily.Monospace
                )
                IconButton(
                    onClick = {
                        val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                        val clip = ClipData.newPlainText("Code", currentContent)
                        clipboard.setPrimaryClip(clip)
                        Toast.makeText(context, "Code copied to clipboard!", Toast.LENGTH_SHORT).show()
                    },
                    modifier = Modifier.size(28.dp)
                ) {
                    Icon(Icons.Default.ContentCopy, contentDescription = "Copy", tint = CodeGreen, modifier = Modifier.size(16.dp))
                }
            }

            // Code Content
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .verticalScroll(rememberScrollState())
                    .horizontalScroll(rememberScrollState())
                    .padding(12.dp)
            ) {
                Text(
                    text = currentContent,
                    color = Color(0xFFECEFF4),
                    fontSize = 12.sp,
                    lineHeight = 18.sp,
                    fontFamily = FontFamily.Monospace
                )
            }
        }

        // Bottom Bar
        Surface(
            color = Color.White,
            shadowElevation = 4.dp
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 10.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Text(
                    "${files.size} ready-to-build Android files",
                    fontSize = 12.sp,
                    color = Muted
                )
                Button(
                    onClick = onTriggerBuild,
                    shape = RoundedCornerShape(10.dp),
                    colors = ButtonDefaults.buttonColors(containerColor = Navy)
                ) {
                    Icon(Icons.Default.CloudUpload, contentDescription = null, modifier = Modifier.size(16.dp))
                    Spacer(Modifier.width(6.dp))
                    Text("Push & Build APK", fontSize = 12.sp)
                }
            }
        }
    }
}
