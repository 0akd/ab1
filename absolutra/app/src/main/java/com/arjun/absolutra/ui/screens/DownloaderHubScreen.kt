package com.arjun.absolutra.ui.screens

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.Tab
import androidx.compose.material3.TabRow
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import com.arjun.absolutra.downloader.audio.Mp3PlayerScreen
import com.arjun.absolutra.downloader.frames.FrameExtractorScreen
import com.arjun.absolutra.downloader.video.VideoPlayerScreen

@Composable
fun DownloaderHubScreen(modifier: Modifier = Modifier) {
    var selectedTabIndex by remember { mutableIntStateOf(0) }
    val tabs = listOf("Video", "Audio", "Frames")

    Column(modifier = modifier.fillMaxSize()) {
        TabRow(selectedTabIndex = selectedTabIndex) {
            tabs.forEachIndexed { index, title ->
                Tab(
                    selected = selectedTabIndex == index,
                    onClick = { selectedTabIndex = index },
                    text = { Text(title) }
                )
            }
        }

        when (selectedTabIndex) {
            0 -> VideoPlayerScreen()
            1 -> Mp3PlayerScreen()
            2 -> FrameExtractorScreen()
        }
    }
}
