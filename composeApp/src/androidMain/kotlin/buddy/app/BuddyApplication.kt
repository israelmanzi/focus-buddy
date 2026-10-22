package buddy.app

import android.app.Application
import kotlinx.coroutines.MainScope

class BuddyApplication : Application() {
    lateinit var controller: AppController
        private set

    override fun onCreate() {
        super.onCreate()
        controller = AppController(AndroidPlatform(this), MainScope())
    }
}
