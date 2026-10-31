package buddy.app

import android.os.Bundle
import android.os.PowerManager
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import buddy.app.ui.App

class MainActivity : ComponentActivity() {
    private val notificationPermission = registerForActivityResult(ActivityResultContracts.RequestPermission()) {}

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        ActivityTracker.notificationLauncher = notificationPermission
        val controller = (application as BuddyApplication).controller
        setContent { App(controller) }
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
