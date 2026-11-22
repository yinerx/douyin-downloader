package app.douyin.save

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Test

class DeleteSessionTest {
    private class Prompt(val name: String) : ConsentPrompt

    /** Scripted MediaFiles: each URI maps to the outcomes of successive delete() calls. */
    private class FakeMedia(override val sdkInt: Int) : MediaFiles {
        val outcomes = mutableMapOf<String, ArrayDeque<FileDelete>>()
        val existing = mutableSetOf<String>()
        val deleteCalls = mutableListOf<String>()
        val batchCalls = mutableListOf<List<String>>()
        var batchPrompt: ConsentPrompt? = Prompt("batch")

        fun on(uri: String, vararg results: FileDelete) {
            outcomes[uri] = ArrayDeque(results.toList())
        }

        override fun delete(uri: String): FileDelete {
            deleteCalls += uri
            val queue = outcomes[uri] ?: error("unexpected delete($uri)")
            return if (queue.size > 1) queue.removeFirst() else queue.first()
        }

        override fun stillExists(uri: String) = uri in existing

        override fun batchConsent(uris: List<String>): ConsentPrompt? {
            batchCalls += uris
            return batchPrompt
        }
    }

    private fun entry(id: String, uri: String? = "content://media/external/video/media/$id", success: Boolean = uri != null) =
        HistoryEntry(id, "视频$id", "作者", id.hashCode().toLong(), success, if (success) "已保存到相册" else "失败", uri)

    private fun DeleteStep.done(): DeleteResult = (this as DeleteStep.Done).result
    private fun DeleteStep.prompt(): ConsentPrompt = (this as DeleteStep.AskConsent).prompt

    // ── records only ──

    @Test
    fun recordsOnlyNeverTouchesFiles() {
        val media = FakeMedia(34)
        val entries = listOf(entry("1"), entry("2"), entry("3", uri = null))
        val result = DeleteSession(entries, withFiles = false, media).start().done()
        assertEquals(entries, result.removed)
        assertTrue(result.kept.isEmpty())
        assertTrue(media.deleteCalls.isEmpty())
        assertTrue(media.batchCalls.isEmpty())
        assertEquals("已删除 3 条记录，相册里的视频保留着", result.message)
    }

    @Test
    fun recordsOnlySingleMessage() {
        val result = DeleteSession(listOf(entry("1")), withFiles = false, FakeMedia(34)).start().done()
        assertEquals("已删除记录，相册里的视频保留着", result.message)
    }

    // ── direct deletes ──

    @Test
    fun ownedFilesAreDeletedWithoutAnyPrompt() {
        val media = FakeMedia(34)
        media.on(entry("1").contentUri!!, FileDelete.Deleted)
        media.on(entry("2").contentUri!!, FileDelete.Deleted)
        val result = DeleteSession(listOf(entry("1"), entry("2")), withFiles = true, media).start().done()
        assertEquals(2, result.removed.size)
        assertEquals(2, result.filesDeleted)
        assertTrue(media.batchCalls.isEmpty())
        assertEquals("已删除 2 条记录和 2 个视频", result.message)
    }

    @Test
    fun missingFileStillRemovesRecord() {
        val media = FakeMedia(34)
        media.on(entry("1").contentUri!!, FileDelete.Missing)
        val result = DeleteSession(listOf(entry("1")), withFiles = true, media).start().done()
        assertEquals(listOf(entry("1")), result.removed)
        assertEquals(1, result.filesMissing)
        assertEquals(0, result.filesDeleted)
        assertEquals("已删除 1 条记录；1 个视频本来就不在相册里", result.message)
    }

    @Test
    fun recordWithoutFileIsRemovedWithoutTouchingMedia() {
        val media = FakeMedia(34)
        val failed = entry("x", uri = null)
        val result = DeleteSession(listOf(failed), withFiles = true, media).start().done()
        assertEquals(listOf(failed), result.removed)
        assertTrue(media.deleteCalls.isEmpty())
        assertEquals("已删除 1 条记录", result.message)
    }

    @Test
    fun failedDeleteKeepsRecord() {
        val media = FakeMedia(28)
        media.on(entry("1").contentUri!!, FileDelete.Failed("没有权限删除这个文件"))
        media.on(entry("2").contentUri!!, FileDelete.Deleted)
        val result = DeleteSession(listOf(entry("1"), entry("2")), withFiles = true, media).start().done()
        assertEquals(listOf(entry("2")), result.removed)
        assertEquals(listOf(entry("1")), result.kept)
        assertEquals(1, result.filesFailed)
        assertEquals("已删除 1 条记录和 1 个视频；1 个视频删除失败，对应的记录已保留", result.message)
    }

