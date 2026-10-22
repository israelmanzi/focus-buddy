package buddy.app.art

import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asComposeCanvas
import androidx.compose.ui.graphics.drawscope.CanvasDrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.LayoutDirection
import buddy.core.Catalog
import org.jetbrains.skia.EncodedImageFormat
import org.jetbrains.skia.Font
import org.jetbrains.skia.FontMgr
import org.jetbrains.skia.FontStyle
import org.jetbrains.skia.Paint
import org.jetbrains.skia.Surface
import java.io.File
import kotlin.test.Test
import kotlin.test.assertTrue

/** Renders every decor item into docs/art/decor-preview.png (rows = kind, columns = theme × variant). */
class DecorPreviewTest {

    @Test
    fun renderPreviewGrid() {
        render(96, File("../docs/art/decor-preview.png"))
        // larger copy for close inspection (not committed)
        render(220, File("build/decor-preview-large.png"))
    }

    private fun render(cell: Int, out: File) {
        val label = (cell * 0.9f).toInt()
        val header = (cell * 0.3f).toInt()
        val kinds = Catalog.DecorKind.entries
        val cols = Catalog.Theme.entries.flatMap { t -> (0 until Catalog.VARIANTS).map { t to it } }
        val w = label + cols.size * cell
        val h = header + kinds.size * cell
        val surface = Surface.makeRasterN32Premul(w, h)
        val canvas = surface.canvas
        canvas.clear(0xFFFBF3E4.toInt())

        val font = Font(FontMgr.default.legacyMakeTypeface("", FontStyle.BOLD), cell * 0.13f)
        val ink = Paint().apply { color = 0xFF4A342E.toInt() }
        Catalog.Theme.entries.forEachIndexed { i, t ->
            canvas.drawString(t.label, (label + i * 4 * cell + 6).toFloat(), header * 0.7f, font, ink)
        }
        kinds.forEachIndexed { r, k -> canvas.drawString(k.label, 6f, (header + r * cell + cell / 2).toFloat(), font, ink) }

        val scope = CanvasDrawScope()
        scope.draw(Density(1f), LayoutDirection.Ltr, canvas.asComposeCanvas(), androidx.compose.ui.geometry.Size(w.toFloat(), h.toFloat())) {
            kinds.forEachIndexed { r, kind ->
                cols.forEachIndexed { c, (theme, v) ->
                    val x = label + c * cell.toFloat()
                    val y = header + r * cell.toFloat()
                    if (c % 4 == 0) drawLine(Color(0x334A342E), Offset(x, header.toFloat()), Offset(x, h.toFloat()))
                    drawRect(Color(0x144A342E), Offset(x + 4, y + 4), Size(cell - 8f, cell - 8f), style = Stroke(1f))
                    val pad = cell * 0.1f
                    drawDecor(Catalog.DecorItem(kind, theme, v), Offset(x + pad, y + pad), Size(cell - 2 * pad, cell - 2 * pad))
                }
            }
        }

        out.parentFile.mkdirs()
        out.writeBytes(surface.makeImageSnapshot().encodeToData(EncodedImageFormat.PNG)!!.bytes)
        assertTrue(out.length() > 0)
    }
}
