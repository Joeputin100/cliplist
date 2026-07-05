package com.cliplist.app.work

import android.content.Context
import android.content.pm.ServiceInfo
import android.net.Uri
import android.os.Build
import androidx.work.CoroutineWorker
import androidx.work.ForegroundInfo
import androidx.work.WorkerParameters
import androidx.work.workDataOf
import com.cliplist.app.R
import com.cliplist.app.settings.SettingsRepository
import com.cliplist.app.workflow.GenPhase
import com.cliplist.scan.MetadataPass
import com.cliplist.scan.PlaylistPlanner
import com.cliplist.scan.PlaylistWriter
import com.cliplist.scan.RenameExecution
import com.cliplist.scan.RenameExecutor
import com.cliplist.scan.RenameOptions
import com.cliplist.scan.RenamePlanner
import com.cliplist.scan.ResultModelBuilder
import com.cliplist.scan.ResultModelCodec
import com.cliplist.scan.ScanOptions
import com.cliplist.storage.AudioProbes
import com.cliplist.storage.StorageVolumes
import kotlinx.coroutines.flow.first
import java.io.File

/**
 * Builds the playlists as a foreground service. Everything is re-derived from the folder URI +
 * settings (no in-memory ViewModel state), so the work survives process death: WorkManager re-runs
 * it from its own database, the per-folder cache makes the re-scan cheap, and the writes are
 * idempotent. The rich [com.cliplist.scan.ResultModel] is serialized to a file for the UI to read
 * once the work succeeds. Progress is reported as a [GenPhase] ordinal so the existing Progress
 * screen renders unchanged.
 */
class PlaylistBuildWorker(
    appContext: Context,
    params: WorkerParameters,
) : CoroutineWorker(appContext, params) {

    override suspend fun getForegroundInfo(): ForegroundInfo =
        buildForeground(GenPhase.Starting, 0, 0, indeterminate = true)

    override suspend fun doWork(): Result {
        val treeUri = inputData.getString(KEY_TREE_URI)?.let(Uri::parse) ?: return Result.failure()
        val displayName = inputData.getString(KEY_DISPLAY_NAME) ?: ""
        val recursive = inputData.getBoolean(KEY_RECURSIVE, true)
        val alphabetize = inputData.getBoolean(KEY_ALPHABETIZE, true)
        val ctx = applicationContext

        return try {
            setForeground(getForegroundInfo())
            val volume = StorageVolumes.forUri(ctx, treeUri)
            val probe = AudioProbes.forVolume(ctx, volume)
            val settings = SettingsRepository(ctx)
            val scanOptions = ScanOptions(
                recursive = recursive,
                alphabetize = alphabetize,
                audioExtensions = settings.audioExtensions.first(),
            )

            // Rename pass (optional) — clean/hidden-file fixes for the Clip Sport.
            val renamePlan = RenamePlanner().plan(
                volume,
                RenameOptions(
                    cleanNames = settings.cleanNames.first(),
                    renameHidden = settings.renameHidden.first(),
                ),
            )
            var renameExec: RenameExecution? = null
            if (renamePlan.ops.isNotEmpty()) {
                update(GenPhase.Cleaning, 0, renamePlan.ops.size, indeterminate = true)
                renameExec = RenameExecutor(volume).execute(renamePlan)
            }

            // Re-plan after renames, then the metadata pass (real durations + drop unreadable).
            val planToWrite = PlaylistPlanner().plan(volume, scanOptions)
            val analyzed = MetadataPass.run(volume, probe, planToWrite, scanOptions.audioExtensions) { done, total ->
                update(GenPhase.Starting, done, total, indeterminate = false)
            }
            val report = PlaylistWriter(volume).execute(analyzed.plan) { done, total ->
                update(GenPhase.Writing, done, total, indeterminate = false)
            }

            // Optional cover art — drop a bundled folder.jpg where none exists (never overwrite).
            if (settings.writeCoverArt.first()) {
                val artBytes = ctx.assets.open("folder.jpg").use { it.readBytes() }
                analyzed.plan.folders.forEach { fp ->
                    val hasArt = volume.children(fp.folder)
                        .any { it.name.equals("folder.jpg", ignoreCase = true) }
                    if (!hasArt) volume.writeFile(fp.folder, "folder.jpg", artBytes, "image/jpeg")
                }
            }

            val result = ResultModelBuilder.build(
                report, renameExec, analyzed.plan, displayName,
                analyzed.totalDurationMs, analyzed.unreadable,
            )
            File(ctx.filesDir, RESULT_FILE).writeText(ResultModelCodec.encode(result))
            Result.success()
        } catch (e: Exception) {
            Result.failure(workDataOf(KEY_ERROR to (e.message ?: "Generation failed")))
        }
    }

    // Non-suspend (ListenableFuture) variants so this can be called from PlaylistWriter's
    // non-suspend onProgress callback as well as from doWork.
    private fun update(phase: GenPhase, done: Int, total: Int, indeterminate: Boolean) {
        setProgressAsync(workDataOf(KEY_PHASE to phase.ordinal, KEY_DONE to done, KEY_TOTAL to total))
        setForegroundAsync(buildForeground(phase, done, total, indeterminate))
    }

    private fun buildForeground(phase: GenPhase, done: Int, total: Int, indeterminate: Boolean): ForegroundInfo {
        val text = when (phase) {
            GenPhase.Starting -> applicationContext.getString(R.string.preparing)
            GenPhase.Cleaning -> applicationContext.getString(R.string.phase_cleaning)
            GenPhase.Writing -> applicationContext.getString(R.string.progress_count_fmt, done, total)
        }
        val notif = BuildNotifications.build(applicationContext, text, done, total, indeterminate)
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            ForegroundInfo(
                BuildNotifications.NOTIFICATION_ID, notif, ServiceInfo.FOREGROUND_SERVICE_TYPE_DATA_SYNC,
            )
        } else {
            ForegroundInfo(BuildNotifications.NOTIFICATION_ID, notif)
        }
    }

    companion object {
        const val WORK_NAME = "playlist_build"
        const val KEY_TREE_URI = "tree_uri"
        const val KEY_DISPLAY_NAME = "display_name"
        const val KEY_RECURSIVE = "recursive"
        const val KEY_ALPHABETIZE = "alphabetize"
        const val KEY_PHASE = "phase"
        const val KEY_DONE = "done"
        const val KEY_TOTAL = "total"
        const val KEY_ERROR = "error"
        const val RESULT_FILE = "last_result.json"
    }
}