    @Test
    fun allFailedMessageHasNoRemovedPart() {
        val media = FakeMedia(28)
        media.on(entry("1").contentUri!!, FileDelete.Failed("x"))
        media.on(entry("2").contentUri!!, FileDelete.Failed("x"))
        val result = DeleteSession(listOf(entry("1"), entry("2")), withFiles = true, media).start().done()
        assertTrue(result.removed.isEmpty())
        assertEquals("2 个视频删除失败，对应的记录已保留（2 个）", result.message)
    }

    @Test
    fun sharedFileIsDeletedOnceAndBothRecordsGo() {
        val media = FakeMedia(34)
        val uri = "content://media/external/video/media/9"
        val a = entry("a", uri)
        val b = entry("b", uri)
        media.on(uri, FileDelete.Deleted)
        val result = DeleteSession(listOf(a, b), withFiles = true, media).start().done()
        assertEquals(listOf(uri), media.deleteCalls)
        assertEquals(listOf(a, b), result.removed)
        assertEquals(1, result.filesDeleted)
    }

    @Test
    fun duplicateEntriesAreReportedOnce() {
        val media = FakeMedia(34)
        media.on(entry("1").contentUri!!, FileDelete.Deleted)
        val result = DeleteSession(listOf(entry("1"), entry("1")), withFiles = true, media).start().done()
        assertEquals(listOf(entry("1")), result.removed)
    }

    // ── Android 11+: one batch dialog ──

    @Test
    fun android11BatchesEveryUnownedFileIntoOneDialog() {
        val media = FakeMedia(30)
        media.on(entry("1").contentUri!!, FileDelete.NeedsConsent(null))
        media.on(entry("2").contentUri!!, FileDelete.Deleted)
        media.on(entry("3").contentUri!!, FileDelete.NeedsConsent(null))
        val session = DeleteSession(listOf(entry("1"), entry("2"), entry("3")), withFiles = true, media)
        val step = session.start()
        assertSame(media.batchPrompt, step.prompt())
        assertEquals(listOf(listOf(entry("1").contentUri!!, entry("3").contentUri!!)), media.batchCalls)

        val result = session.onConsent(granted = true).done()
        assertEquals(3, result.removed.size)
        assertEquals(3, result.filesDeleted)
        assertEquals("已删除 3 条记录和 3 个视频", result.message)
    }

    @Test
    fun android11DeclineKeepsOnlyThePromptedRecords() {
        val media = FakeMedia(33)
        media.on(entry("1").contentUri!!, FileDelete.NeedsConsent(null))
        media.on(entry("2").contentUri!!, FileDelete.Deleted)
        val session = DeleteSession(listOf(entry("1"), entry("2")), withFiles = true, media)
        session.start().prompt()
        val result = session.onConsent(granted = false).done()
        assertEquals(listOf(entry("2")), result.removed)
        assertEquals(listOf(entry("1")), result.kept)
        assertEquals(1, result.filesDeclined)
        assertEquals("已删除 1 条记录和 1 个视频；你在系统弹窗里没有允许，对应的记录已保留", result.message)
    }

    @Test
    fun android11GrantedButFileSurvivedCountsAsFailure() {
        val media = FakeMedia(34)
        val uri = entry("1").contentUri!!
        media.on(uri, FileDelete.NeedsConsent(null))
        media.existing += uri
        val session = DeleteSession(listOf(entry("1")), withFiles = true, media)
        session.start()
        val result = session.onConsent(granted = true).done()
        assertEquals(listOf(entry("1")), result.kept)
        assertEquals(1, result.filesFailed)
    }

    @Test
    fun android11WithoutBatchRequestFailsSafely() {
        val media = FakeMedia(30)
        media.batchPrompt = null
        media.on(entry("1").contentUri!!, FileDelete.NeedsConsent(null))
        val result = DeleteSession(listOf(entry("1")), withFiles = true, media).start().done()
        assertEquals(listOf(entry("1")), result.kept)
        assertEquals(1, result.filesFailed)
    }

    // ── Android 10: one dialog per file ──

    @Test
    fun android10AsksPerFileAndRetriesAfterGrant() {
        val media = FakeMedia(29)
        val p1 = Prompt("1")
        val p2 = Prompt("2")
        media.on(entry("1").contentUri!!, FileDelete.NeedsConsent(p1), FileDelete.Deleted)
        media.on(entry("2").contentUri!!, FileDelete.NeedsConsent(p2), FileDelete.Deleted)
        val session = DeleteSession(listOf(entry("1"), entry("2")), withFiles = true, media)
        assertSame(p1, session.start().prompt())
        assertSame(p2, session.onConsent(granted = true).prompt())
        val result = session.onConsent(granted = true).done()
        assertEquals(2, result.removed.size)
        assertEquals(2, result.filesDeleted)
        assertTrue(media.batchCalls.isEmpty())
        // Each file: first attempt + retry after the grant.
        assertEquals(4, media.deleteCalls.size)
    }

