package com.vyoma.app

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

/**
 * A build snapshot tied to one immutable project revision.
 * The API/backend will own this state; UI must not fake build completion.
 */
internal data class CloudBuildSnapshot(
    val jobId: String,
    val projectId: String,
    val revision: Int,
    val status: CloudBuildStatus,
    val stage: String,
    val progress: Float?,
    val message: String,
    val logLines: List<String> = emptyList(),
    val previewUrl: String? = null,
    val artifactUrl: String? = null,
    val errorMessage: String? = null
)

internal enum class CloudBuildStatus {
    QUEUED, RUNNING, SUCCEEDED, FAILED, CANCELLED
}

/**
 * Reusable status card for the Home/chat workspace.
 * Progress is indeterminate when the server has no trustworthy percentage.
 */
@Composable
internal fun CloudBuildStatusCard(
    build: CloudBuildSnapshot,
    modifier: Modifier = Modifier
) {
    val statusLabel = when (build.status) {
        CloudBuildStatus.QUEUED -> "Queued"
        CloudBuildStatus.RUNNING -> "Building in cloud"
        CloudBuildStatus.SUCCEEDED -> "Build successful"
        CloudBuildStatus.FAILED -> "Build failed"
        CloudBuildStatus.CANCELLED -> "Build cancelled"
    }
    val statusColor = when (build.status) {
        CloudBuildStatus.QUEUED -> Color(0xFF8A6500)
        CloudBuildStatus.RUNNING -> Color(0xFF247451)
        CloudBuildStatus.SUCCEEDED -> Color(0xFF247451)
        CloudBuildStatus.FAILED -> Color(0xFFB3261E)
        CloudBuildStatus.CANCELLED -> Color(0xFF6E7C82)
    }

    Card(
        modifier = modifier.fillMaxWidth(),
        shape = RoundedCornerShape(20.dp),
        colors = CardDefaults.cardColors(containerColor = Color.White),
        border = BorderStroke(1.dp, Color(0xFFE5EAE6))
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Column(verticalArrangement = Arrangement.spacedBy(3.dp)) {
                    Text("Cloud build", color = Color(0xFF132B3A), fontWeight = FontWeight.Bold, fontSize = 16.sp)
                    Text("Revision ${build.revision}", color = Color(0xFF6E7C82), fontSize = 11.sp)
                }
                Text(statusLabel, color = statusColor, fontWeight = FontWeight.SemiBold, fontSize = 12.sp)
            }
            Text(build.message, color = Color(0xFF132B3A), fontSize = 13.sp)
            if (build.status == CloudBuildStatus.RUNNING || build.status == CloudBuildStatus.QUEUED) {
                if (build.progress != null) {
                    LinearProgressIndicator(
                        progress = { build.progress.coerceIn(0f, 1f) },
                        modifier = Modifier.fillMaxWidth(),
                        color = Color(0xFF247451)
                    )
                } else {
                    LinearProgressIndicator(
                        modifier = Modifier.fillMaxWidth(),
                        color = Color(0xFF247451)
                    )
                }
                Text("You can keep chatting while this job runs.", color = Color(0xFF6E7C82), fontSize = 11.sp)
            }
            if (build.status == CloudBuildStatus.SUCCEEDED) {
                Text(
                    if (build.previewUrl != null) "Preview is ready for this revision." else "Build finished; preview generation status is not available yet.",
                    color = Color(0xFF247451),
                    fontSize = 12.sp
                )
            }
            build.errorMessage?.let {
                Text(it, color = Color(0xFFB3261E), fontSize = 12.sp)
            }
            if (build.logLines.isNotEmpty()) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(Color(0xFFF8F8F2), RoundedCornerShape(12.dp))
                        .padding(10.dp),
                    verticalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    Text("Latest build output", color = Color(0xFF132B3A), fontWeight = FontWeight.SemiBold, fontSize = 11.sp)
                    build.logLines.takeLast(5).forEach { line ->
                        Text(line, color = Color(0xFF526168), fontSize = 10.sp, lineHeight = 14.sp)
                    }
                }
            }
        }
    }
}
