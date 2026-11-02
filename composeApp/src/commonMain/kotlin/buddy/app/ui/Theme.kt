package buddy.app.ui

import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.datetime.toLocalDateTime

/** The hand-drawn palette. Every colour in the UI comes from here. */
object Ink {
    val outline = Color(0xFF4A342E)
    val paper = Color(0xFFFBF3E4)
    val paperDeep = Color(0xFFF3E3C7)
    val sky = Color(0xFFCDE8E5)
    val leaf = Color(0xFF7FB069)
    val leafDark = Color(0xFF5E8C4A)
    val berry = Color(0xFFE07A5F)
    val butter = Color(0xFFF2CC8F)
    val acorn = Color(0xFFB5763A)
    val dim = Color(0x99302420)
    val muted = Color(0xFF8C7468)

    // Surfaces and states that used to be inline literals.
    val card = Color.White
    val selected = Color(0xFFDDEBC8)
    val disabled = Color(0xFFD9CFC2)
    val dayOff = Color(0xFFEDE3D3)
    val bgTop = Color(0xFFBFE3E0)
    val bgBottom = Color(0xFFE9F4EC)
    val night = Color(0xFF302A26)
    val bubble = Color.White.copy(alpha = 0.85f)
    val onSelected = Color.White
    val clockFace = Color(0xFF8FC1B5)

    // Icon button tints.
    val roomsTint = Color(0xFFDDEFEA)
    val statsTint = Color(0xFFFCEBD0)
    val shopTint = Color(0xFFFADCD3)
    val decorateTint = Color(0xFFDCE6F5)
    val buddyTint = Color(0xFFF6E2C4)
    val settingsTint = Color(0xFFE8E2DA)
}

/** Spacing scale (dp). Prefer these over fresh literals. */
object Space {
    val xxs: Dp = 2.dp
    val xs: Dp = 4.dp
    val sm: Dp = 6.dp
    val md: Dp = 8.dp
    val lg: Dp = 10.dp
    val xl: Dp = 12.dp
    val xxl: Dp = 16.dp
    val xxxl: Dp = 24.dp
}

/** Corner radii, named by role. */
object Shapes {
    val cell = RoundedCornerShape(8.dp)
    val field = RoundedCornerShape(10.dp)
    val tile = RoundedCornerShape(12.dp)
    val card = RoundedCornerShape(14.dp)
    val icon = RoundedCornerShape(16.dp)
    val panel = RoundedCornerShape(20.dp)
    val bubble = RoundedCornerShape(22.dp)
    val pill = RoundedCornerShape(50)
    val dot = RoundedCornerShape(3.dp)
}

/** Outline weights, named by role. */
object Strokes {
    val hair: Dp = 1.dp
    val thin: Dp = 1.5.dp
    val line: Dp = 2.dp
    val bold: Dp = 2.5.dp
    val heavy: Dp = 3.dp
}

/** Type scale, named by role. */
object Type {
    val micro = 10.sp
    val caption = 11.sp
    val foot = 12.sp
    val small = 13.sp
    val body = 14.sp
    val lead = 15.sp
    val bodyLarge = 16.sp
    val section = 17.sp
    val button = 18.sp
    val heading = 20.sp
    val title = 22.sp
    val titleLarge = 24.sp
    val display = 26.sp
    val focus = 30.sp
    val clockSetup = 54.sp
    val clockBreak = 56.sp
    val clock = 64.sp
}

/** Layout bounds. The app keeps a phone-like column and caps sheet height. */
object Sizes {
    val appWidth: Dp = 560.dp
    val sheetWidth: Dp = 440.dp
    val dialogWidth: Dp = 360.dp
    val pickerWidth: Dp = 460.dp
    val sheetHeight: Dp = 720.dp
    val bubbleWidth: Dp = 360.dp
    val iconButton: Dp = 56.dp
    val closeButton: Dp = 48.dp
}

@Composable
fun BuddyTheme(content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = lightColorScheme(
            primary = Ink.leafDark, onPrimary = Color.White, background = Ink.paper, surface = Ink.paper,
            onSurface = Ink.outline, onBackground = Ink.outline, secondary = Ink.berry,
        ),
        content = content,
    )
}

fun label(size: TextUnit = Type.bodyLarge, weight: FontWeight = FontWeight.Bold, color: Color = Ink.outline) =
    TextStyle(fontSize = size, fontWeight = weight, color = color, letterSpacing = 0.5.sp)


/** The sky outside the room. */
data class Sky(
    val top: Color,
    val bottom: Color,
    val light: Color,
    val isNight: Boolean,
)

/**
 * Sky for the given moment. Sunrise and sunset come from a seasonal daylight model (12 h +/-
 * 2.5 h of daylight around the equinoxes), not fixed clock hours, so an autumn evening turns dark
 * when the sun actually sets. Northern-hemisphere approximation: the tropics and the southern
 * hemisphere will be off by up to an hour or two.
 */
fun skyFor(nowMs: Long, tz: kotlinx.datetime.TimeZone = kotlinx.datetime.TimeZone.currentSystemDefault()): Sky {
    val local = kotlin.time.Instant.fromEpochMilliseconds(nowMs).toLocalDateTime(tz)
    val daylight = 12.0 + 2.5 * kotlin.math.cos(2 * Math.PI * (local.date.dayOfYear - 172) / 365.0)
    val sunrise = 12.0 - daylight / 2
    val sunset = 12.0 + daylight / 2
    val h = local.hour + local.minute / 60.0
    val twilight = 0.75
    return when {
        h >= sunrise - twilight && h < sunrise + twilight ->
            Sky(Color(0xFFF3B9A0), Color(0xFFFFE3C7), Color(0xFFFFD79A), false)   // dawn
        h >= sunrise + twilight && h < sunset - twilight ->
            Sky(Color(0xFFBFE3E0), Color(0xFFE9F4EC), Color(0xFFF4D06F), false)   // day
        h >= sunset - twilight && h < sunset + twilight ->
            Sky(Color(0xFFE8A87C), Color(0xFFF6D7B0), Color(0xFFF08A5C), false)   // dusk
        else ->
            Sky(Color(0xFF1E2A44), Color(0xFF33415C), Color(0xFFF4EBD0), true)    // night
    }
}
