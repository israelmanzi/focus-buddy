package buddy.app.ui

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import buddy.app.AppController

@Composable
fun App(controller: AppController) {
    val state by controller.state.collectAsState()
    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        Text("Focus Buddy — ${state.acorns} acorns")
    }
}
