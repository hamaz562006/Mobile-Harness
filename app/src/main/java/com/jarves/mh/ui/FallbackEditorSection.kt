package com.jarves.mh.ui

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material3.Button
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import com.jarves.mh.R
import com.jarves.mh.model.FallbackPolicy
import com.jarves.mh.model.FallbackTarget
import com.jarves.mh.model.ProviderKind
import com.jarves.mh.model.FreeModelCatalog
import com.jarves.mh.network.DiscoveredModel

/**
 * Fallbacks editor — mirrors AndroidHarness Models & providers → Fallbacks UI.
 * Stores chain of backup models (any provider) + retries + same-provider constraint.
 */
@Composable
fun FallbackEditorSection(
    policy: FallbackPolicy,
    availableProviders: List<ProviderKind>,
    discoveredModels: List<DiscoveredModel>,
    onSave: (FallbackPolicy) -> Unit,
    modifier: Modifier = Modifier,
) {
    var draft by remember(policy) { mutableStateOf(policy) }
    var privacy by rememberSaveable { mutableStateOf(false) }
    var retriesMenu by remember { mutableStateOf(false) }
    var picking by remember { mutableStateOf(false) }

    Surface(
        modifier = modifier.fillMaxWidth(),
        shape = RoundedCornerShape(16.dp),
        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.28f),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.4f)),
    ) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Column {
                Text(stringResource(R.string.fallback_title), fontWeight = FontWeight.Bold, fontSize = 18.sp)
                Text(
                    stringResource(R.string.fallback_subtitle),
                    fontSize = 12.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }

            Row(
                Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(stringResource(R.string.fallback_automatic), Modifier.weight(1f), fontWeight = FontWeight.SemiBold)
                Switch(
                    checked = draft.enabled,
                    onCheckedChange = { draft = draft.copy(enabled = it) },
                )
            }

            Text(
                stringResource(R.string.fallback_help),
                fontSize = 12.sp,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                lineHeight = 16.sp,
            )

            if (draft.targets.isEmpty()) {
                Text(
                    stringResource(R.string.fallback_none),
                    fontSize = 13.sp,
                    fontWeight = FontWeight.Medium,
                )
            } else {
                draft.targets.forEachIndexed { index, target ->
                    Row(
                        Modifier.fillMaxWidth().padding(vertical = 4.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Column(Modifier.weight(1f)) {
                            Text(target.model, fontWeight = FontWeight.SemiBold, fontSize = 13.sp, maxLines = 2)
                            Text(
                                target.providerId,
                                fontSize = 11.sp,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                        Text("#${index + 1}", fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        IconButton(
                            onClick = {
                                draft = draft.copy(targets = draft.targets.filterIndexed { i, _ -> i != index })
                            },
                        ) {
                            Icon(Icons.Default.Delete, stringResource(R.string.fallback_remove), Modifier.size(18.dp))
                        }
                    }
                    HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.35f))
                }
            }

            TextButton(
                onClick = { picking = true },
                enabled = draft.targets.size < FallbackPolicy.MAX_TARGETS,
            ) {
                Icon(Icons.Default.Add, null, Modifier.size(16.dp))
                Spacer(Modifier.width(6.dp))
                Text(
                    stringResource(
                        R.string.fallback_add_model,
                        draft.targets.size,
                        FallbackPolicy.MAX_TARGETS,
                    ),
                )
            }

            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                Text(stringResource(R.string.fallback_retries), Modifier.weight(1f))
                TextButton(onClick = { retriesMenu = true }) {
                    Text(stringResource(R.string.fallback_retries_value, draft.retries))
                }
                DropdownMenu(expanded = retriesMenu, onDismissRequest = { retriesMenu = false }) {
                    (0..5).forEach { count ->
                        DropdownMenuItem(
                            text = { Text(stringResource(R.string.fallback_retries_value, count)) },
                            onClick = {
                                draft = draft.copy(retries = count)
                                retriesMenu = false
                            },
                        )
                    }
                }
            }

            Text(
                stringResource(R.string.fallback_policy_note),
                fontSize = 12.sp,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                lineHeight = 16.sp,
            )

            TextButton(onClick = { privacy = !privacy }) {
                Text(
                    if (privacy) stringResource(R.string.fallback_hide_privacy)
                    else stringResource(R.string.fallback_show_privacy),
                )
            }

            if (privacy) {
                Text(
                    if (draft.enabled) stringResource(R.string.fallback_privacy_on)
                    else stringResource(R.string.fallback_privacy_off),
                    fontSize = 12.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    lineHeight = 16.sp,
                )
                Row(
                    Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(stringResource(R.string.fallback_same_provider), Modifier.weight(1f))
                    Switch(
                        checked = draft.sameProviderOnly,
                        onCheckedChange = { draft = draft.copy(sameProviderOnly = it) },
                    )
                }
            }

            Button(
                onClick = { onSave(draft) },
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(24.dp),
            ) {
                Text(stringResource(R.string.fallback_save), fontWeight = FontWeight.SemiBold)
            }
        }
    }

    if (picking) {
        FallbackModelPickerDialog(
            providers = availableProviders,
            discoveredModels = discoveredModels,
            existing = draft.targets,
            onDismiss = { picking = false },
            onPick = { target ->
                if (draft.targets.none { it.providerId == target.providerId && it.model == target.model } &&
                    draft.targets.size < FallbackPolicy.MAX_TARGETS
                ) {
                    draft = draft.copy(targets = draft.targets + target)
                }
                picking = false
            },
        )
    }
}

