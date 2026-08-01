package com.example.pepper_person_id_poc.ui.component

import android.media.MediaPlayer
import android.graphics.SurfaceTexture
import android.view.Surface
import android.view.TextureView
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import com.example.pepper_person_id_poc.domain.config.LoadTestVideoOption

@Composable
fun SampleVideoPlayer(selection: LoadTestVideoOption, modifier: Modifier = Modifier) {
    val assetPath = selection.assetPath ?: return
    MutedAssetVideo(selection.displayName, assetPath, modifier.background(Color.Black))
}

@Composable
private fun MutedAssetVideo(label: String, assetPath: String, modifier: Modifier = Modifier) {
    val context = LocalContext.current
    var videoSurface by remember { mutableStateOf<Surface?>(null) }
    var mediaPlayer by remember { mutableStateOf<MediaPlayer?>(null) }
    var error by remember { mutableStateOf<String?>(null) }

    fun releasePlayer() {
        mediaPlayer?.runCatching { release() }
        mediaPlayer = null
    }

    LaunchedEffect(videoSurface, assetPath) {
        val surface = videoSurface ?: return@LaunchedEffect
        releasePlayer()
        error = null
        runCatching {
            val descriptor = context.assets.openFd(assetPath)
            MediaPlayer().also { player ->
                mediaPlayer = player
                player.setDataSource(descriptor.fileDescriptor, descriptor.startOffset, descriptor.length)
                descriptor.close()
                player.setSurface(surface)
                player.setVolume(0f, 0f)
                player.isLooping = true
                player.setOnPreparedListener { it.start() }
                player.setOnErrorListener { _, _, _ ->
                    error = "再生エラー"
                    true
                }
                player.prepareAsync()
            }
        }.onFailure { error = "再生エラー" }
    }

    DisposableEffect(Unit) { onDispose { releasePlayer() } }

    Column(modifier) {
        Text(label, color = Color.White, style = MaterialTheme.typography.labelSmall)
        AndroidView(
            factory = { viewContext ->
                TextureView(viewContext).apply {
                    surfaceTextureListener = object : TextureView.SurfaceTextureListener {
                        override fun onSurfaceTextureAvailable(texture: SurfaceTexture, width: Int, height: Int) {
                            videoSurface = Surface(texture)
                        }

                        override fun onSurfaceTextureSizeChanged(texture: SurfaceTexture, width: Int, height: Int) = Unit

                        override fun onSurfaceTextureDestroyed(texture: SurfaceTexture): Boolean {
                            videoSurface?.release()
                            videoSurface = null
                            return true
                        }

                        override fun onSurfaceTextureUpdated(texture: SurfaceTexture) = Unit
                    }
                }
            },
            modifier = Modifier.fillMaxWidth().weight(1f),
        )
        error?.let { Text(it, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.labelSmall) }
    }
}
