package org.opencarproject.launcher.core.pure

/**
 * Pixel-art sprite written as text rows: each char is one pixel, `.` is transparent and any
 * other char is a key of the palette the drawing code maps to a color.
 */
class PixelSprite private constructor(val width: Int, val height: Int, private val pixels: List<String>) {

    /** Palette key at ([x], [y]), or `null` when transparent. */
    fun at(x: Int, y: Int): Char? = pixels[y][x].takeIf { it != TRANSPARENT }

    companion object {
        const val TRANSPARENT = '.'

        /** Validates that every row has the same width and only uses [keys] (or `.`). */
        fun parse(rows: List<String>, keys: String): PixelSprite {
            require(rows.isNotEmpty()) { "Empty sprite" }
            val width = rows.first().length
            rows.forEachIndexed { y, row ->
                require(row.length == width) { "Row $y is ${row.length} wide, expected $width" }
                row.forEach { require(it == TRANSPARENT || it in keys) { "Unknown pixel '$it' in row $y" } }
            }
            return PixelSprite(width, rows.size, rows)
        }

        /** Keys of the pixel cat: outline, grey, light grey (belly), dark grey (stripes), white, pink. */
        const val CAT_KEYS = "kgldwp"

        private val catBody = listOf(
            "kk...........kgk...kgk",
            "ggk..........kgpkkkpgk",
            "kgk.........kggggggggk",
            "kgk.kkkkkkkkkggwggwggk",
            "kggkgggdggdggggkggkggk",
            ".kgggggdggdggggggpgggk",
            "..kkggggggdgggpgkgkgpk",
            "...kgggggggggggggkgggk",
            "...kgglllllllggggggggk",
            "...kgglllllllllgkkkkk.",
        )

        /** Original grey 8-bit cat in two running frames (legs forward / back). */
        val CAT_FRAMES: List<PixelSprite> = listOf(
            listOf(
                "....kgkkgkkkgkkgk.....",
                "....kgkkgk.kgkkgk.....",
                ".....k..k...k..k......",
            ),
            listOf(
                "....kkgkkgkgkkgk......",
                ".....kgkkgkgkkgk......",
                "......k..k.k..k.......",
            ),
        ).map { legs -> parse(catBody + legs, CAT_KEYS) }
    }
}