    @Test
    fun android10DeclineStopsAskingForTheRest() {
        val media = FakeMedia(29)
        media.on(entry("1").contentUri!!, FileDelete.NeedsConsent(Prompt("1")))
        media.on(entry("2").contentUri!!, FileDelete.NeedsConsent(Prompt("2")))
        val session = DeleteSession(listOf(entry("1"), entry("2")), withFiles = true, media)
        session.start()
        val result = session.onConsent(granted = false).done()
        assertTrue(result.removed.isEmpty())
        assertEquals(2, result.filesDeclined)
        assertEquals("你在系统弹窗里没有允许，对应的记录已保留（2 个）", result.message)
    }

    @Test
    fun android10GrantButRetryFails() {
        val media = FakeMedia(29)
        media.on(entry("1").contentUri!!, FileDelete.NeedsConsent(Prompt("1")), FileDelete.Failed("x"))
        val session = DeleteSession(listOf(entry("1")), withFiles = true, media)
        session.start()
        val result = session.onConsent(granted = true).done()
        assertEquals(listOf(entry("1")), result.kept)
        assertEquals(1, result.filesFailed)
    }

    @Test
    fun android10FileVanishedBeforeRetryCountsAsDeleted() {
        val media = FakeMedia(29)
        media.on(entry("1").contentUri!!, FileDelete.NeedsConsent(Prompt("1")), FileDelete.Missing)
        val session = DeleteSession(listOf(entry("1")), withFiles = true, media)
        session.start()
        assertEquals(listOf(entry("1")), session.onConsent(granted = true).done().removed)
    }

    @Test
    fun android10WithoutPromptFailsInsteadOfAsking() {
        val media = FakeMedia(29)
        media.on(entry("1").contentUri!!, FileDelete.NeedsConsent(null))
        media.on(entry("2").contentUri!!, FileDelete.NeedsConsent(Prompt("2")), FileDelete.Deleted)
        val session = DeleteSession(listOf(entry("1"), entry("2")), withFiles = true, media)
        assertEquals("2", (session.start().prompt() as Prompt).name)
        val result = session.onConsent(granted = true).done()
        assertEquals(listOf(entry("1")), result.kept)
    }

    // ── robustness ──

    @Test
    fun mixedOutcomesInOneClearAll() {
        val media = FakeMedia(31)
        media.on(entry("1").contentUri!!, FileDelete.Deleted)
        media.on(entry("2").contentUri!!, FileDelete.Missing)
        media.on(entry("3").contentUri!!, FileDelete.Failed("x"))
        media.on(entry("4").contentUri!!, FileDelete.NeedsConsent(null))
        val entries = listOf(entry("1"), entry("2"), entry("3"), entry("4"), entry("5", uri = null))
        val session = DeleteSession(entries, withFiles = true, media)
        session.start().prompt()
        val result = session.onConsent(granted = true).done()
        assertEquals(listOf(entry("1"), entry("2"), entry("4"), entry("5", uri = null)), result.removed)
        assertEquals(listOf(entry("3")), result.kept)
        assertEquals(2, result.filesDeleted)
        assertEquals(1, result.filesMissing)
        assertEquals(1, result.filesFailed)
    }

    @Test
    fun strayConsentAfterDoneChangesNothing() {
        val media = FakeMedia(34)
        media.on(entry("1").contentUri!!, FileDelete.Deleted)
        val session = DeleteSession(listOf(entry("1")), withFiles = true, media)
        val first = session.start().done()
        val again = session.onConsent(granted = false).done()
        assertEquals(first, again)
    }

    @Test
    fun consentBeforeStartNeverRemovesRecordsWithFiles() {
        val media = FakeMedia(34)
        val result = DeleteSession(listOf(entry("1")), withFiles = true, media).onConsent(true).done()
        assertTrue(result.removed.isEmpty())
        assertTrue(media.deleteCalls.isEmpty())
    }

    @Test(expected = IllegalStateException::class)
    fun startTwiceIsRejected() {
        val media = FakeMedia(34)
        media.on(entry("1").contentUri!!, FileDelete.Deleted)
        val session = DeleteSession(listOf(entry("1")), withFiles = true, media)
        session.start()
        session.start()
    }

    @Test
    fun emptySessionIsHarmless() {
        val result = DeleteSession(emptyList(), withFiles = true, FakeMedia(34)).start().done()
        assertTrue(result.removed.isEmpty())
        assertFalse(result.message.isBlank())
    }
}
