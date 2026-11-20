package app.douyin.save.parser

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class RouterJsonTest {
    @Test
    fun extractsObjectAndKeepsBracesInsideStrings() {
        val html = """
            <html><script>
            window._ROUTER_DATA = {"a":"{not}","b":1}</script></html>
        """.trimIndent()
        assertEquals("""{"a":"{not}","b":1}""", RouterJson.fromHtml(html))
    }

    @Test
    fun returnsNullWhenMarkerOrBraceIsMissing() {
        assertNull(RouterJson.fromHtml("<html>no data</html>"))
        assertNull(RouterJson.fromHtml("window._ROUTER_DATA = not-json"))
        assertNull(RouterJson.sliceObject("{\"a\":1", 0))
    }
}
