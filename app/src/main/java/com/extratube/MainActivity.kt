package com.extratube

import android.Manifest
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.core.app.ActivityCompat
import androidx.core.view.WindowCompat
import com.extratube.presentation.navigation.NavGraph
import com.extratube.presentation.theme.ExtraTubeTheme
import dagger.hilt.android.AndroidEntryPoint

/**
 * Single Activity — the only Activity in the app.
 *
 * @AndroidEntryPoint enables Hilt injection into this Activity (required for
 * hiltViewModel() calls in Compose screens and for the NavGraph to work).
 *
 * All navigation is handled by NavGraph (Compose Navigation).
 * All screens are Composables — no Fragments, no XML layouts.
 */
@AndroidEntryPoint
class MainActivity : ComponentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // Request POST_NOTIFICATIONS permission on API 33+.
        // Without user grant, the PlaybackService notification is silently suppressed.
        // The foreground service still runs, but Android may kill it as an invisible ANR.
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            ActivityCompat.requestPermissions(
                this,
                arrayOf(Manifest.permission.POST_NOTIFICATIONS),
                /* requestCode = */ NOTIFICATION_PERMISSION_REQUEST_CODE
            )
        }

        // Enable edge-to-edge display: content draws behind status bar + nav bar.
        // Screens use Modifier.systemBarsPadding() / navigationBarsPadding() for insets.
        WindowCompat.setDecorFitsSystemWindows(window, false)
        enableEdgeToEdge()

        setContent {
            ExtraTubeTheme {
                NavGraph()
            }
        }
    }

    companion object {
        private const val NOTIFICATION_PERMISSION_REQUEST_CODE = 1001
    }
}
