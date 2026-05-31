package com.cliplist.app.workflow

import android.app.Application
import android.net.Uri
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.cliplist.scan.AudioExtensions
import com.cliplist.scan.PlaylistPlanner
import com.cliplist.scan.PreviewModel
import com.cliplist.scan.PreviewModelBuilder
import com.cliplist.scan.RenameOptions
import com.cliplist.scan.RenamePlanner
import com.cliplist.scan.ScanOptions
import com.cliplist.storage.SafTreeVolume
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/** The four Home toggles. Defaults match spec §3a. */
data class WorkflowOptions(
    val searchSubfolders: Boolean = true,
    val alphabetize: Boolean = true,
    val cleanNames: Boolean = false,
    val renameHidden: Boolean = false,
)

/** A folder the user granted via SAF. */
data class SelectedFolder(val uri: Uri, val displayName: String)

sealed interface ScanUiState {
    data object Idle : ScanUiState
    data object Scanning : ScanUiState
    data class Ready(val model: PreviewModel) : ScanUiState
    data class Error(val message: String) : ScanUiState
}

/**
 * Activity-scoped: shared by Home (sets folder/options, triggers scan) and Preview (reads result).
 * Scans run on Dispatchers.IO because the SAF volume does ContentResolver I/O.
 */
class ScanViewModel(app: Application) : AndroidViewModel(app) {

    private val _options = MutableStateFlow(WorkflowOptions())
    val options: StateFlow<WorkflowOptions> = _options.asStateFlow()

    private val _folder = MutableStateFlow<SelectedFolder?>(null)
    val folder: StateFlow<SelectedFolder?> = _folder.asStateFlow()

    private val _scanState = MutableStateFlow<ScanUiState>(ScanUiState.Idle)
    val scanState: StateFlow<ScanUiState> = _scanState.asStateFlow()

    fun setFolder(folder: SelectedFolder) { _folder.value = folder }
    fun setSearchSubfolders(v: Boolean) = _options.update { it.copy(searchSubfolders = v) }
    fun setAlphabetize(v: Boolean) = _options.update { it.copy(alphabetize = v) }
    fun setCleanNames(v: Boolean) = _options.update { it.copy(cleanNames = v) }
    fun setRenameHidden(v: Boolean) = _options.update { it.copy(renameHidden = v) }

    /** Resets the scan result (e.g. when returning to Home to re-run). */
    fun clearResult() { _scanState.value = ScanUiState.Idle }

    fun scan() {
        val f = _folder.value ?: return
        val opts = _options.value
        _scanState.value = ScanUiState.Scanning
        viewModelScope.launch {
            try {
                val model = withContext(Dispatchers.IO) {
                    val volume = SafTreeVolume(getApplication(), f.uri)
                    val scanPlan = PlaylistPlanner().plan(
                        volume,
                        ScanOptions(
                            recursive = opts.searchSubfolders,
                            alphabetize = opts.alphabetize,
                            audioExtensions = AudioExtensions.DEFAULT
                        )
                    )
                    val renamePlan = RenamePlanner().plan(
                        volume,
                        RenameOptions(cleanNames = opts.cleanNames, renameHidden = opts.renameHidden)
                    )
                    PreviewModelBuilder.build(scanPlan, renamePlan)
                }
                _scanState.value = ScanUiState.Ready(model)
            } catch (e: Exception) {
                _scanState.value = ScanUiState.Error(e.message ?: "Scan failed")
            }
        }
    }
}
