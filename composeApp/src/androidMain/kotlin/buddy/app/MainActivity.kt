package buddy.app

import android.os.Bundle
import android.os.PowerManager
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.activity.result.contract.ActivityResultContracts
import buddy.app.art.LocalAnimationActive
import buddy.app.ui.App

class MainActivity : ComponentActivity() {
    private val notificationPermission = registerForActivityResult(ActivityResultContracts.RequestPermission()) {}

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        ActivityTracker.notificationLauncher = notificationPermission
        val controller = (application as BuddyApplication).controller
        setContent {
            // Stop the animation clock while the activity is stopped: no frames, no wakeups.
            var active by remember { mutableStateOf(true) }
            DisposableEffect(Unit) {
                val observer = androidx.lifecycle.LifecycleEventObserver { _, event ->
                    when (event) {
                        androidx.lifecycle.Lifecycle.Event.ON_START -> active = true
                        androidx.lifecycle.Lifecycle.Event.ON_STOP -> active = false
                        else -> Unit
                    }
                }
                lifecycle.addObserver(observer)
                onDispose { lifecycle.removeObserver(observer) }
            }
            androidx.compose.runtime.CompositionLocalProvider(LocalAnimationActive provides active) {
                App(controller)
            }
        }
    }

    override fun onStart() {
        super.onStart()
        (application as BuddyApplication).controller.onAppForegrounded()
    }

    override fun onStop() {
        super.onStop()
        // Turning the screen off is not leaving the app: locking the phone must not end a session.
        val screenOff = !getSystemService(PowerManager::class.java).isInteractive
        if (!isChangingConfigurations && !screenOff) (application as BuddyApplication).controller.onAppBackgrounded()
    }

    override fun onDestroy() {
        if (ActivityTracker.notificationLauncher === notificationPermission) ActivityTracker.notificationLauncher = null
        super.onDestroy()
    }
}
