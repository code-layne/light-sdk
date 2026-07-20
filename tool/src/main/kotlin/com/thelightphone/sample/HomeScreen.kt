package com.thelightphone.sample

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import com.thelightphone.sdk.InitialScreen
import com.thelightphone.sdk.LightScreen
import com.thelightphone.sdk.LightViewModel
import com.thelightphone.sdk.SealedLightActivity
import com.thelightphone.sdk.SimpleLightScreen
import com.thelightphone.sdk.ui.LightIcon
import com.thelightphone.sdk.ui.LightIcons
import com.thelightphone.sdk.ui.LightText
import com.thelightphone.sdk.ui.LightTextVariant
import com.thelightphone.sdk.ui.LightTheme
import com.thelightphone.sdk.ui.LightThemeController
import com.thelightphone.sdk.ui.LightThemeTokens
import com.thelightphone.sdk.ui.gridUnitsAsDp
import com.thelightphone.sdk.ui.lightClickable
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow

class HomeScreenViewModel : LightViewModel<Unit>() {

    private val generator = BrownNoiseGenerator()

    private val _isPlaying = MutableStateFlow(false)
    val isPlaying: StateFlow<Boolean> = _isPlaying

    fun toggle() {
        if (_isPlaying.value) stop() else start()
    }

    private fun start() {
        generator.start()
        _isPlaying.value = true
    }

    private fun stop() {
        generator.stop()
        _isPlaying.value = false
    }

    /**
     * Foreground-only playback: LightOS does not (yet) let a tool keep audio
     * running in the background, so stop when the app is paused rather than
     * leave a silent, battery-burning AudioTrack alive off-screen.
     */
    override fun onAppPause() {
        super.onAppPause()
        if (_isPlaying.value) stop()
    }

    override fun onCleared() {
        super.onCleared()
        generator.stop()
    }
}

@InitialScreen
class HomeScreen(sealedActivity: SealedLightActivity) :
    LightScreen<Unit, HomeScreenViewModel>(sealedActivity) {

    override val viewModelClass: Class<HomeScreenViewModel>
        get() = HomeScreenViewModel::class.java

    override fun createViewModel() = HomeScreenViewModel()

    @Composable
    override fun Content() {
        val isPlaying by viewModel.isPlaying.collectAsState()
        val themeColors by LightThemeController.colors.collectAsState()

        LightTheme(colors = themeColors) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .background(LightThemeTokens.colors.background)
                    .padding(2f.gridUnitsAsDp()),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center,
            ) {
                LightText(
                    text = "Brown Noise",
                    variant = LightTextVariant.Heading,
                    modifier = Modifier.padding(bottom = 3f.gridUnitsAsDp()),
                )

                LightIcon(
                    icon = if (isPlaying) LightIcons.STOP else LightIcons.PLAY,
                    size = 6f,
                    modifier = Modifier.lightClickable { viewModel.toggle() },
                )

                LightText(
                    text = if (isPlaying) "Playing" else "Tap to play",
                    variant = LightTextVariant.Copy,
                    lighten = true,
                    modifier = Modifier.padding(top = 3f.gridUnitsAsDp()),
                )

                LightText(
                    text = "Stops when you leave the tool",
                    variant = LightTextVariant.Detail,
                    lighten = true,
                    modifier = Modifier.padding(top = 1f.gridUnitsAsDp()),
                )
            }
        }
    }
}
