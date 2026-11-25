package com.yiner.videodownloader

import android.app.Application
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class MainViewModelHistoryTest {
    private lateinit var app: Application
    private lateinit var vm: MainViewModel

    private fun e(id: Int) = HistoryEntry("$id", "视频$id", "作者", id.toLong(), true, "已保存到相册", "content://media/$id")

    private val history get() = vm.state.value.history

    @Before
    fun setUp() {
        app = RuntimeEnvironment.getApplication()
        val store = HistoryStore(app)
        (1..4).forEach { store.add(e(it)) } // newest first: 4, 3, 2, 1
        vm = MainViewModel(app)
    }

    @Test
    fun deleteRemovesOnlyThatRecordAndOffersUndo() {
        vm.deleteHistory(listOf(e(3)))
        assertEquals(listOf(e(4), e(2), e(1)), history)
        assertEquals("已删除 1 条记录", vm.state.value.historyUndo?.message)
        // Persisted, not just in memory.
        assertEquals(listOf(e(4), e(2), e(1)), HistoryStore(app).load())
    }

    @Test
    fun undoRestoresOriginalPosition() {
        vm.deleteHistory(listOf(e(3)))
        vm.undoDelete(vm.state.value.historyUndo!!.id)
        assertEquals(listOf(e(4), e(3), e(2), e(1)), history)
        assertNull(vm.state.value.historyUndo)
        assertEquals(history, HistoryStore(app).load())
    }

    @Test
    fun clearEmptiesHistoryAndUndoBringsAllBack() {
        vm.clearHistory()
        assertTrue(history.isEmpty())
        assertEquals("已清空 4 条记录", vm.state.value.historyUndo?.message)
        vm.undoDelete(vm.state.value.historyUndo!!.id)
        assertEquals(listOf(e(4), e(3), e(2), e(1)), history)
    }

    @Test
    fun clearOnEmptyHistoryDoesNothing() {
        vm.clearHistory()
        val id = vm.state.value.historyUndo!!.id
        vm.undoDismissed(id)
        vm.clearHistory()
        assertNull(vm.state.value.historyUndo)
    }

    @Test
    fun undoAfterSnackbarDismissedIsIgnored() {
        vm.deleteHistory(listOf(e(2)))
        val id = vm.state.value.historyUndo!!.id
        vm.undoDismissed(id)
        assertNull(vm.state.value.historyUndo)
        vm.undoDelete(id)
        assertEquals(listOf(e(4), e(3), e(1)), history)
    }

    @Test
    fun onlyLatestDeleteCanBeUndone() {
        vm.deleteHistory(listOf(e(4)))
        val first = vm.state.value.historyUndo!!.id
        vm.deleteHistory(listOf(e(1)))
        val second = vm.state.value.historyUndo!!.id
        vm.undoDelete(first)
        assertEquals(listOf(e(3), e(2)), history)
        vm.undoDelete(second)
        assertEquals(listOf(e(3), e(2), e(1)), history)
    }

    @Test
    fun staleDismissDoesNotCancelNewerUndo() {
        vm.deleteHistory(listOf(e(4)))
        val first = vm.state.value.historyUndo!!.id
        vm.deleteHistory(listOf(e(1)))
        val second = vm.state.value.historyUndo!!.id
        vm.undoDismissed(first)
        assertEquals(second, vm.state.value.historyUndo?.id)
        vm.undoDelete(second)
        assertEquals(listOf(e(3), e(2), e(1)), history)
    }

    @Test
    fun deletingUnknownEntryIsNoOp() {
        vm.deleteHistory(listOf(e(9)))
        vm.deleteHistory(emptyList())
        assertEquals(listOf(e(4), e(3), e(2), e(1)), history)
        assertNull(vm.state.value.historyUndo)
    }

    @Test
    fun undoTwiceDoesNotDuplicate() {
        vm.deleteHistory(listOf(e(3)))
        val id = vm.state.value.historyUndo!!.id
        vm.undoDelete(id)
        vm.undoDelete(id)
        assertEquals(listOf(e(4), e(3), e(2), e(1)), history)
    }

    @Test
    fun copyingALinkDoesNotOfferItBackFromTheClipboard() {
        val url = "https://x.com/animel7316/status/2125245578590531786?a=1pY7pvdvyCZcA3wT3hc&s=20"
        vm.suppressOwnClipboard(url)
        vm.offerClipboard(url)
        assertNull(vm.state.value.clipboard)
        vm.offerClipboard("https://v.douyin.com/J4IQiholWbo/")
        assertEquals("https://v.douyin.com/J4IQiholWbo/", vm.state.value.clipboard?.url)
    }

    @Test
    fun retryUpdatesTheSameFailedRow() {
        val url = "https://x.com/animel7316/status/2125245578590531786?a=1&s=20"
        val failed = HistoryEntry(
            awemeId = "",
            title = url.take(40),
            author = "",
            savedAt = 9L,
            success = false,
            message = "下载失败",
            contentUri = null,
            platform = "X",
            sourceUrl = url,
            id = "failed-1",
        )
        HistoryStore(app).add(failed)
        vm = MainViewModel(app)
        vm.retryHistory(failed)
        val rows = vm.state.value.history.filter { it.id == "failed-1" }
        assertEquals(1, rows.size)
        assertEquals(url, rows.single().sourceUrl)
        assertEquals(false, rows.single().success)
    }

    @Test
    fun deletingSeveralUsesCount() {
        vm.deleteHistory(listOf(e(1), e(2)))
        assertEquals("已删除 2 条记录", vm.state.value.historyUndo?.message)
        assertEquals(listOf(e(4), e(3)), history)
    }
}
