@file:OptIn(ExperimentalMaterial3Api::class)

package com.jwoglom.controlx2.presentation.screens.sections

import android.content.Context
import android.content.SharedPreferences
import android.os.Handler
import android.os.Looper
import android.widget.Toast
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Checkbox
import androidx.compose.material3.Divider
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.ListItem
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import androidx.navigation.NavHostController
import com.jwoglom.controlx2.sync.nightscout.NightscoutSyncConfig
import com.jwoglom.controlx2.sync.nightscout.NightscoutSyncStatusStore
import com.jwoglom.controlx2.sync.nightscout.ProcessorType
import com.jwoglom.controlx2.sync.nightscout.NightscoutSyncWorker
import com.jwoglom.controlx2.sync.nightscout.normalizeNightscoutUrl
import com.jwoglom.controlx2.presentation.components.HeaderLine
import com.jwoglom.controlx2.dataStore
import com.jwoglom.controlx2.db.historylog.HistoryLogDatabase
import com.jwoglom.controlx2.presentation.components.HistoryLogSyncProgressBar
import com.jwoglom.controlx2.util.HistoryLogAnchorLookup
import com.jwoglom.controlx2.util.HistoryLogAnchorManager
import com.jwoglom.controlx2.util.HistoryLogSyncProgress
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withContext
import kotlinx.coroutines.launch
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter

