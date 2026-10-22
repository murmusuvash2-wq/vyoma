package com.vyoma.app

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.launch

private val WorkspaceTabs = listOf("Chat", "Blueprint", "Preview", "Files")

@Composable
internal fun ProjectWorkspaceScreen(
    repository: ProjectRepository,
    projectId: String,
    onBack: () -> Unit
) {
    val projectWithBlueprints by repository.observeProject(projectId).collectAsState(initial = null)
    var selectedTab by remember { mutableIntStateOf(1) }
    val scope = rememberCoroutineScope()
    var goal by remember { mutableStateOf("") }
    var screens by remember { mutableStateOf("") }
    var data by remember { mutableStateOf("") }
    var integrations by remember { mutableStateOf("") }
    var permissions by remember { mutableStateOf("") }
    var edgeCases by remember { mutableStateOf("") }
    var acceptanceTests by remember { mutableStateOf("") }
    var message by remember { mutableStateOf<String?>(null) }
    val project = projectWithBlueprints

    LaunchedEffect(project?.project?.projectId, project?.blueprints?.maxOfOrNull { it.version }) {
        val latest = project?.blueprints?.maxByOrNull { it.version } ?: return@LaunchedEffect
        goal = latest.goal
        screens = latest.screens
        data = latest.data
        integrations = latest.integrations
        permissions = latest.permissions
        edgeCases = latest.edgeCases
        acceptanceTests = latest.acceptanceTests
        message = null
    }

    Column(
        Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(18.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            Button(onClick = onBack, colors = ButtonDefaults.buttonColors(containerColor = Navy)) {
                Text("← Projects")
            }
        }
        if (project == null) {
            Text("Loading project…", color = Muted)
            return@Column
        }
        val latest = project.blueprints.maxByOrNull { it.version }
        Text(project.project.name, color = Navy, fontSize = 27.sp, fontWeight = FontWeight.ExtraBold)
        Text("Project workspace · local database", color = Muted, fontSize = 13.sp)
        Row(horizontalArrangement = Arrangement.spacedBy(6.dp), modifier = Modifier.fillMaxWidth()) {
            WorkspaceTabs.forEachIndexed { index, label ->
                Button(
                    onClick = { selectedTab = index; message = null },
                    colors = ButtonDefaults.buttonColors(
                        containerColor = if (selectedTab == index) Navy else Color(0xFFE8EEEA),
                        contentColor = if (selectedTab == index) Color.White else Navy
                    ),
                    shape = RoundedCornerShape(12.dp),
                    modifier = Modifier.weight(1f)
                ) {
                    Text(label, fontSize = 10.sp)
                }
            }
        }
        when (selectedTab) {
            0 -> {
                Text("Chat", color = Navy, fontSize = 21.sp, fontWeight = FontWeight.Bold)
                Surface(color = Color.White, shape = RoundedCornerShape(18.dp), border = BorderStroke(1.dp, Line)) {
                    Column(Modifier.fillMaxWidth().padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Text("Your project is ready for conversation.", color = Navy, fontWeight = FontWeight.SemiBold)
                        Text("The AI chat and code-writing service are not connected yet. Your project and blueprint are saved locally on this device.", color = Muted, fontSize = 13.sp)
                    }
                }
            }
            1 -> {
                Text("Blueprint · v${latest?.version ?: 1}", color = Navy, fontSize = 21.sp, fontWeight = FontWeight.Bold)
                Text("Edit the plan here. Saving creates a new version, keeping earlier versions in the database.", color = Muted, fontSize = 12.sp)
                WorkspaceField("Goal", goal, { goal = it })
                WorkspaceField("Screens", screens, { screens = it })
                WorkspaceField("Data model", data, { data = it })
                WorkspaceField("Integrations", integrations, { integrations = it })
                WorkspaceField("Permissions", permissions, { permissions = it })
                WorkspaceField("Edge cases", edgeCases, { edgeCases = it })
                WorkspaceField("Acceptance tests", acceptanceTests, { acceptanceTests = it })
                Button(
                    onClick = {
                        scope.launch {
                            runCatching {
                                repository.saveBlueprintVersion(
                                    project = project,
                                    goal = goal,
                                    screens = screens,
                                    data = data,
                                    integrations = integrations,
                                    permissions = permissions,
                                    edgeCases = edgeCases,
                                    acceptanceTests = acceptanceTests
                                )
                            }.onSuccess { message = "Saved as a new blueprint version." }
                                .onFailure { message = it.message ?: "Could not save blueprint." }
                        }
                    },
                    enabled = goal.isNotBlank(),
                    colors = ButtonDefaults.buttonColors(containerColor = Navy),
                    modifier = Modifier.fillMaxWidth()
                ) { Text("Save new version") }
                message?.let { Text(it, color = Green, fontSize = 12.sp) }
            }
            2 -> {
                Text("Preview", color = Navy, fontSize = 21.sp, fontWeight = FontWeight.Bold)
                Surface(color = Color.White, shape = RoundedCornerShape(20.dp), border = BorderStroke(1.dp, Line)) {
                    Column(Modifier.fillMaxWidth().padding(18.dp), verticalArrangement = Arrangement.spacedBy(9.dp)) {
                        Text(project.project.name, color = Navy, fontSize = 20.sp, fontWeight = FontWeight.Bold)
                        Text(goal.ifBlank { "Your app goal will appear here." }, color = Muted, fontSize = 14.sp)
                        Text("Planned screens", color = Navy, fontWeight = FontWeight.SemiBold)
                        Text(screens.ifBlank { "No screens defined yet." }, color = Muted, fontSize = 13.sp)
                        Text("This is a blueprint preview, not a running generated app.", color = Muted, fontSize = 11.sp)
                    }
                }
            }
            else -> {
                Text("Files", color = Navy, fontSize = 21.sp, fontWeight = FontWeight.Bold)
                Surface(color = Color.White, shape = RoundedCornerShape(18.dp), border = BorderStroke(1.dp, Line)) {
                    Column(Modifier.fillMaxWidth().padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Text("Project files", color = Navy, fontWeight = FontWeight.SemiBold)
                        Text("No generated source files yet. File creation will be enabled when the code-generation worker is connected.", color = Muted, fontSize = 13.sp)
                    }
                }
            }
        }
        Spacer(Modifier.height(8.dp))
    }
}

@Composable
private fun WorkspaceField(label: String, value: String, onValueChange: (String) -> Unit) {
    OutlinedTextField(
        value = value,
        onValueChange = onValueChange,
        label = { Text(label) },
        modifier = Modifier.fillMaxWidth(),
        minLines = 2,
        shape = RoundedCornerShape(14.dp)
    )
}
