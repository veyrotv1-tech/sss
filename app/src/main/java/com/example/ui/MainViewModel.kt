package com.example.ui

import android.app.Application
import android.net.Uri
import android.widget.Toast
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.data.db.AppDatabase
import com.example.data.model.SavedProject
import com.example.video.GallerySaver
import com.example.video.OutputQuality
import com.example.video.ProcessingProgress
import com.example.video.ProcessingStage
import com.example.video.SampleVideoHelper
import com.example.video.SlideshowResult
import com.example.video.VideoInfo
import com.example.video.VideoSlideshowCreator
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import java.io.File

enum class AppScreen {
    HOME,
    VIDEO_SETTINGS,
    PROCESSING,
    PREVIEW,
    MY_PROJECTS,
    SETTINGS
}

class MainViewModel(application: Application) : AndroidViewModel(application) {

    private val db = AppDatabase.getDatabase(application)
    private val projectDao = db.projectDao()

    val savedProjects: StateFlow<List<SavedProject>> = projectDao.getAllProjects()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    private val _currentScreen = MutableStateFlow(AppScreen.HOME)
    val currentScreen: StateFlow<AppScreen> = _currentScreen.asStateFlow()

    private val _selectedVideo = MutableStateFlow<VideoInfo?>(null)
    val selectedVideo: StateFlow<VideoInfo?> = _selectedVideo.asStateFlow()

    private val _selectedQuality = MutableStateFlow(OutputQuality.QUALITY_1080P)
    val selectedQuality: StateFlow<OutputQuality> = _selectedQuality.asStateFlow()

    private val _processingProgress = MutableStateFlow(ProcessingProgress())
    val processingProgress: StateFlow<ProcessingProgress> = _processingProgress.asStateFlow()

    private val _slideshowResult = MutableStateFlow<SlideshowResult?>(null)
    val slideshowResult: StateFlow<SlideshowResult?> = _slideshowResult.asStateFlow()

    private val _previewProject = MutableStateFlow<SavedProject?>(null)
    val previewProject: StateFlow<SavedProject?> = _previewProject.asStateFlow()

    private val _cacheSizeBytes = MutableStateFlow(0L)
    val cacheSizeBytes: StateFlow<Long> = _cacheSizeBytes.asStateFlow()

    private val _isLoading = MutableStateFlow(false)
    val isLoading: StateFlow<Boolean> = _isLoading.asStateFlow()

    private var processingJob: Job? = null

    init {
        refreshCacheSize()
    }

    fun navigateTo(screen: AppScreen) {
        _currentScreen.value = screen
    }

    fun setQuality(quality: OutputQuality) {
        _selectedQuality.value = quality
    }

    fun selectVideo(uri: Uri) {
        viewModelScope.launch {
            _isLoading.value = true
            try {
                val videoInfo = VideoSlideshowCreator.inspectVideo(getApplication(), uri)
                _selectedVideo.value = videoInfo
                _currentScreen.value = AppScreen.VIDEO_SETTINGS
            } catch (e: Exception) {
                Toast.makeText(getApplication(), "Failed to read video: ${e.message}", Toast.LENGTH_LONG).show()
            } finally {
                _isLoading.value = false
            }
        }
    }

    fun loadSampleVideo() {
        viewModelScope.launch {
            _isLoading.value = true
            try {
                val sampleUri = SampleVideoHelper.createSampleVideo(getApplication())
                if (sampleUri != null) {
                    selectVideo(sampleUri)
                } else {
                    Toast.makeText(getApplication(), "Failed to create demo video", Toast.LENGTH_SHORT).show()
                }
            } catch (e: Exception) {
                Toast.makeText(getApplication(), "Error loading sample: ${e.message}", Toast.LENGTH_SHORT).show()
            } finally {
                _isLoading.value = false
            }
        }
    }

