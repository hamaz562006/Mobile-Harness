package com.jarves.mh.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Build
import androidx.compose.material.icons.outlined.Image
import androidx.compose.material.icons.outlined.Psychology
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.jarves.mh.network.DiscoveredModel

/** Format context window like AndroidHarness: 256000 → "256K ctx", 1000000 → "1M ctx". */
fun formatContextLabel(tokens: Long?): String? {
    if (tokens == null || tokens <= 0L) return null
    return when {
        tokens >= 1_000_000L -> {
            val m = tokens / 1_000_000.0
            if (m == m.toLong().toDouble()) "${m.toLong()}M ctx" else "${"%.1f".format(m)}M ctx"
        }
        tokens >= 1_000L -> "${tokens / 1_000L}K ctx"
        else -> "$tokens ctx"
    }
}

@Composable
fun ModelPickerDetails(
    model: DiscoveredModel,
    showCurrent: Boolean = false,
    modifier: Modifier = Modifier,
) {
    val metaParts = buildList {
        if (showCurrent) add("Current")
        formatContextLabel(model.contextTokens)?.let { add(it) }
    }
    Column(modifier.fillMaxWidth()) {
        Text(
            text = model.id,
            fontWeight = FontWeight.SemiBold,
            fontSize = 14.sp,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
            color = MaterialTheme.colorScheme.onSurface,
        )
        if (metaParts.isNotEmpty()) {
            Spacer(Modifier.height(2.dp))
            Text(
                text = metaParts.joinToString(" · "),
                fontSize = 12.sp,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
        val caps = buildList {
            if (model.supportsThinking == true) add(Cap("Thinking", Icons.Outlined.Psychology))
            if (model.supportsTools == true) add(Cap("Tools", Icons.Outlined.Build))
            if (model.supportsImages == true) add(Cap("Images", Icons.Outlined.Image))
        }
        if (caps.isNotEmpty()) {
            Spacer(Modifier.height(4.dp))
            Row(
                horizontalArrangement = Arrangement.spacedBy(10.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                caps.forEach { cap ->
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(
                            cap.icon,
                            contentDescription = null,
                            modifier = Modifier.size(13.dp),
                            tint = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                        Spacer(Modifier.width(3.dp))
                        Text(
                            cap.label,
                            fontSize = 11.sp,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
            }
        }
        model.note?.takeIf { it.isNotBlank() }?.let { note ->
            Spacer(Modifier.height(3.dp))
            Text(
                text = note,
                fontSize = 11.sp,
                color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.9f),
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
            )
        }
    }
}

private data class Cap(val label: String, val icon: ImageVector)
