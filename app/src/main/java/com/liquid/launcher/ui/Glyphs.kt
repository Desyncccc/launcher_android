package com.liquid.launcher.ui

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.graphics.vector.addPathNodes
import androidx.compose.ui.unit.dp

/** Line glyphs in the spirit of SF Symbols (drawn from scratch, 24×24 grid). */
object Glyphs {
    private fun stroke(name: String, d: String, w: Float = 1.9f) = ImageVector.Builder(name, 24.dp, 24.dp, 24f, 24f)
        .addPath(
            pathData = addPathNodes(d), fill = null, stroke = SolidColor(Color.Black), strokeLineWidth = w,
            strokeLineCap = StrokeCap.Round, strokeLineJoin = StrokeJoin.Round,
        ).build()

    private fun fill(name: String, d: String) = ImageVector.Builder(name, 24.dp, 24.dp, 24f, 24f)
        .addPath(pathData = addPathNodes(d), fill = SolidColor(Color.Black)).build()

    val Search = stroke("search", "M17.5 10.5a7 7 0 1 1-14 0a7 7 0 1 1 14 0Z M15.6 15.6L20.5 20.5", 2.2f)
    val Plus = stroke("plus", "M12 5v14M5 12h14", 2.2f)
    val Minus = stroke("minus", "M6 12h12", 2.6f)
    val Check = stroke("check", "M5 12.5l4.5 4.5L19 7.5", 2.3f)
    val Close = stroke("close", "M6.5 6.5l11 11M17.5 6.5l-11 11", 2.2f)
    val Share = stroke("share", "M12 3.5v11.5M8 7.5l4-4 4 4M8 10.5H6.5a1.5 1.5 0 0 0-1.5 1.5v7.5A1.5 1.5 0 0 0 6.5 21h11a1.5 1.5 0 0 0 1.5-1.5V12a1.5 1.5 0 0 0-1.5-1.5H16")
    val Trash = stroke("trash", "M4 6.5h16M10 10.5v6M14 10.5v6M6 6.5l1 12.5a2 2 0 0 0 2 1.8h6a2 2 0 0 0 2-1.8l1-12.5M9 6.5V4.2h6v2.3")
    val MinusCircle = stroke("minuscircle", "M12 3a9 9 0 1 0 0 18a9 9 0 1 0 0-18Z M8 12h8")
    val Pencil = stroke("pencil", "M4 20h4L19 9a2.1 2.1 0 0 0-4-4L4 16v4Z M13.5 6.5l4 4")
    val Apps = stroke("apps", "M5 4h4a1 1 0 0 1 1 1v4a1 1 0 0 1-1 1H5a1 1 0 0 1-1-1V5a1 1 0 0 1 1-1Z M15 4h4a1 1 0 0 1 1 1v4a1 1 0 0 1-1 1h-4a1 1 0 0 1-1-1V5a1 1 0 0 1 1-1Z M5 14h4a1 1 0 0 1 1 1v4a1 1 0 0 1-1 1H5a1 1 0 0 1-1-1v-4a1 1 0 0 1 1-1Z M15 14h4a1 1 0 0 1 1 1v4a1 1 0 0 1-1 1h-4a1 1 0 0 1-1-1v-4a1 1 0 0 1 1-1Z")
    val Info = stroke("info", "M12 3a9 9 0 1 0 0 18a9 9 0 1 0 0-18Z M12 11v5.5 M12 7.6v.4")
    val Gear = stroke("gear", "M12 9a3 3 0 1 0 0 6a3 3 0 1 0 0-6Z M12 2.5v2.6M12 18.9v2.6M2.5 12h2.6M18.9 12h2.6M5.3 5.3l1.8 1.8M16.9 16.9l1.8 1.8M5.3 18.7l1.8-1.8M16.9 7.1l1.8-1.8")
    val Widgets = stroke("widgets", "M5 4h4.5a1 1 0 0 1 1 1v4.5a1 1 0 0 1-1 1H5a1 1 0 0 1-1-1V5a1 1 0 0 1 1-1Z M14.5 4H19a1 1 0 0 1 1 1v4.5a1 1 0 0 1-1 1h-4.5a1 1 0 0 1-1-1V5a1 1 0 0 1 1-1Z M5 13.5h14a1 1 0 0 1 1 1V19a1 1 0 0 1-1 1H5a1 1 0 0 1-1-1v-4.5a1 1 0 0 1 1-1Z")
    val Palette = stroke("palette", "M12 3a9 9 0 1 0 0 18c1 0 1.6-.7 1.6-1.5 0-.9-.8-1.3-.8-2.1 0-.9.7-1.6 1.6-1.6H17a4 4 0 0 0 4-4C21 6.9 17 3 12 3Z M7.5 11.5h.01 M10 7.5h.01 M14.5 7.5h.01", 2f)
    val Sun = stroke("sun", "M12 8a4 4 0 1 0 0 8a4 4 0 1 0 0-8Z M12 2.5v2M12 19.5v2M2.5 12h2M19.5 12h2M5.3 5.3l1.4 1.4M17.3 17.3l1.4 1.4M5.3 18.7l1.4-1.4M17.3 6.7l1.4-1.4")
    val Moon = stroke("moon", "M20 14.5A8 8 0 1 1 9.5 4a6.5 6.5 0 0 0 10.5 10.5Z")
    val Auto = stroke("auto", "M12 3a9 9 0 1 0 0 18a9 9 0 1 0 0-18Z M12 3v18")
    val Mic = stroke("mic", "M12 3a3 3 0 0 0-3 3v6a3 3 0 0 0 6 0V6a3 3 0 0 0-3-3Z M5.5 11a6.5 6.5 0 0 0 13 0 M12 17.5v3")
    val Image = stroke("image", "M5 4.5h14a1.5 1.5 0 0 1 1.5 1.5v12a1.5 1.5 0 0 1-1.5 1.5H5A1.5 1.5 0 0 1 3.5 18V6A1.5 1.5 0 0 1 5 4.5Z M3.5 16l5-5 4 4 3-3 4.5 4.5 M15.5 8.5h.01")
    val Home = stroke("home", "M4 11l8-7 8 7v8.5a1 1 0 0 1-1 1h-4.5V15h-5v5.5H5a1 1 0 0 1-1-1Z")
    val ChevronRight = stroke("chev", "M9.5 6l6 6-6 6", 2.2f)
    val ChevronDown = stroke("chevd", "M6 9.5l6 6 6-6", 2.2f)
    val Select = stroke("select", "M6 4.5h12A1.5 1.5 0 0 1 19.5 6v12a1.5 1.5 0 0 1-1.5 1.5H6A1.5 1.5 0 0 1 4.5 18V6A1.5 1.5 0 0 1 6 4.5Z M8.5 12l2.5 2.5 4.5-5")
    val Folder = stroke("folder", "M3.5 7.5A2 2 0 0 1 5.5 5.5h3.6l2 2h7.4a2 2 0 0 1 2 2v7.5a2 2 0 0 1-2 2h-13a2 2 0 0 1-2-2Z")
    val Bolt = fill("bolt", "M13.5 2L4.5 13.6h6.3L9.8 22l9.2-11.9h-6.4Z")
    val Phone = stroke("phoneglyph", "M8 2.8h8a1.6 1.6 0 0 1 1.6 1.6v15.2a1.6 1.6 0 0 1-1.6 1.6H8a1.6 1.6 0 0 1-1.6-1.6V4.4A1.6 1.6 0 0 1 8 2.8Z M10.5 5.3h3", 1.8f)
    val More = fill("more", "M6 10.5a1.5 1.5 0 1 1 0 3a1.5 1.5 0 1 1 0-3Z M12 10.5a1.5 1.5 0 1 1 0 3a1.5 1.5 0 1 1 0-3Z M18 10.5a1.5 1.5 0 1 1 0 3a1.5 1.5 0 1 1 0-3Z")
    val Globe = stroke("globe", "M12 3a9 9 0 1 0 0 18a9 9 0 1 0 0-18Z M3 12h18 M12 3c2.5 2.7 3.6 5.7 3.6 9s-1.1 6.3-3.6 9c-2.5-2.7-3.6-5.7-3.6-9S9.5 5.7 12 3Z")
    val Bag = stroke("bag", "M5 8h14l-1 12H6Z M9 8V6.5a3 3 0 0 1 6 0V8")
    val Stack = stroke("stack", "M4 9h16v10H4z M6 6h12 M8 3.5h8")
}
