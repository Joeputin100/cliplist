package com.cliplist.app.ui.results

import android.content.Intent
import android.provider.DocumentsContract
import android.provider.Settings
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.animateIntAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material3.Button
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.NavController
import com.cliplist.app.R
import com.cliplist.app.nav.Screen
import com.cliplist.app.workflow.GenerateUiState
import com.cliplist.app.workflow.ScanViewModel
import com.cliplist.scan.ResultFolderRow
import com.cliplist.scan.ResultModel
import com.cliplist.scan.StorageHeuristics

// DocumentsContract.ACTION_DOCUMENT_ROOT_SETTINGS / Root.MIME_TYPE_ITEM — @hide constants, but
// the Settings app's exported PublicVolumeSettingsActivity has filtered on exactly this
// action + MIME type since Android 6, so the string values are stable.
private const val ACTION_DOCUMENT_ROOT_SETTINGS = "android.provider.action.DOCUMENT_ROOT_SETTINGS"
private const val MIME_TYPE_DOCUMENT_ROOT = "vnd.android.document/root"

/** The animated "Living summary" Results page: gradient hero, count-up stats, staggered tiles. */
@Composable
fun ResultsScreen(navController: NavController, vm: ScanViewModel) {
    val context = LocalContext.current
    val state by vm.generateState.collectAsStateWithLifecycle()
    val folder by vm.folder.collectAsStateWithLifecycle()
    val result = (state as? GenerateUiState.Done)?.result

    if (result == null) {
        Column(
            Modifier.fillMaxSize().padding(24.dp),
            verticalArrangement = Arrangement.Center,
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Text(stringResource(R.string.no_preview), style = MaterialTheme.typography.bodyLarge)
        }
        return
    }

    val goHome: () -> Unit = {
        vm.resetWorkflow()
        navController.navigate(Screen.Home.route) { popUpTo(Screen.Home.route) { inclusive = true } }
    }

    // Kick the entrance animations once, on first composition.
    var started by remember { mutableStateOf(false) }
    LaunchedEffect(Unit) { started = true }

    val tileRows = result.folders.chunked(2)

    LazyColumn(
        modifier = Modifier.fillMaxSize().padding(horizontal = 20.dp),
        contentPadding = PaddingValues(vertical = 24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        item {
            GradientHero(result, started)
            Spacer(Modifier.height(20.dp))
            StatRow(result, started)
            if (result.unreadable.isNotEmpty()) {
                Spacer(Modifier.height(12.dp))
                UnreadableChip(result.unreadable.size)
            }
            if (result.errors.isNotEmpty()) {
                Spacer(Modifier.height(12.dp))
                result.errors.take(5).forEach {
                    Text(
                        it,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.error,
                        textAlign = TextAlign.Center,
                    )
                }
            }
            if (tileRows.isNotEmpty()) {
                Spacer(Modifier.height(24.dp))
                Text(
                    stringResource(R.string.results_folders_written),
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurface,
                    modifier = Modifier.fillMaxWidth(),
                )
                Spacer(Modifier.height(12.dp))
            }
        }

        itemsIndexed(tileRows) { rowIndex, pair ->
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                pair.forEachIndexed { i, row ->
                    PlaylistTile(row, started, delayMs = (rowIndex * 2 + i) * 70, modifier = Modifier.weight(1f))
                }
                if (pair.size == 1) Spacer(Modifier.weight(1f))
            }
            Spacer(Modifier.height(12.dp))
        }

        item {
            Spacer(Modifier.height(12.dp))
            val volumeUuid = folder?.removableVolumeUuid
            if (volumeUuid != null) {
                OutlinedButton(
                    onClick = {
                        // The volume's own settings page (with the Unmount button) — the same
                        // intent the system Files app fires on a storage root's ⚙ action.
                        val volumeSettings = Intent(ACTION_DOCUMENT_ROOT_SETTINGS).setDataAndType(
                            DocumentsContract.buildRootUri(
                                StorageHeuristics.EXTERNAL_STORAGE_AUTHORITY, volumeUuid
                            ),
                            MIME_TYPE_DOCUMENT_ROOT,
                        )
                        runCatching { context.startActivity(volumeSettings) }.recoverCatching {
                            // OEM Settings without that page: fall back to the storage overview.
                            context.startActivity(Intent(Settings.ACTION_INTERNAL_STORAGE_SETTINGS))
                        }
                    },
                    modifier = Modifier.fillMaxWidth(),
                ) { Text(stringResource(R.string.eject)) }
                Text(
                    stringResource(R.string.eject_note),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurface,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.fillMaxWidth().padding(top = 6.dp),
                )
                Spacer(Modifier.height(16.dp))
            }
            Button(onClick = goHome, modifier = Modifier.fillMaxWidth()) {
                Text(stringResource(R.string.done))
            }
            TextButton(onClick = goHome, modifier = Modifier.fillMaxWidth()) {
                Text(stringResource(R.string.make_another))
            }
        }
    }
}

