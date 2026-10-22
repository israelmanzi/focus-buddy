package buddy.app

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import buddy.app.ui.App

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val controller = (application as BuddyApplication).controller
        setContent { App(controller) }
    }

    override fun onStart() {
        super.onStart()
        (application as BuddyApplication).controller.onAppForegrounded()
    }

    override fun onStop() {
        super.onStop()
        if (!isChangingConfigurations) (application as BuddyApplication).controller.onAppBackgrounded()
    }
}
