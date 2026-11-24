package com.yiner.videodownloader

import java.io.File
import java.nio.file.Files
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

// Robolectric only because HistoryStore uses Android's org.json.
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class HistoryStoreTest {
    private lateinit var file: File
    private lateinit var store: HistoryStore

    private fun entry(id: String, uri: String? = "content://media/external/video/media/$id", at: Long = id.toLong()) =
        HistoryEntry(id, "视频$id", "作者", at, uri != null, "已保存到相册", uri)

    @Before
    fun setUp() {
        file = File(Files.createTempDirectory("history").toFile(), "history.json")
        store = HistoryStore(file)
    }

    @Test
    fun missingPlatformReadsAsDouyinAndXRoundTrips() {
        file.writeText(
            """[{"awemeId":"1","title":"旧记录","author":"甲","savedAt":1,"success":true,"message":"已保存到相册","contentUri":"content://old"}]""",
        )
        assertEquals("抖音", store.load().single().platform)
        store.add(entry("2").copy(platform = "X"))
        assertEquals(listOf("X", "抖音"), HistoryStore(file).load().map { it.platform })
    }

    @Test
    fun removeDropsOnlyMatchingEntriesAndKeepsOrder() {
        listOf("1", "2", "3", "4").forEach { store.add(entry(it)) }
        assertEquals(2, store.remove(listOf(entry("2"), entry("4"))))
        assertEquals(listOf(entry("3"), entry("1")), store.load())
        // Survives a fresh instance reading the same file.
        assertEquals(listOf(entry("3"), entry("1")), HistoryStore(file).load())
    }

    @Test
    fun removeAllClearsHistory() {
        listOf("1", "2").forEach { store.add(entry(it)) }
        assertEquals(2, store.remove(store.load()))
        assertTrue(store.load().isEmpty())
    }

    @Test
    fun removeHandlesEntriesWithoutFile() {
        store.add(entry("1", uri = null))
        store.add(entry("2"))
        assertEquals(1, store.remove(listOf(entry("1", uri = null))))
        assertEquals(listOf(entry("2")), store.load())
    }

    @Test
    fun removeUnknownEntryIsNoOp() {
        store.add(entry("1"))
        val before = file.readText()
        assertEquals(0, store.remove(listOf(entry("9"))))
        assertEquals(0, store.remove(emptyList()))
        assertEquals(before, file.readText())
    }

    @Test
    fun removeOnMissingOrCorruptFileDoesNotCrash() {
        assertEquals(0, store.remove(listOf(entry("1"))))
        file.writeText("{ not json")
        assertEquals(0, store.remove(listOf(entry("1"))))
    }

    @Test
    fun addStillCapsAtThirtyNewestFirst() {
        (1..35).forEach { store.add(entry("$it")) }
        val loaded = store.load()
        assertEquals(30, loaded.size)
        assertEquals(entry("35"), loaded.first())
        assertEquals(entry("6"), loaded.last())
    }

    @Test
    fun restorePutsSingleEntryBackInPlace() {
        listOf("1", "2", "3").forEach { store.add(entry(it)) }
        val snapshot = store.load() // 3, 2, 1
        store.remove(listOf(entry("2")))
        store.restore(snapshot, listOf(entry("2")))
        assertEquals(snapshot, store.load())
        assertEquals(snapshot, HistoryStore(file).load())
    }

    @Test
    fun restoreAfterClearBringsEverythingBack() {
        listOf("1", "2", "3").forEach { store.add(entry(it)) }
        val snapshot = store.load()
        store.remove(snapshot)
        assertTrue(store.load().isEmpty())
        store.restore(snapshot, snapshot)
        assertEquals(snapshot, store.load())
    }

    @Test
    fun restoreKeepsNewDownloadOnTop() {
        listOf("1", "2").forEach { store.add(entry(it)) }
        val snapshot = store.load()
        store.remove(listOf(entry("1")))
        store.add(entry("3"))
        store.restore(snapshot, listOf(entry("1")))
        assertEquals(listOf(entry("3"), entry("2"), entry("1")), store.load())
    }

    @Test
    fun restoringTwiceDoesNotDuplicate() {
        listOf("1", "2").forEach { store.add(entry(it)) }
        val snapshot = store.load()
        store.remove(listOf(entry("1")))
        store.restore(snapshot, listOf(entry("1")))
        store.restore(snapshot, listOf(entry("1")))
        assertEquals(snapshot, store.load())
    }

    @Test
    fun restoreNothingLeavesFileUntouched() {
        store.add(entry("1"))
        val before = file.readText()
        store.restore(store.load(), emptyList())
        assertEquals(before, file.readText())
    }
}
