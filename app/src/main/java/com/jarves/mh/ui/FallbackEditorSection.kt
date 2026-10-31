package com.jarves.mh.ui

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.KeyboardArrowUp
import androidx.compose.material.icons.filled.SwapHoriz
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
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import com.jarves.mh.R
import com.jarves.mh.model.FallbackPolicy
import com.jarves.mh.model.FallbackTarget
import com.jarves.mh.model.FreeModelCatalog
import com.jarves.mh.model.ProviderKind
import com.jarves.mh.network.DiscoveredModel

/**
 * Fallbacks — collapsible like other Agent rows; picker lists models only from
 * providers the user has actually configured (AndroidHarness-style chain of up to 4).
 */
@Composable
fun FallbackEditorSection(
    policy: FallbackPolicy,
    /** Providers the user has set up (keys / Free models / etc.), not the full catalog. */
    configuredProviders: Set<ProviderKind>,
    /** Live models for the currently selected provider (used when that kind is configured). */
    currentProvider: ProviderKind,
    currentModels: List<DiscoveredModel>,
    onSave: (FallbackPolicy) -> Unit,
    modifier: Modifier = Modifier,
) {
    var expanded by rememberSaveable { mutableStateOf(false) }
    var draft by remember(policy) { mutableStateOf(policy) }
    var privacy by rememberSaveable { mutableStateOf(false) }
    var retriesMenu by remember { mutableStateOf(false) }
    var picking by remember { mutableStateOf(false) }

    val subtitle = when {
        draft.enabled && draft.targets.isNotEmpty() ->
            stringResource(R.string.fallback_subtitle_active, draft.targets.size)
        draft.enabled -> stringResource(R.string.fallback_subtitle)
        else -> stringResource(R.string.fallback_subtitle)
    }

    Column(modifier.fillMaxWidth()) {
        // Same pattern as PremiumSummaryRow in Agent settings
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .clickable { expanded = !expanded }
                .padding(vertical = 13.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Box(
                modifier = Modifier
                    .size(38.dp)
                    .background(MaterialTheme.colorScheme.primary.copy(alpha = 0.10f), RoundedCornerShape(11.dp)),
                contentAlignment = Alignment.Center,
            ) {
                Icon(
                    Icons.Default.SwapHoriz,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(19.dp),
                )
            }
            Spacer(Modifier.width(12.dp))
            Column(Modifier.weight(1f)) {
                Text(stringResource(R.string.fallback_title), fontWeight = FontWeight.SemiBold, fontSize = 14.sp)
                Text(
                    subtitle,
                    fontSize = 11.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                )
            }
            Icon(
                if (expanded) Icons.Default.KeyboardArrowUp else Icons.Default.KeyboardArrowDown,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }

        if (expanded) {
            Column(
                Modifier.padding(start = 50.dp, end = 4.dp, bottom = 12.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        stringResource(R.string.fallback_automatic),
                        Modifier.weight(1f),
                        fontWeight = FontWeight.SemiBold,
                        fontSize = 13.sp,
                    )
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
                            Modifier.fillMaxWidth().padding(vertical = 2.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Column(Modifier.weight(1f)) {
                                Text(target.model, fontWeight = FontWeight.SemiBold, fontSize = 13.sp, maxLines = 2)
                                Text(
                                    target.providerId.replace('_', ' '),
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
                    Text(stringResource(R.string.fallback_retries), Modifier.weight(1f), fontSize = 13.sp)
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
                    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                        Text(stringResource(R.string.fallback_same_provider), Modifier.weight(1f), fontSize = 13.sp)
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
    }

    if (picking) {
        FallbackModelPickerDialog(
            configuredProviders = configuredProviders,
            currentProvider = currentProvider,
            currentModels = currentModels,
            existing = draft.targets,
            onDismiss = { picking = false },
            onPick = { target ->
                if (draft.targets.none { it.providerId == target.providerId && it.model == target.model } &&
                    draft.targets.size < FallbackPolicy.MAX_TARGETS
                ) {
                    draft = draft.copy(
                        enabled = true,
                        targets = draft.targets + target,
                    )
                }
                picking = false
            },
        )
    }
}

/**
 * Models the user can add to the fallback chain: only from configured providers.
 * Free models → full curated/live free list; other configured providers → their
 * discovered models (or default model when list is empty).
 */
internal fun fallbackPickerEntries(
    configuredProviders: Set<ProviderKind>,
    currentProvider: ProviderKind,
    currentModels: List<DiscoveredModel>,
): List<Pair<ProviderKind, DiscoveredModel>> {
    val kinds = configuredProviders.ifEmpty {
        // First install: Free models is the onboarding default.
        setOf(ProviderKind.FREE_MODELS)
    }.toList().sortedBy { it.name }

    return buildList {
        for (kind in kinds) {
            val models: List<DiscoveredModel> = when {
                kind == ProviderKind.FREE_MODELS -> {
                    val liveFree = currentModels.filter {
                        it.isFree ||
                            it.id.equals(FreeModelCatalog.DEFAULT_ID, true) ||
                            it.id.endsWith(":free", true)
                    }
                    FreeModelCatalog.mergeLive(liveFree)
                }
                kind == currentProvider && currentModels.isNotEmpty() -> currentModels
                else -> listOf(
                    DiscoveredModel(
                        id = kind.defaultModel,
                        displayName = kind.defaultModel,
                        isFree = false,
                    ),
                )
            }
            models.forEach { model -> add(kind to model) }
        }
    }.distinctBy { (kind, model) -> "${kind.name}\u0000${model.id}" }
}

@Composable
private fun FallbackModelPickerDialog(
    configuredProviders: Set<ProviderKind>,
    currentProvider: ProviderKind,
    currentModels: List<DiscoveredModel>,
    existing: List<FallbackTarget>,
    onDismiss: () -> Unit,
    onPick: (FallbackTarget) -> Unit,
) {
    val entries = remember(configuredProviders, currentProvider, currentModels) {
        fallbackPickerEntries(configuredProviders, currentProvider, currentModels)
    }
    var selected by remember { mutableStateOf<Pair<ProviderKind, String>?>(null) }

    Dialog(onDismissRequest = onDismiss) {
        Surface(
            shape = RoundedCornerShape(20.dp),
            tonalElevation = 4.dp,
            modifier = Modifier.fillMaxWidth().heightIn(max = 520.dp),
        ) {
            Column(Modifier.padding(20.dp)) {
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
                Text(
                    stringResource(R.string.fallback_pick_hint),
                    fontSize = 12.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(bottom = 8.dp),
                )
                Column(
                    Modifier
                        .weight(1f, fill = false)
                        .heightIn(max = 360.dp)
                        .verticalScroll(rememberScrollState()),
                ) {
                    var lastKind: ProviderKind? = null
                    entries.forEach { (kind, model) ->
                        if (kind != lastKind) {
                            if (lastKind != null) Spacer(Modifier.height(8.dp))
                            Text(
                                kind.name.replace('_', ' '),
                                fontWeight = FontWeight.Bold,
                                fontSize = 12.sp,
                                color = MaterialTheme.colorScheme.primary,
                                modifier = Modifier.padding(vertical = 6.dp),
                            )
                            lastKind = kind
                        }
                        val already = existing.any { it.providerId == kind.name && it.model == model.id }
                        val isSel = selected?.first == kind && selected?.second == model.id
                        Row(
                            Modifier
                                .fillMaxWidth()
                                .clickable(enabled = !already) {
                                    selected = kind to model.id
                                }
                                .padding(vertical = 8.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Column(Modifier.weight(1f)) {
                                Text(
                                    model.id,
                                    fontWeight = if (isSel) FontWeight.Bold else FontWeight.Medium,
                                    fontSize = 13.sp,
                                    color = when {
                                        already -> MaterialTheme.colorScheme.onSurfaceVariant
                                        isSel -> MaterialTheme.colorScheme.primary
                                        else -> MaterialTheme.colorScheme.onSurface
                                    },
                                    maxLines = 2,
                                )
                                formatContextLabel(model.contextTokens)?.let { ctx ->
                                    Text(ctx, fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                }
                            }
                            if (already) {
                                Text("✓", color = MaterialTheme.colorScheme.primary)
                            }
                        }
                        HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.3f))
                    }
                    if (entries.isEmpty()) {
                        Text(
                            stringResource(R.string.fallback_pick_empty),
                            fontSize = 13.sp,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.padding(vertical = 16.dp),
                        )
                    }
                }
                Spacer(Modifier.height(12.dp))
                Button(
                    onClick = {
                        val pick = selected ?: return@Button
                        onPick(FallbackTarget(providerId = pick.first.name, model = pick.second))
                    },
                    modifier = Modifier.fillMaxWidth(),
                    enabled = selected != null,
                ) {
                    Text(stringResource(R.string.fallback_add_model, existing.size, FallbackPolicy.MAX_TARGETS))
                }
            }
        }
    }
}
