package com.thelightphone.sdk.emulator

import android.os.Bundle
import android.util.Log
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.Orientation
import androidx.compose.foundation.gestures.draggable
import androidx.compose.foundation.gestures.rememberDraggableState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import com.thelightphone.sdk.server.LightSdkServer
import com.thelightphone.sdk.server.LightSdkServer.filterVerifiedTools
import com.thelightphone.sdk.server.LightSdkServer.queryInstalledClients
import com.thelightphone.sdk.server.LightSdkServer.runningAsSystemApp
import com.thelightphone.sdk.shared.LightResult

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        enableEdgeToEdge()
        super.onCreate(savedInstanceState)
        WindowCompat.getInsetsController(window, window.decorView).apply {
            hide(WindowInsetsCompat.Type.systemBars())
            systemBarsBehavior = WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
        }
        if (!runningAsSystemApp) {
            Log.w(
                "LightEmulator",
                "WARNING: LightOS emulator is NOT running as a system app and may not work."
            )
        }
        LightSdkServer.customServiceMethodResolver = { callingId, methodId, payload ->
            Log.d("LightEmulator", "unknown service method: $methodId")
            LightResult.Error(LightResult.ErrorCode.Unknown)
        }
        LightSdkServer.pushEndpointFetcher = { callingPackage, token, vapid ->
            // TODO this will refer to internal http server eventually
            Log.d("LightEmulator", "getting push endpoint for token: $token, vapid: $vapid")
            "http://localhost/push/$token"
        }
        setContent {
            MaterialTheme(
                colorScheme = darkColorScheme(
                    background = Color.Black,
                    surface = Color.Black,
                    onBackground = Color.White,
                    onSurface = Color.White,
                )
            ) {
                ToolList(
                    fetchExternalTools = {
                        queryInstalledClients()
                            .filter { LightSdkServer.isSdkVersionSupported(it.sdkVersion) }
                            .filterVerifiedTools()
                            .map {
                                val appInfo = it.packageInfo.applicationInfo!!
                                val label = packageManager.getApplicationLabel(appInfo).toString()
                                Tool(label, it.packageInfo.packageName)
                            }
                    }, launchPackage = {
                        packageManager.getLaunchIntentForPackage(it)?.let { intent ->
                            intent.addFlags(android.content.Intent.FLAG_ACTIVITY_NO_ANIMATION)
                            val options =
                                android.app.ActivityOptions.makeCustomAnimation(this, 0, 0)
                            startActivity(intent, options.toBundle())
                        }
                    })
            }
        }
    }
}

private data class Tool(val label: String, val packageName: String?)

private val defaultTools = listOf(
    Tool("Placeholder", null)
)

@Composable
private fun ToolList(
    fetchExternalTools: suspend () -> List<Tool>,
    launchPackage: (String) -> Unit
) {
    // TODO page indicator
    val toolPageSize = 6
    var currentPageIndex by remember { mutableIntStateOf(0) }
    var pages by remember { mutableStateOf(defaultTools.chunked(toolPageSize)) }
    val currentPage by remember {
        derivedStateOf {
            pages.getOrNull(currentPageIndex) ?: pages.first()
        }
    }
    LaunchedEffect(Unit) {
        val externalTools = fetchExternalTools()
        pages = (defaultTools + externalTools).chunked(toolPageSize)
    }
    Surface(modifier = Modifier.fillMaxSize()) {
        Column(
            modifier = Modifier.draggable(
                orientation = Orientation.Vertical,
                state = rememberDraggableState {},
                onDragStopped = { velocity ->
                    if (velocity < -200f && currentPageIndex < pages.size - 1) {
                        currentPageIndex++
                    } else if (velocity > 200f && currentPageIndex > 0) {
                        currentPageIndex--
                    }
                }
            ),
            verticalArrangement = Arrangement.spacedBy(18.dp, Alignment.CenterVertically),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            for (tool in currentPage) {
                Text(
                    text = tool.label,
                    fontSize = 35.sp,
                    modifier = Modifier.clickable {
                        tool.packageName?.let { launchPackage(it) }
                    }
                )
            }
        }
    }
}