@Composable
private fun FallbackModelPickerDialog(
    providers: List<ProviderKind>,
    discoveredModels: List<DiscoveredModel>,
    existing: List<FallbackTarget>,
    onDismiss: () -> Unit,
    onPick: (FallbackTarget) -> Unit,
) {
    var provider by remember { mutableStateOf(providers.firstOrNull() ?: ProviderKind.FREE_MODELS) }
    val modelOptions = remember(provider, discoveredModels) {
        when (provider) {
            ProviderKind.FREE_MODELS -> {
                val live = discoveredModels.filter { it.isFree }
                if (live.isNotEmpty()) live else FreeModelCatalog.asDiscovered()
            }
            else -> discoveredModels.ifEmpty {
                listOf(
                    DiscoveredModel(
                        id = provider.defaultModel,
                        displayName = provider.defaultModel,
                    ),
                )
            }
        }
    }
    var model by remember(modelOptions) { mutableStateOf(modelOptions.firstOrNull()?.id.orEmpty()) }

    Dialog(onDismissRequest = onDismiss) {
        Surface(shape = RoundedCornerShape(20.dp), tonalElevation = 4.dp) {
            Column(Modifier.padding(20.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        stringResource(R.string.fallback_pick_title),
                        fontWeight = FontWeight.Bold,
                        fontSize = 16.sp,
                        modifier = Modifier.weight(1f),
                    )
                    IconButton(onClick = onDismiss) {
                        Icon(Icons.Default.Close, contentDescription = null)
                    }
                }
                Text(stringResource(R.string.fallback_pick_provider), fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                providers.forEach { kind ->
                    val selected = provider == kind
                    Text(
                        text = kind.name.replace('_', ' '),
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable { provider = kind }
                            .padding(vertical = 8.dp),
                        fontWeight = if (selected) FontWeight.Bold else FontWeight.Normal,
                        color = if (selected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface,
                    )
                }
                HorizontalDivider()
                Text(stringResource(R.string.fallback_pick_model), fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                modelOptions.take(40).forEach { option ->
                    val selected = model == option.id
                    val already = existing.any { it.providerId == provider.name && it.model == option.id }
                    Text(
                        text = option.id + if (already) " ✓" else "",
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable(enabled = !already) { model = option.id }
                            .padding(vertical = 8.dp),
                        fontWeight = if (selected) FontWeight.Bold else FontWeight.Normal,
                        color = if (selected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface,
                        maxLines = 2,
                    )
                }
                Button(
                    onClick = {
                        if (model.isNotBlank()) {
                            onPick(FallbackTarget(providerId = provider.name, model = model))
                        }
                    },
                    modifier = Modifier.fillMaxWidth(),
                    enabled = model.isNotBlank(),
                ) {
                    Text(stringResource(R.string.fallback_add_model, existing.size, FallbackPolicy.MAX_TARGETS))
                }
            }
        }
    }
}
