package com.cliplist.app.workflow

import android.app.Application
import android.net.Uri
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.cliplist.app.settings.SettingsRepository
import com.cliplist.scan.AudioExtensions
import com.cliplist.scan.PlaylistPlanner
import com.cliplist.scan.PlaylistWriter
import com.cliplist.scan.PreviewModel
import com.cliplist.scan.PreviewModelBuilder
import com.cliplist.scan.RenameExecution
import com.cliplist.scan.RenameExecutor
import com.cliplist.scan.RenameOptions
import com.cliplist.scan.RenamePlan
import com.cliplist.scan.RenamePlanner
import com.cliplist.scan.ResultModel
import com.cliplist.scan.ResultModelBuilder
import com.cliplist.scan.ScanOptions
import com.cliplist.scan.ScanPlan
import com.cliplist.storage.SafTreeVolume
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

data class WorkflowOptions(
    val searchSubfolders: Boolean = true,
    val alphabetize: Boolean = true,
    val cleanNames: Boolean = false,
    val renameHidden: Boolean = false,
    val writeCoverArt: Boolean = false,
)

data class SelectedFolder(val uri: Uri, val displayName: String)

sealed interface ScanUiState {
    data object Idle : ScanUiState
    data object Scanning : ScanUiState
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

    // Raw plans retained from the last scan, needed to actually execute.
    private var lastScanPlan: ScanPlan? = null
    private var lastRenamePlan: RenamePlan? = null
    private var lastScanOptions: ScanOptions? = null

    fun setFolder(folder: SelectedFolder) { _folder.value = folder }
    fun setSearchSubfolders(v: Boolean) = _options.update { it.copy(searchSubfolders = v) }
    fun setAlphabetize(v: Boolean) = _options.update { it.copy(alphabetize = v) }
    fun setCleanNames(v: Boolean) = _options.update { it.copy(cleanNames = v) }
    fun setRenameHidden(v: Boolean) = _options.update { it.copy(renameHidden = v) }
    fun setWriteCoverArt(v: Boolean) = _options.update { it.copy(writeCoverArt = v) }

    fun clearResult() { _scanState.value = ScanUiState.Idle }

    fun scan() {
        val f = _folder.value ?: return
        val opts = _options.value
        _scanState.value = ScanUiState.Scanning
        viewModelScope.launch {
            try {
                val model = withContext(Dispatchers.IO) {
                    val volume = SafTreeVolume(getApplication(), f.uri)
                    val scanOptions = ScanOptions(
                        recursive = opts.searchSubfolders,
                        alphabetize = opts.alphabetize,
                        audioExtensions = SettingsRepository(getApplication()).audioExtensions.first()
                    )
                    val scanPlan = PlaylistPlanner().plan(volume, scanOptions)
                    val renamePlan = RenamePlanner().plan(
                        volume,
                        RenameOptions(cleanNames = opts.cleanNames, renameHidden = opts.renameHidden)
                    )
                    lastScanPlan = scanPlan
                    lastRenamePlan = renamePlan
                    lastScanOptions = scanOptions
                    PreviewModelBuilder.build(scanPlan, renamePlan)
                }
                _scanState.value = ScanUiState.Ready(model)
            } catch (e: Exception) {
                _scanState.value = ScanUiState.Error(e.message ?: "Scan failed")
            }
        }
    }

    /**
     * Applies renames (if any), re-scans so playlists reference the cleaned names, writes the
     * playlists with progress, and publishes a ResultModel. No-op if no scan has been run.
     */
    fun generate() {
        val f = _folder.value ?: return
        val scanPlan = lastScanPlan ?: return
        val renamePlan = lastRenamePlan ?: return
        val scanOptions = lastScanOptions ?: return
        val writeCoverArt = _options.value.writeCoverArt
        _generateState.value = GenerateUiState.Working(GenPhase.Starting, 0, scanPlan.folders.size)
        viewModelScope.launch {
            try {
                val result = withContext(Dispatchers.IO) {
                    val volume = SafTreeVolume(getApplication(), f.uri)
                    var renameExec: RenameExecution? = null
                    if (renamePlan.ops.isNotEmpty()) {
                        _generateState.value =
                            GenerateUiState.Working(GenPhase.Cleaning, 0, renamePlan.ops.size)
                        renameExec = RenameExecutor(volume).execute(renamePlan)
                    }
                    // After renames, filenames changed — re-scan so the .m3u lists the new names.
                    val planToWrite = if (renamePlan.ops.isNotEmpty())
                        PlaylistPlanner().plan(volume, scanOptions) else scanPlan
                    val report = PlaylistWriter(volume).execute(planToWrite) { done, total ->
                        _generateState.value =
                            GenerateUiState.Working(GenPhase.Writing, done, total)
                    }
                    // Optional cover art: drop a bundled folder.jpg into each music folder that
                    // doesn't already have one (never overwrite the user's existing art).
                    if (writeCoverArt) {
                        val artBytes = getApplication<Application>().assets
                            .open("folder.jpg").use { it.readBytes() }
                        planToWrite.folders.forEach { fp ->
                            val hasArt = volume.children(fp.folder)
                                .any { it.name.equals("folder.jpg", ignoreCase = true) }
                            if (!hasArt) volume.writeFile(fp.folder, "folder.jpg", artBytes, "image/jpeg")
                        }
                    }
                    ResultModelBuilder.build(report, renameExec)
                }
                _generateState.value = GenerateUiState.Done(result)
            } catch (e: Exception) {
                _generateState.value = GenerateUiState.Error(e.message ?: "Generation failed")
            }
        }
    }

    /** Resets everything for "make another playlist". */
    fun resetWorkflow() {
        _scanState.value = ScanUiState.Idle
        _generateState.value = GenerateUiState.Idle
    }
}