    fun startProcessing() {
        val video = _selectedVideo.value ?: return
        _currentScreen.value = AppScreen.PROCESSING
        _processingProgress.value = ProcessingProgress(
            stage = ProcessingStage.ANALYZING,
            totalScreenshots = video.estimatedScreenshots,
            statusMessage = "Starting video conversion..."
        )

        processingJob?.cancel()
        processingJob = viewModelScope.launch {
            try {
                val result = VideoSlideshowCreator.createVideoFromScreenshots(
                    context = getApplication(),
                    videoInfo = video,
                    quality = _selectedQuality.value,
                    onProgress = { progress ->
                        _processingProgress.value = progress
                    }
                )
                _slideshowResult.value = result

                // Save to Room Database
                val project = SavedProject(
                    title = "V2P_${video.name}",
                    originalVideoName = video.name,
                    videoPath = result.outputFile.absolutePath,
                    thumbnailPath = result.thumbnailFile.absolutePath,
                    durationSeconds = result.durationMs / 1000L,
                    screenshotCount = result.screenshotCount,
                    fileSizeBytes = result.fileSizeBytes,
                    resolution = _selectedQuality.value.label
                )
                val id = projectDao.insertProject(project)
                _previewProject.value = project.copy(id = id)

                _currentScreen.value = AppScreen.PREVIEW
                refreshCacheSize()
            } catch (e: Exception) {
                if (e !is kotlinx.coroutines.CancellationException) {
                    _processingProgress.value = _processingProgress.value.copy(
                        stage = ProcessingStage.ERROR,
                        error = e.localizedMessage ?: "Conversion failed"
                    )
                }
            }
        }
    }

    fun cancelProcessing() {
        processingJob?.cancel()
        _processingProgress.value = ProcessingProgress(
            stage = ProcessingStage.IDLE,
            statusMessage = "Processing cancelled"
        )
        _currentScreen.value = AppScreen.VIDEO_SETTINGS
    }

    fun previewSavedProject(project: SavedProject) {
        _previewProject.value = project
        _currentScreen.value = AppScreen.PREVIEW
    }

    fun saveCurrentVideoToGallery() {
        val file = getCurrentVideoFile() ?: return
        viewModelScope.launch {
            val uri = GallerySaver.saveToGallery(getApplication(), file)
            if (uri != null) {
                Toast.makeText(getApplication(), "Saved to Gallery (Movies/Video2Photo)!", Toast.LENGTH_LONG).show()
            } else {
                Toast.makeText(getApplication(), "Failed to save video to Gallery", Toast.LENGTH_SHORT).show()
            }
        }
    }

    fun shareCurrentVideo() {
        val file = getCurrentVideoFile() ?: return
        GallerySaver.shareVideo(getApplication(), file)
    }

    private fun getCurrentVideoFile(): File? {
        val resultFile = _slideshowResult.value?.outputFile
        if (resultFile != null && resultFile.exists()) return resultFile
        val projectPath = _previewProject.value?.videoPath
        if (projectPath != null) {
            val file = File(projectPath)
            if (file.exists()) return file
        }
        return null
    }

    fun deleteProject(project: SavedProject) {
        viewModelScope.launch {
            try {
                projectDao.deleteProject(project)
                File(project.videoPath).delete()
                File(project.thumbnailPath).delete()
                refreshCacheSize()
                Toast.makeText(getApplication(), "Project deleted", Toast.LENGTH_SHORT).show()
            } catch (e: Exception) {
                Toast.makeText(getApplication(), "Error deleting project: ${e.message}", Toast.LENGTH_SHORT).show()
            }
        }
    }

    fun cleanCache() {
        viewModelScope.launch {
            val cacheDir = getApplication<Application>().cacheDir
            cacheDir.listFiles()?.forEach { file ->
                if (file.name.startsWith("v2p_") || file.name.endsWith(".jpg") || file.name.endsWith(".mp4")) {
                    file.deleteRecursively()
                }
            }
            refreshCacheSize()
            Toast.makeText(getApplication(), "Temporary cache cleared!", Toast.LENGTH_SHORT).show()
        }
    }

    private fun refreshCacheSize() {
        viewModelScope.launch {
            var size = 0L
            val cacheDir = getApplication<Application>().cacheDir
            cacheDir.walkTopDown().forEach { size += it.length() }
            _cacheSizeBytes.value = size
        }
    }
}
