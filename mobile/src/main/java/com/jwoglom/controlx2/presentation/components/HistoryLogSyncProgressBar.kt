package com.jwoglom.controlx2.presentation.components

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.livedata.observeAsState
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.jwoglom.controlx2.LocalDataStore
import com.jwoglom.controlx2.presentation.DataStore
import com.jwoglom.controlx2.presentation.theme.ControlX2Theme
import com.jwoglom.controlx2.util.HistoryLogSyncProgress

@Composable
fun HistoryLogSyncProgressBar(
    modifier: Modifier = Modifier,
    hideWhenComplete: Boolean = false,
    replaceWithPaddingWhenComplete: Boolean = false,
    title: String = "History Log Sync"
) {
    val ds = LocalDataStore.current
    val historyLogProgress by ds.historyLogSyncProgress.observeAsState()

    historyLogProgress?.let { progress ->
        val target = progress.target
        val synced = progress.synced
        val isSynced = target <= 0 || synced >= target

        if (replaceWithPaddingWhenComplete && isSynced) {
            Column(
                modifier = modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 8.dp)
                    .height(50.dp)
            ) {
                Spacer(modifier = Modifier.fillMaxWidth())
            }
            return
        }

        if (hideWhenComplete && isSynced) {
            return
        }

        val fraction = if (target > 0) (synced.toFloat() / target.toFloat()).coerceIn(0f, 1f) else 1f

        Column(
            modifier = modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 8.dp)
                .height(50.dp)
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.fillMaxWidth()
            ) {
                Text(
                    text = title,
                    style = MaterialTheme.typography.bodyMedium,
                    fontWeight = FontWeight.SemiBold,
                    fontSize = 14.sp
                )
                Spacer(modifier = Modifier.weight(1f))
                Text(
                    text = if (isSynced) {
                        "Synced"
                    } else {
                        "$synced / $target"
                    },
                    style = MaterialTheme.typography.bodySmall,
                    fontSize = 12.sp,
                    color = if (isSynced) {
                        MaterialTheme.colorScheme.primary
                    } else {
                        MaterialTheme.colorScheme.onSurfaceVariant
                    }
                )
            }

            Spacer(modifier = Modifier.height(4.dp))

            LinearProgressIndicator(
                progress = { fraction },
                modifier = Modifier
                    .fillMaxWidth()
                    .height(8.dp),
            )

            if (!isSynced && target > 0) {
                Spacer(modifier = Modifier.height(2.dp))
                Text(
                    text = "${(fraction * 100).toInt()}% complete (${target - synced} logs remaining)",
                    style = MaterialTheme.typography.bodySmall,
                    fontSize = 11.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }
}

@Preview(showBackground = true, name = "50% Synced")
@Composable
private fun HistoryLogSyncProgressBar_HalfSynced_Preview() {
    ControlX2Theme {
        Surface(
            modifier = Modifier.fillMaxSize(),
            color = Color.White,
        ) {
            val dataStore = DataStore()
            setMockProgress(dataStore, 10000, 5000)
            CompositionLocalProvider(LocalDataStore provides dataStore) {
                HistoryLogSyncProgressBar()
            }
        }
    }
}

@Preview(showBackground = true, name = "75% Synced")
@Composable
private fun HistoryLogSyncProgressBar_MostlySynced_Preview() {
    ControlX2Theme {
        Surface(
            modifier = Modifier.fillMaxWidth(),
            color = Color.White,
        ) {
            val dataStore = DataStore()
            setMockProgress(dataStore, 10000, 7500)
            CompositionLocalProvider(LocalDataStore provides dataStore) {
                HistoryLogSyncProgressBar()
            }
        }
    }
}

@Preview(showBackground = true, name = "Fully Synced")
@Composable
private fun HistoryLogSyncProgressBar_FullySynced_Preview() {
    ControlX2Theme {
        Surface(
            modifier = Modifier.fillMaxWidth(),
            color = Color.White,
        ) {
            val dataStore = DataStore()
            setMockProgress(dataStore, 10000, 10000)
            CompositionLocalProvider(LocalDataStore provides dataStore) {
                HistoryLogSyncProgressBar()
            }
        }
    }
}
@Preview(showBackground = true, name = "Fully Synced Hidden")
@Composable
private fun HistoryLogSyncProgressBar_FullySyncedHidden_Preview() {
    ControlX2Theme {
        Surface(
            modifier = Modifier.fillMaxWidth(),
            color = Color.White,
        ) {
            val dataStore = DataStore()
            setMockProgress(dataStore, 10000, 10000)
            CompositionLocalProvider(LocalDataStore provides dataStore) {
                HistoryLogSyncProgressBar(hideWhenComplete = true)
            }
        }
    }
}

@Preview(showBackground = true, name = "Just Started (10%)")
@Composable
private fun HistoryLogSyncProgressBar_JustStarted_Preview() {
    ControlX2Theme {
        Surface(
            modifier = Modifier.fillMaxWidth(),
            color = Color.White,
        ) {
            val dataStore = DataStore()
            setMockProgress(dataStore, 10000, 1000)
            CompositionLocalProvider(LocalDataStore provides dataStore) {
                HistoryLogSyncProgressBar()
            }
        }
    }
}

@Preview(showBackground = true, name = "With Non-Zero First Seq")
@Composable
private fun HistoryLogSyncProgressBar_NonZeroStart_Preview() {
    ControlX2Theme {
        Surface(
            modifier = Modifier.fillMaxWidth(),
            color = Color.White,
        ) {
            val dataStore = DataStore()
            // pump has logs 5000-10000 (5000 to fetch), 3000 fetched so far (~60%)
            setMockProgress(dataStore, 5000, 3000)
            CompositionLocalProvider(LocalDataStore provides dataStore) {
                HistoryLogSyncProgressBar()
            }
        }
    }
}

// Preview helper: set the current sync-scope progress on the DataStore so the
// progress bar renders the given target/synced pair.
private fun setMockProgress(dataStore: DataStore, target: Long, synced: Long) {
    dataStore.historyLogSyncProgress.value = HistoryLogSyncProgress(target, synced)
}
