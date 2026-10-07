package com.example

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.example.ui.AppScreen
import com.example.ui.MainViewModel
import com.example.ui.screens.HomeScreen
import com.example.ui.screens.PreviewScreen
import com.example.ui.screens.ProcessingScreen
import com.example.ui.screens.ProjectsScreen
import com.example.ui.screens.SettingsScreen
import com.example.ui.screens.VideoSettingsScreen
import com.example.ui.theme.NavyBackground
import com.example.ui.theme.Video2PhotoTheme

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            Video2PhotoTheme {
                val viewModel: MainViewModel = viewModel()
                Video2PhotoApp(viewModel = viewModel)
            }
        }
    }
}

@Composable
fun Video2PhotoApp(viewModel: MainViewModel) {
    val currentScreen by viewModel.currentScreen.collectAsStateWithLifecycle()
    val selectedVideo by viewModel.selectedVideo.collectAsStateWithLifecycle()
    val selectedQuality by viewModel.selectedQuality.collectAsStateWithLifecycle()
    val processingProgress by viewModel.processingProgress.collectAsStateWithLifecycle()
    val slideshowResult by viewModel.slideshowResult.collectAsStateWithLifecycle()
    val previewProject by viewModel.previewProject.collectAsStateWithLifecycle()
    val savedProjects by viewModel.savedProjects.collectAsStateWithLifecycle()
    val cacheSizeBytes by viewModel.cacheSizeBytes.collectAsStateWithLifecycle()
    val isLoading by viewModel.isLoading.collectAsStateWithLifecycle()

    val snackbarHostState = remember { SnackbarHostState() }

    Scaffold(
        snackbarHost = { SnackbarHost(snackbarHostState) },
        containerColor = NavyBackground,
        modifier = Modifier
            .fillMaxSize()
            .statusBarsPadding()
            .navigationBarsPadding()
    ) { innerPadding ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .background(NavyBackground)
        ) {
            AnimatedContent(
                targetState = currentScreen,
                transitionSpec = { fadeIn() togetherWith fadeOut() },
                label = "ScreenTransition"
            ) { screen ->
                when (screen) {
                    AppScreen.HOME -> {
                        HomeScreen(
                            viewModel = viewModel,
                            isLoading = isLoading
                        )
                    }
                    AppScreen.VIDEO_SETTINGS -> {
                        VideoSettingsScreen(
                            viewModel = viewModel,
                            videoInfo = selectedVideo,
                            selectedQuality = selectedQuality
                        )
                    }
                    AppScreen.PROCESSING -> {
                        ProcessingScreen(
                            viewModel = viewModel,
                            videoInfo = selectedVideo,
                            progress = processingProgress
                        )
                    }
                    AppScreen.PREVIEW -> {
                        PreviewScreen(
                            viewModel = viewModel,
                            slideshowResult = slideshowResult,
                            previewProject = previewProject
                        )
                    }
                    AppScreen.MY_PROJECTS -> {
                        ProjectsScreen(
                            viewModel = viewModel,
                            projects = savedProjects
                        )
                    }
                    AppScreen.SETTINGS -> {
                        SettingsScreen(
                            viewModel = viewModel,
                            cacheSizeBytes = cacheSizeBytes
                        )
                    }
                }
            }
        }
    }
}
