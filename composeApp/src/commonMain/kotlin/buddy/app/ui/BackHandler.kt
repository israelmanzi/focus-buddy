package buddy.app.ui

import androidx.compose.runtime.Composable

/** Handles the system back gesture where the host has one; a no-op on desktop. */
@Composable
expect fun PlatformBackHandler(enabled: Boolean, onBack: () -> Unit)
