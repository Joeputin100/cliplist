package com.cliplist.app.workflow

import android.app.Application
import android.net.Uri
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import androidx.work.ExistingWorkPolicy
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkInfo
import androidx.work.WorkManager
import androidx.work.workDataOf
import com.cliplist.app.settings.SettingsRepository
import com.cliplist.app.work.PlaylistBuildWorker
import com.cliplist.scan.MetadataPass
import com.cliplist.scan.PlaylistPlanner
import com.cliplist.scan.PreviewModel
import com.cliplist.scan.PreviewModelBuilder
import com.cliplist.scan.RenameOptions
import com.cliplist.scan.RenamePlanner
import com.cliplist.scan.ResultModel
import com.cliplist.scan.ResultModelCodec
import com.cliplist.scan.ScanOptions
import com.cliplist.storage.AudioProbes
import com.cliplist.storage.StorageVolumes
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File

data class WorkflowOptions(
    val searchSubfolders: Boolean = true,
    val alphabetize: Boolean = true,
)

data class SelectedFolder(
    val uri: Uri,
    val displayName: String,
    /** FAT volume UUID (e.g. "1704-050E") when the folder is on removable media; null otherwise. */
    val removableVolumeUuid: String? = null,
)

sealed interface ScanUiState {
    data object Idle : ScanUiState
    /** total == 0 while folders are still being enumerated (indeterminate). */
    data class Scanning(val done: Int = 0, val total: Int = 0) : ScanUiState
    data class Ready(val model: PreviewModel) : ScanUiState
    data class Error(val message: String) : ScanUiState
}

enum class GenPhase { Starting, Cleaning, Writing }

sealed interface GenerateUiState {
    data object Idle : GenerateUiState
    data class Working(val phase: GenPhase, val done: Int, val total: Int) : GenerateUiState
    data class Done(val result: ResultModel) : GenerateUiState
    data class Error(val message: String) : GenerateUiState
}

class ScanViewModel(app: Application) : AndroidViewModel(app) {

    private val _options = MutableStateFlow(WorkflowOptions())
    val options: StateFlow<WorkflowOptions> = _options.asStateFlow()

    private val _folder = MutableStateFlow<SelectedFolder?>(null)
    val folder: StateFlow<SelectedFolder?> = _folder.asStateFlow()

    private val _scanState = MutableStateFlow<ScanUiState>(ScanUiState.Idle)
    val scanState: StateFlow<ScanUiState> = _scanState.asStateFlow()

    private val _generateState = MutableStateFlow<GenerateUiState>(GenerateUiState.Idle)
    val generateState: StateFlow<GenerateUiState> = _generateState.asStateFlow()

    init {
        // Mirror the foreground build worker into generateState. This is what surfaces a build that
        // kept running in the background (or was re-run by WorkManager after a process kill) the
        // moment we're foregrounded again.
        viewModelScope.launch {
            WorkManager.getInstance(getApplication())
                .getWorkInfosForUniqueWorkFlow(PlaylistBuildWorker.WORK_NAME)
                .collect { infos ->
                    val active = setOf(
                        WorkInfo.State.ENQUEUED, WorkInfo.State.RUNNING, WorkInfo.State.BLOCKED,
                    )
                    val info = infos.firstOrNull { it.state in active }
                        ?: infos.lastOrNull() ?: return@collect
                    when (info.state) {
                        WorkInfo.State.ENQUEUED, WorkInfo.State.RUNNING, WorkInfo.State.BLOCKED -> {
                            val p = info.progress
                            val phase = GenPhase.entries.getOrElse(
                                p.getInt(PlaylistBuildWorker.KEY_PHASE, 0)
                            ) { GenPhase.Starting }
                            _generateState.value = GenerateUiState.Working(
                                phase,
                                p.getInt(PlaylistBuildWorker.KEY_DONE, 0),
                                p.getInt(PlaylistBuildWorker.KEY_TOTAL, 0),
                            )
                        }
                        WorkInfo.State.SUCCEEDED -> {
                            val result = withContext(Dispatchers.IO) {
                                val f = File(
                                    getApplication<Application>().filesDir,
                                    PlaylistBuildWorker.RESULT_FILE,
                                )
                                ResultModelCodec.decode(if (f.exists()) f.readText() else null)
                            }
                            if (result != null) _generateState.value = GenerateUiState.Done(result)
                        }
                        WorkInfo.State.FAILED -> _generateState.value = GenerateUiState.Error(
                            info.outputData.getString(PlaylistBuildWorker.KEY_ERROR) ?: "Generation failed"
                        )
                        WorkInfo.State.CANCELLED -> Unit
                    }
                }
        }
    }

