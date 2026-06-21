package kr.co.edoli.edoltool

import kr.co.edoli.edoltool.feature.home.toolCatalog
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class ToolCatalogTest {
    @Test
    fun toolIdsAreUnique() {
        val ids = toolCatalog.map { it.id }

        assertEquals(ids.size, ids.toSet().size)
    }

    @Test
    fun catalogHasStarterTools() {
        assertTrue(toolCatalog.size >= 6)
    }
}