@Composable
private fun GradientHero(result: ResultModel, started: Boolean) {
    val checkScale by animateFloatAsState(
        targetValue = if (started) 1f else 0f,
        animationSpec = tween(500, easing = FastOutSlowInEasing),
        label = "check",
    )
    // onPrimary, not hardcoded white: in dark schemes primary/secondary are light pastels,
    // where white text all but disappears — onPrimary flips to a dark tone there.
    val onHero = MaterialTheme.colorScheme.onPrimary
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(28.dp))
            .background(
                Brush.linearGradient(
                    listOf(MaterialTheme.colorScheme.primary, MaterialTheme.colorScheme.secondary)
                )
            )
            .padding(vertical = 28.dp, horizontal = 20.dp),
        contentAlignment = Alignment.Center,
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Icon(
                Icons.Default.CheckCircle,
                contentDescription = null,
                tint = onHero,
                modifier = Modifier.size(64.dp).scale(checkScale),
            )
            Spacer(Modifier.height(12.dp))
            Text(
                if (result.allSucceeded) stringResource(R.string.results_success)
                else stringResource(R.string.results_issues),
                style = MaterialTheme.typography.headlineSmall,
                color = onHero,
                textAlign = TextAlign.Center,
            )
            if (result.destination.isNotEmpty()) {
                Spacer(Modifier.height(4.dp))
                Text(
                    stringResource(R.string.results_in_destination_fmt, result.destination),
                    style = MaterialTheme.typography.bodyMedium,
                    color = onHero.copy(alpha = 0.9f),
                    textAlign = TextAlign.Center,
                )
            }
        }
    }
}

@Composable
private fun StatRow(result: ResultModel, started: Boolean) {
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
        val playlists by animateIntAsState(
            if (started) result.playlistsWritten else 0, tween(900, easing = FastOutSlowInEasing), label = "p",
        )
        val tracks by animateIntAsState(
            if (started) result.totalTracks else 0, tween(900, easing = FastOutSlowInEasing), label = "t",
        )
        val minutes by animateIntAsState(
            if (started) (result.totalDurationMs / 60000L).toInt() else 0,
            tween(900, easing = FastOutSlowInEasing), label = "d",
        )
        StatChip(playlists.toString(), stringResource(R.string.results_stat_playlists), Modifier.weight(1f))
        StatChip(tracks.toString(), stringResource(R.string.results_stat_tracks), Modifier.weight(1f))
        StatChip(
            stringResource(R.string.results_duration_fmt, minutes / 60, minutes % 60),
            stringResource(R.string.results_stat_time),
            Modifier.weight(1f),
        )
    }
}

@Composable
private fun StatChip(value: String, label: String, modifier: Modifier = Modifier) {
    Column(
        modifier
            .clip(RoundedCornerShape(18.dp))
            .background(MaterialTheme.colorScheme.surfaceVariant)
            .padding(vertical = 14.dp, horizontal = 12.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text(value, style = MaterialTheme.typography.headlineSmall, color = MaterialTheme.colorScheme.primary)
        Text(label, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurface)
    }
}

@Composable
private fun UnreadableChip(count: Int) {
    Row(
        Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .background(MaterialTheme.colorScheme.errorContainer)
            .padding(vertical = 10.dp, horizontal = 14.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text("⚠", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onErrorContainer)
        Spacer(Modifier.size(8.dp))
        Text(
            stringResource(R.string.results_unreadable_fmt, count),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onErrorContainer,
        )
    }
}

@Composable
private fun PlaylistTile(row: ResultFolderRow, visible: Boolean, delayMs: Int, modifier: Modifier = Modifier) {
    val appear by animateFloatAsState(
        targetValue = if (visible) 1f else 0f,
        animationSpec = tween(420, delayMillis = delayMs, easing = FastOutSlowInEasing),
        label = "tile",
    )
    Box(
        modifier
            .height(64.dp)
            .graphicsLayer { alpha = appear; translationY = (1f - appear) * 24f }
            .clip(RoundedCornerShape(16.dp))
            .background(
                Brush.linearGradient(
                    listOf(MaterialTheme.colorScheme.primary, MaterialTheme.colorScheme.secondary)
                )
            )
            .padding(horizontal = 14.dp),
        contentAlignment = Alignment.CenterStart,
    ) {
        val onTile = MaterialTheme.colorScheme.onPrimary
        Row(verticalAlignment = Alignment.CenterVertically) {
            EqGlyph(onTile)
            Spacer(Modifier.size(10.dp))
            Column {
                Text(
                    row.folderName,
                    style = MaterialTheme.typography.titleSmall,
                    color = onTile,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                Text(
                    stringResource(R.string.results_folder_tracks_fmt, row.trackCount),
                    style = MaterialTheme.typography.labelSmall,
                    color = onTile.copy(alpha = 0.85f),
                )
            }
        }
    }
}

/** A small equalizer glyph (four rounded bars). */
@Composable
private fun EqGlyph(color: Color) {
    val heights = floatArrayOf(0.45f, 1f, 0.65f, 0.85f)
    Canvas(Modifier.size(18.dp, 20.dp)) {
        val barW = size.width / 7f
        heights.forEachIndexed { i, h ->
            val x = i * (barW + barW / 2f) + barW / 2f
            val barH = size.height * h
            drawLine(
                color = color,
                start = Offset(x, size.height),
                end = Offset(x, size.height - barH),
                strokeWidth = barW,
                cap = StrokeCap.Round,
            )
        }
    }
}