@Composable
fun NightscoutSettings(
    innerPadding: PaddingValues = PaddingValues(),
    navController: NavHostController? = null,
    pumpSid: Int = 0
) {
    val context = LocalContext.current
    val prefs = context.getSharedPreferences("controlx2", android.content.Context.MODE_PRIVATE)
    val coroutineScope = rememberCoroutineScope()

    var config by remember { mutableStateOf(NightscoutSyncConfig.load(prefs)) }
    var syncStatus by remember { mutableStateOf(NightscoutSyncStatusStore.load(prefs)) }
    var showUrlDialog by remember { mutableStateOf(false) }
    var showApiSecretDialog by remember { mutableStateOf(false) }
    var showProcessorsDialog by remember { mutableStateOf(false) }
    var showIntervalDialog by remember { mutableStateOf(false) }

    LazyColumn(
        contentPadding = innerPadding,
        verticalArrangement = Arrangement.spacedBy(0.dp),
        modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = 0.dp),
        content = {
            item {
                HeaderLine("Nightscout Settings")
                Divider()
            }

            // Enable/Disable Nightscout sync
            item {
                ListItem(
                    headlineContent = {
                        Text(if (config.enabled) "Disable Nightscout Sync" else "Enable Nightscout Sync")
                    },
                    supportingContent = {
                        Text(
                            if (config.enabled) {
                                "Stops automatic upload to Nightscout"
                            } else {
                                "Starts automatic upload of pump data to Nightscout"
                            }
                        )
                    },
                    leadingContent = {
                        Icon(
                            if (config.enabled) Icons.Filled.Close else Icons.Filled.Check,
                            contentDescription = if (config.enabled) "Disable" else "Enable"
                        )
                    },
                    modifier = Modifier.clickable {
                        val newConfig = config.copy(enabled = !config.enabled)
                        config = newConfig
                        NightscoutSyncConfig.save(prefs, newConfig)

                        // Start/stop worker
                        if (newConfig.enabled) {
                            NightscoutSyncWorker.startIfEnabled(context, prefs, pumpSid)
                            Toast.makeText(context, "Nightscout sync enabled", Toast.LENGTH_SHORT).show()
                        } else {
                            NightscoutSyncWorker.stopIfRunning()
                            Toast.makeText(context, "Nightscout sync disabled", Toast.LENGTH_SHORT).show()
                        }

                        syncStatus = NightscoutSyncStatusStore.load(prefs)
                    }
                )
                Divider()
            }
            item {
                ListItem(
                    headlineContent = { Text("Include notes in uploads") },
                    supportingContent = {
                        Text(
                            "Attach human-readable notes (rate, source, IDs) to each uploaded " +
                            "event. Off by default to reduce clutter in Nocturne; enable to mirror NightScout."
                        )
                    },
                    trailingContent = {
                        Checkbox(
                            checked = config.includeNotes,
                            onCheckedChange = null
                        )
                    },
                    modifier = Modifier.clickable {
                        val newConfig = config.copy(includeNotes = !config.includeNotes)
                        config = newConfig
                        NightscoutSyncConfig.save(prefs, newConfig)
                    }
                )
                Divider()
            }

            item {
                ListItem(
                    headlineContent = { Text("Last successful sync") },
                    supportingContent = {
                        Text(
                            syncStatus.lastSuccessfulSyncMillis?.let { formatTimestamp(it) } ?: "Never"
                        )
                    }
                )
                Divider()
            }

            item {
                val connectionMessage = if (!config.enabled) {
                    "Nightscout sync is disabled"
                } else if (!config.isValid()) {
                    "Nightscout config is incomplete"
                } else if (!syncStatus.lastError.isNullOrBlank()) {
                    syncStatus.lastError ?: "Connection error"
                } else {
                    "Connected"
                }

                val supportingMessage = if (config.enabled && !syncStatus.lastError.isNullOrBlank()) {
                    syncStatus.lastErrorMillis?.let { "Last failure: ${formatTimestamp(it)}" } ?: ""
                } else {
                    ""
                }

                ListItem(
                    headlineContent = { Text("Connection status") },
                    supportingContent = {
                        Column {
                            Text(connectionMessage)
                            if (supportingMessage.isNotBlank()) {
                                Text(supportingMessage)
                            }
                        }
                    }
                )
                Divider()
            }

            // Nightscout URL
            item {
                ListItem(
                    headlineContent = { Text("Nightscout URL") },
                    supportingContent = {
                        Text(config.nightscoutUrl.ifBlank { "Not configured" })
                    },
                    modifier = Modifier.clickable {
                        showUrlDialog = true
                    }
                )
                Divider()
            }

            // API Secret
            item {
                ListItem(
                    headlineContent = { Text("API Secret") },
                    supportingContent = {
                        Text(if (config.apiSecret.isNotBlank()) "••••••••" else "Not configured")
                    },
                    modifier = Modifier.clickable {
                        showApiSecretDialog = true
                    }
                )
                Divider()
            }

            // Enabled Processors
            item {
                ListItem(
                    headlineContent = { Text("Data Types") },
                    supportingContent = {
                        Text("${config.enabledProcessors.size} of ${ProcessorType.all().size} enabled")
                    },
                    modifier = Modifier.clickable {
                        showProcessorsDialog = true
                    }
                )
                Divider()
            }

            // Sync Interval
            item {
                ListItem(
                    headlineContent = { Text("Sync Interval") },
                    supportingContent = { Text("${config.syncIntervalMinutes} minutes") },
                    modifier = Modifier.clickable {
                        showIntervalDialog = true
                    }
                )
                Divider()
            }

            // NightScout sync progress
            item {
                HistoryLogSyncProgressBar(title = "NightScout Sync")
            }

            // Send Range to NightScout
            item {
                ListItem(
                    headlineContent = { Text("Send Range to NightScout") },
                    supportingContent = {
                        Text("Send a downloaded date range to NightScout (re-sync older data)")
                    },
                    modifier = Modifier.clickable {
                        coroutineScope.launch {
                            openNightscoutRangeDialog(context, prefs, pumpSid)
                        }
                    }
                )
                Divider()
            }

            // Sync Now button
            item {
                ListItem(
                    headlineContent = { Text("Sync Now") },
                    supportingContent = { Text("Trigger immediate sync") },
                    leadingContent = {
                        Icon(Icons.Filled.Refresh, contentDescription = "Sync")
                    },
                    modifier = Modifier.clickable {
                        if (config.enabled && config.isValid()) {
                            coroutineScope.launch {
                                NightscoutSyncWorker.getInstance(context, prefs, pumpSid).syncNow()
                                syncStatus = NightscoutSyncStatusStore.load(prefs)
                                Toast.makeText(context, "Sync triggered", Toast.LENGTH_SHORT).show()
                            }
                        } else {
                            Toast.makeText(
                                context,
                                "Please enable and configure Nightscout first",
                                Toast.LENGTH_SHORT
                            ).show()
                        }
                    }
                )
                Divider()
            }
        }
    )

    // URL Dialog
    if (showUrlDialog) {
        var urlInput by remember { mutableStateOf(config.nightscoutUrl) }
        var urlError by remember { mutableStateOf<String?>(null) }
        AlertDialog(
            onDismissRequest = { showUrlDialog = false },
            title = { Text("Nightscout URL") },
            text = {
                Column {
                    OutlinedTextField(
                        value = urlInput,
                        onValueChange = {
                            urlInput = it
                            urlError = null
                        },
                        label = { Text("URL") },
                        placeholder = { Text("https://your-nightscout.herokuapp.com") },
                        isError = urlError != null,
                        modifier = Modifier.fillMaxWidth()
                    )

                    if (urlError != null) {
                        Text(
                            text = urlError!!,
                            modifier = Modifier.padding(top = 8.dp)
                        )
                    }
                }
            },
            confirmButton = {
                TextButton(onClick = {
                    val normalizedUrl = normalizeNightscoutUrl(urlInput)
                    if (normalizedUrl == null) {
                        urlError = "Please enter a valid Nightscout URL"
                        return@TextButton
                    }

                    val newConfig = config.copy(nightscoutUrl = normalizedUrl)
                    config = newConfig
                    NightscoutSyncConfig.save(prefs, newConfig)
                    showUrlDialog = false
                }) {
                    Text("Save")
                }
            },
            dismissButton = {
                TextButton(onClick = { showUrlDialog = false }) {
                    Text("Cancel")
                }
            }
        )
    }

    // API Secret Dialog
    if (showApiSecretDialog) {
        var secretInput by remember { mutableStateOf(config.apiSecret) }
        AlertDialog(
            onDismissRequest = { showApiSecretDialog = false },
            title = { Text("API Secret") },
            text = {
                OutlinedTextField(
                    value = secretInput,
                    onValueChange = { secretInput = it },
                    label = { Text("API Secret") },
                    visualTransformation = PasswordVisualTransformation(),
                    modifier = Modifier.fillMaxWidth()
                )
            },
            confirmButton = {
                TextButton(onClick = {
                    val newConfig = config.copy(apiSecret = secretInput.trim())
                    config = newConfig
                    NightscoutSyncConfig.save(prefs, newConfig)
                    showApiSecretDialog = false
                }) {
                    Text("Save")
                }
            },
            dismissButton = {
                TextButton(onClick = { showApiSecretDialog = false }) {
                    Text("Cancel")
                }
            }
        )
    }

    // Processors Dialog
    if (showProcessorsDialog) {
        var selectedProcessors by remember { mutableStateOf(config.enabledProcessors) }
        AlertDialog(
            onDismissRequest = { showProcessorsDialog = false },
            title = { Text("Select Data Types") },
            text = {
                LazyColumn {
                    items(ProcessorType.all().toList()) { processorType ->
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable {
                                    selectedProcessors = if (selectedProcessors.contains(processorType)) {
                                        selectedProcessors - processorType
                                    } else {
                                        selectedProcessors + processorType
                                    }
                                }
                                .padding(vertical = 8.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Checkbox(
                                checked = selectedProcessors.contains(processorType),
                                onCheckedChange = null
                            )
                            Text(
                                text = processorType.displayName,
                                modifier = Modifier.padding(start = 8.dp)
                            )
                        }
                    }
                }
            },
            confirmButton = {
                TextButton(onClick = {
                    val newConfig = config.copy(enabledProcessors = selectedProcessors)
                    config = newConfig
                    NightscoutSyncConfig.save(prefs, newConfig)
                    showProcessorsDialog = false
                }) {
                    Text("Save")
                }
            },
            dismissButton = {
                TextButton(onClick = { showProcessorsDialog = false }) {
                    Text("Cancel")
                }
            }
        )
    }

    // Sync Interval Dialog
    if (showIntervalDialog) {
        var intervalInput by remember { mutableStateOf(config.syncIntervalMinutes.toString()) }
        AlertDialog(
            onDismissRequest = { showIntervalDialog = false },
            title = { Text("Sync Interval") },
            text = {
                OutlinedTextField(
                    value = intervalInput,
                    onValueChange = { intervalInput = it },
                    label = { Text("Minutes") },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                    modifier = Modifier.fillMaxWidth()
                )
            },
            confirmButton = {
                TextButton(onClick = {
                    val minutes = intervalInput.toIntOrNull() ?: 15
                    val newConfig = config.copy(syncIntervalMinutes = minutes.coerceIn(5, 1440))
                    config = newConfig
                    NightscoutSyncConfig.save(prefs, newConfig)

                    // Restart worker with new interval
                    if (config.enabled) {
                        NightscoutSyncWorker.stopIfRunning()
                        NightscoutSyncWorker.startIfEnabled(context, prefs, pumpSid)
                    }

                    showIntervalDialog = false
                }) {
                    Text("Save")
                }
            },
            dismissButton = {
                TextButton(onClick = { showIntervalDialog = false }) {
                    Text("Cancel")
                }
            }
        )
    }

}

