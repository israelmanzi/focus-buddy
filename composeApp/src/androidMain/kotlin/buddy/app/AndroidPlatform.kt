package buddy.app

import android.content.Context
import java.io.File

class AndroidPlatform(private val context: Context) : Platform {
    override val name = "android"
    override val dataDir: File = File(context.filesDir, "buddy")
    override val leavingAppInterrupts = true
}
