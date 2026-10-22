package buddy.app.art

import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import buddy.core.Catalog

// TEMPORARY placeholder until the real DecorArt.kt lands; delete on merge.
fun DrawScope.drawDecor(item: Catalog.DecorItem, topLeft: Offset, size: Size) {
    val hue = listOf(0xFFE8B4A0, 0xFFA8C8A0, 0xFFA0B8D8, 0xFFE8D8A0)[item.variant]
    drawRoundRect(Color(hue), topLeft, size, CornerRadius(size.minDimension * 0.15f))
    drawRoundRect(Color(0xFF4A342E), topLeft, size, CornerRadius(size.minDimension * 0.15f), style = Stroke(size.minDimension * 0.04f))
}
