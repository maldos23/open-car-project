package org.opencarproject.launcher.core.pure

import org.opencarproject.launcher.core.model.CatalogItemKind
import org.opencarproject.launcher.core.model.Cell
import org.opencarproject.launcher.core.model.GridSpec
import org.opencarproject.launcher.core.model.LayoutDefinition
import org.opencarproject.launcher.core.model.PlacedItem
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Test

/** Tests for the layout JSON codec (round trip and leniency). */
class JsonLayoutCodecTest {

    private val codec = JsonLayoutCodec()

    @Test
    fun roundTrip_preservesData() {
        val layout = LayoutDefinition(
            grid = GridSpec(6, 4),
            items = listOf(
                PlacedItem(
                    id = "widget-1",
                    kind = CatalogItemKind.WIDGET,
                    componentKey = "pkg/Clock",
                    widgetId = 1,
                    cell = Cell(0, 0),
                    span = Cell(1, 2),
                    title = "Reloj",
                    packageName = "pkg",
                ),
                PlacedItem(
                    id = "app-pkg/Main",
                    kind = CatalogItemKind.APP,
                    componentKey = "pkg/Main",
                    widgetId = -1,
                    cell = Cell(1, 2),
                    span = Cell(1, 1),
                    title = "App",
                    packageName = "pkg",
                ),
            ),
        )
        val decoded = codec.decode(codec.encode(layout))
        assertEquals(layout, decoded)
    }

    @Test
    fun decode_returnsNullForGarbage() {
        assertNull(codec.decode("{esto-no-es-json"))
    }

    @Test
    fun decode_ignoresUnknownKeys() {
        val raw = """{"grid":{"columns":6,"rows":4},"items":[],"campoFuturo":"x"}"""
        val decoded = codec.decode(raw)
        assertNotNull(decoded)
        assertEquals(6, decoded!!.grid.columns)
        assertEquals(0, decoded.items.size)
    }

    @Test
    fun decode_dropsItemsOfUnknownKindInsteadOfLosingLayout() {
        val raw = """{"grid":{"columns":12,"rows":4},"columnScale":2,"items":[
            {"id":"app-a","kind":"APP","componentKey":"pkg/A","cell":{"row":0,"column":0},"span":{"row":1,"column":1},"title":"A","packageName":"pkg"},
            {"id":"x","kind":"GONE_KIND","componentKey":"builtin/x","cell":{"row":1,"column":0},"span":{"row":1,"column":2},"title":"X","packageName":"pkg"}
        ]}"""
        val layout = JsonLayoutCodec().decode(raw)!!
        org.junit.Assert.assertEquals(listOf("app-a"), layout.items.map { it.id })
    }
}