    fun setFolder(folder: SelectedFolder) { _folder.value = folder }
    fun setSearchSubfolders(v: Boolean) = _options.update { it.copy(searchSubfolders = v) }
    fun setAlphabetize(v: Boolean) = _options.update { it.copy(alphabetize = v) }

    fun clearResult() { _scanState.value = ScanUiState.Idle }

    fun scan() {
        val f = _folder.value ?: return
        val opts = _options.value
        _scanState.value = ScanUiState.Scanning()
        viewModelScope.launch {
            try {
                val model = withContext(Dispatchers.IO) {
                    val volume = StorageVolumes.forUri(getApplication(), f.uri)
                    val probe = AudioProbes.forVolume(getApplication(), volume)
                    val settings = SettingsRepository(getApplication())
                    val audioExts = settings.audioExtensions.first()
                    val scanOptions = ScanOptions(
                        recursive = opts.searchSubfolders,
                        alphabetize = opts.alphabetize,
                        audioExtensions = audioExts
                    )
                    val scanPlan = PlaylistPlanner().plan(volume, scanOptions)
                    // Metadata pass: real durations + drop unreadable files (cache-accelerated).
                    val analyzed = MetadataPass.run(volume, probe, scanPlan, audioExts) { done, total ->
                        _scanState.value = ScanUiState.Scanning(done, total)
                    }
                    val renamePlan = RenamePlanner().plan(
                        volume,
                        RenameOptions(
                            cleanNames = settings.cleanNames.first(),
                            renameHidden = settings.renameHidden.first()
                        )
                    )
                    PreviewModelBuilder.build(
                        analyzed.plan, renamePlan, analyzed.totalDurationMs, analyzed.unreadable
                    )
                }
                _scanState.value = ScanUiState.Ready(model)
            } catch (e: Exception) {
                _scanState.value = ScanUiState.Error(e.message ?: "Scan failed")
            }
        }
    }

    /**
     * Enqueues the playlist build as a unique foreground-service job. The worker re-derives
     * everything from the folder URI + settings, so it survives process death; progress and the
     * final result flow back through [generateState] via the WorkInfo observer in [init].
     */
    fun generate() {
        val f = _folder.value ?: return
        val opts = _options.value
        _generateState.value = GenerateUiState.Working(GenPhase.Starting, 0, 0)
        val request = OneTimeWorkRequestBuilder<PlaylistBuildWorker>()
            .setInputData(
                workDataOf(
                    PlaylistBuildWorker.KEY_TREE_URI to f.uri.toString(),
                    PlaylistBuildWorker.KEY_DISPLAY_NAME to f.displayName,
                    PlaylistBuildWorker.KEY_RECURSIVE to opts.searchSubfolders,
                    PlaylistBuildWorker.KEY_ALPHABETIZE to opts.alphabetize,
                )
            )
            .build()
        WorkManager.getInstance(getApplication())
            .enqueueUniqueWork(PlaylistBuildWorker.WORK_NAME, ExistingWorkPolicy.REPLACE, request)
    }

    /** Resets everything for "make another playlist". */
    fun resetWorkflow() {
        _scanState.value = ScanUiState.Idle
        _generateState.value = GenerateUiState.Idle
        WorkManager.getInstance(getApplication())
            .cancelUniqueWork(PlaylistBuildWorker.WORK_NAME)
    }
}