private fun formatTimestamp(timestampMillis: Long): String {
    val formatter = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss")
    return Instant.ofEpochMilli(timestampMillis)
        .atZone(ZoneId.systemDefault())
        .format(formatter)
}

/**
 * Open the shared range dialog for re-sending a downloaded date range to NightScout.
 *
 * Fetches the DB's actual seq span and the sampled anchors off the main thread, then hands the
 * window to [rangeSliderRangeDialog]. On confirm it maps the chosen ids back to instants via the
 * anchor lookup and hands them to [NightscoutSyncWorker.syncRetroactiveRange], which runs the sync
 * on a background scope, drives the shared progress field per-processor, and toasts start/finish.
 */
suspend fun openNightscoutRangeDialog(
    context: Context,
    prefs: SharedPreferences,
    pumpSid: Int,
) {
    val anchorManager = HistoryLogAnchorManager(context)

    // Bounds + anchors are DB reads; Room throws IllegalStateException on main-thread access, so do
    // them inside the IO block together (the anchor lookup also reads the DB).
    val (tail, head, lookup) = withContext(Dispatchers.IO) {
        val range = HistoryLogDatabase.getDatabase(context)
            .historyLogDao()
            .getSeqRange(pumpSid)
            .first()
        // A null range means no rows exist for this pumpSid. A non-null range is real history even
        // when its min seqId is 0 (pump sequence numbers start at 0), so only null means "empty".
        if (range == null) {
            return@withContext Triple(0L, 0L, null)
        }
        Triple(
            range.minSeqId!!,
            range.maxSeqId!!,
            HistoryLogAnchorLookup(anchorManager.anchors(pumpSid))
        )
    }
    if (lookup == null) {
        Toast.makeText(
            context,
            "No history in this app yet — fetch logs first",
            Toast.LENGTH_SHORT
        ).show()
        return
    }
    // Default the slider to the last ~24h (head-24h .. head); the full span stays reachable.
    val defaultStart = lookup.recentStart(head, fallback = tail)

    rangeSliderRangeDialog(
        context,
        "Send Range to NightScout",
        "Only data already downloaded to this app is sent. Use Get History Logs first to pull more.",
        minSeq = tail,
        maxSeq = head,
        initialStart = defaultStart,
        dateLabel = { id -> formatAnchor(id, lookup) },
        detail = { start, end -> "${end - start + 1} items in app" },
        confirmText = "Send to NightScout"
    ) { startId, endId ->
        val startLdt = lookup.dateForId(startId)
        val endLdt = lookup.dateForId(endId)
        if (startLdt == null || endLdt == null) {
            Toast.makeText(
                context,
                "Date not found for the selected range",
                Toast.LENGTH_SHORT
            ).show()
            return@rangeSliderRangeDialog
        }

        // Prime the bar, then let the worker drive it per-log and toast completion.
        dataStore.historyLogSyncProgress.value = HistoryLogSyncProgress(1, 0)
        NightscoutSyncWorker.getInstance(context, prefs, pumpSid)
            .syncRetroactiveRange(startLdt, endLdt) { done, total ->
                val progress = HistoryLogSyncProgress(total.toLong(), done.toLong())
                if (Looper.myLooper() != Looper.getMainLooper()) {
                    Handler(Looper.getMainLooper()).post {
                        dataStore.historyLogSyncProgress.value = progress
                    }
                } else {
                    dataStore.historyLogSyncProgress.value = progress
                }
            }
    }
}

/** Render a seq id as "id · MMM dd, yyyy HH:mm", falling back to the bare id with no anchor. */
private fun formatAnchor(id: Long, lookup: HistoryLogAnchorLookup): String {
    val date = lookup.dateForId(id)
    return if (date != null) "$id  \u00b7  ${date.format(ANCHOR_DATE_FORMAT)}" else id.toString()
}

private val ANCHOR_DATE_FORMAT = DateTimeFormatter.ofPattern("MMM dd, yyyy HH:mm")
