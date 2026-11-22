package app.douyin.save

import org.junit.Assert.assertEquals
import org.junit.Test

class RestoredOrderTest {
    private fun e(id: Int) = HistoryEntry("$id", "视频$id", "", id.toLong(), true, "已保存到相册", "content://media/$id")

    private val snapshot = listOf(e(5), e(4), e(3), e(2), e(1))

    @Test
    fun middleEntryReturnsToItsSlot() {
        val current = snapshot - e(3)
        assertEquals(snapshot, restoredOrder(current, snapshot, listOf(e(3))))
    }

    @Test
    fun firstAndLastReturn() {
        val current = listOf(e(4), e(3), e(2))
        assertEquals(snapshot, restoredOrder(current, snapshot, listOf(e(5), e(1))))
    }

    @Test
    fun clearAllReturnsEverything() {
        assertEquals(snapshot, restoredOrder(emptyList(), snapshot, snapshot))
    }

    @Test
    fun entriesAddedSinceStayOnTop() {
        val current = listOf(e(7), e(6), e(5), e(4), e(2), e(1))
        assertEquals(listOf(e(7), e(6)) + snapshot, restoredOrder(current, snapshot, listOf(e(3))))
    }

    @Test
    fun entriesRemovedElsewhereStayRemoved() {
        // 3 was the undone delete; 1 disappeared some other way and must not come back.
        val current = listOf(e(5), e(4), e(2))
        assertEquals(listOf(e(5), e(4), e(3), e(2)), restoredOrder(current, snapshot, listOf(e(3))))
    }

    @Test
    fun alreadyPresentEntryIsNotDuplicated() {
        assertEquals(snapshot, restoredOrder(snapshot, snapshot, listOf(e(3))))
    }

    @Test
    fun resultIsCappedLikeAdd() {
        val big = (60 downTo 1).map(::e)
        val current = (65 downTo 61).map(::e)
        val restored = restoredOrder(current, big, big)
        assertEquals(HISTORY_LIMIT, restored.size)
        assertEquals(e(65), restored.first())
    }
}
