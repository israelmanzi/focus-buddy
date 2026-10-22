package buddy.app.art

import androidx.compose.ui.graphics.Color
import buddy.core.Catalog.Theme

/**
 * Colours for one theme × variant. [main] and [pop] change per variant; the rest is the theme's
 * fixed shape-language palette.
 */
internal class Pal(
    val main: Color,
    val pop: Color,
    val accent: Color,
    val accent2: Color,
    val cream: Color,
    val wood: Color,
    val leaf: Color,
    val sky: Color,
    val skyLow: Color,
    val glow: Color,
    val v: Int,
)

private fun c(hex: Long) = Color(hex)

internal fun palette(theme: Theme, variant: Int): Pal {
    val v = variant.mod(4)
    return when (theme) {
        Theme.BASIC -> Pal(
            main = listOf(c(0xFFEBD9BE), c(0xFFBCD3E8), c(0xFFC5DDBB), c(0xFFF2CDC8))[v],
            pop = listOf(c(0xFFD9B98E), c(0xFF8FB3D6), c(0xFF94BF87), c(0xFFE7A49C))[v],
            accent = c(0xFFF7E3A6), accent2 = c(0xFFC9CED3), cream = c(0xFFFFF7EA), wood = c(0xFFE6C493),
            leaf = c(0xFF93C884), sky = c(0xFFB9DFF3), skyLow = c(0xFFE6F4F8), glow = c(0xFFFFEBB0), v = v,
        )
        Theme.COZY -> Pal(
            main = listOf(c(0xFFEA8A58), c(0xFFD65F57), c(0xFFE7AC52), c(0xFFB9707A))[v],
            pop = listOf(c(0xFFB9473E), c(0xFF8E3A3A), c(0xFFC0703A), c(0xFF7E4A5A))[v],
            accent = c(0xFFFBE6C8), accent2 = c(0xFF9B4B3B), cream = c(0xFFFFF1DD), wood = c(0xFFBE8156),
            leaf = c(0xFF87B26A), sky = c(0xFFF7B48E), skyLow = c(0xFFFCE3B6), glow = c(0xFFFFCB75), v = v,
        )
        Theme.GARDEN -> Pal(
            main = listOf(c(0xFF88C47D), c(0xFF67B69C), c(0xFFADC868), c(0xFF9DBE97))[v],
            pop = listOf(c(0xFFF4A3B8), c(0xFFF8D25E), c(0xFFC5A8EA), c(0xFFF39079))[v],
            accent = c(0xFFDB8459), accent2 = c(0xFFF8E27A), cream = c(0xFFFFF6E0), wood = c(0xFFDEB880),
            leaf = c(0xFF73B26A), sky = c(0xFFAEDDF0), skyLow = c(0xFFE4F5E6), glow = c(0xFFFFEFA8), v = v,
        )
        Theme.RETRO -> Pal(
            main = listOf(c(0xFFEBB53C), c(0xFF3FA19C), c(0xFFE77B3B), c(0xFF9CAA45))[v],
            pop = listOf(c(0xFFE77B3B), c(0xFFEBB53C), c(0xFF3FA19C), c(0xFFE77B3B))[v],
            accent = c(0xFF8F5B3A), accent2 = c(0xFFF2D9A6), cream = c(0xFFFAEBCF), wood = c(0xFFA86B42),
            leaf = c(0xFF7FA556), sky = c(0xFFF19A5B), skyLow = c(0xFFF7D27A), glow = c(0xFFFFC56B), v = v,
        )
        Theme.NIGHT -> Pal(
            main = listOf(c(0xFF4A5A94), c(0xFF7360A8), c(0xFF367A88), c(0xFF8E5795))[v],
            pop = listOf(c(0xFF8EA2E0), c(0xFFB9A3EE), c(0xFF79C4CC), c(0xFFD7A0D9))[v],
            accent = c(0xFFF4D06E), accent2 = c(0xFFFFF0C2), cream = c(0xFFEEE8F8), wood = c(0xFF5E4C72),
            leaf = c(0xFF5D9C8C), sky = c(0xFF242E5C), skyLow = c(0xFF4B4D8C), glow = c(0xFFFFD27A), v = v,
        )
    }
}
