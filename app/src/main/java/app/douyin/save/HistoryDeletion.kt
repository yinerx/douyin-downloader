package app.douyin.save

/** A system prompt the user must accept before media we do not own can be deleted. */
internal interface ConsentPrompt

/** What happened when we tried to delete one gallery file without asking the user. */
internal sealed interface FileDelete {
    data object Deleted : FileDelete

    /** The file or its MediaStore row is already gone. */
    data object Missing : FileDelete

    /**
     * The file exists but this app may not delete it on its own (for example after a reinstall,
     * MediaStore no longer counts us as the owner). [prompt] is the per-item system dialog on
     * Android 10; on Android 11+ it may be null because one batch request covers every file.
     */
    data class NeedsConsent(val prompt: ConsentPrompt?) : FileDelete

    data class Failed(val reason: String) : FileDelete
}

/** Platform side of deleting saved videos. Implemented by [AndroidMediaFiles]; faked in tests. */
internal interface MediaFiles {
    val sdkInt: Int

    fun delete(uri: String): FileDelete

    /** Used after a system prompt to confirm the file is really gone. */
    fun stillExists(uri: String): Boolean

    /** Android 11+: one system dialog that deletes all [uris] when accepted. */
    fun batchConsent(uris: List<String>): ConsentPrompt?
}

internal sealed interface DeleteStep {
    data class AskConsent(val prompt: ConsentPrompt) : DeleteStep
    data class Done(val result: DeleteResult) : DeleteStep
}

internal data class DeleteResult(
    val withFiles: Boolean,
    val removed: List<HistoryEntry>,
    val kept: List<HistoryEntry>,
    val filesDeleted: Int,
    val filesMissing: Int,
    val filesFailed: Int,
    val filesDeclined: Int,
) {
    val message: String
        get() {
            if (!withFiles) {
                return if (removed.size == 1) "已删除记录，相册里的视频保留着" else "已删除 ${removed.size} 条记录，相册里的视频保留着"
            }
            val parts = mutableListOf<String>()
            if (removed.isNotEmpty()) {
                parts += if (filesDeleted > 0) {
                    "已删除 ${removed.size} 条记录和 $filesDeleted 个视频"
                } else {
                    "已删除 ${removed.size} 条记录"
                }
            }
            if (filesMissing > 0) parts += "$filesMissing 个视频本来就不在相册里"
            if (filesDeclined > 0) parts += "你在系统弹窗里没有允许，${keptFor(filesDeclined)}"
            if (filesFailed > 0) parts += "$filesFailed 个视频删除失败，${keptFor(filesFailed)}"
            return parts.joinToString("；").ifBlank { "没有删除任何内容" }
        }

    private fun keptFor(files: Int): String = "对应的记录已保留" + if (files > 1) "（$files 个）" else ""
}

/**
 * Deletes history entries, optionally together with their gallery files.
 *
 * A record is removed only when it has no file to worry about, or its file was deleted or was
 * already gone. If the user declines a system prompt or deletion fails, the record stays so the
 * file can still be found from the app.
 *
 * Not thread-safe; drive it from one coroutine at a time.
 */
internal class DeleteSession(
    entries: List<HistoryEntry>,
    private val withFiles: Boolean,
    private val media: MediaFiles,
) {
    private val entries = entries.distinct()

    private enum class Fate { Deleted, Missing, Failed, Declined }

    private val fates = linkedMapOf<String, Fate>()
    private val waiting = ArrayDeque<Pair<String, ConsentPrompt?>>()
    private var asking: List<String> = emptyList()
    private var batch = false
    private var started = false

    fun start(): DeleteStep {
        check(!started) { "DeleteSession.start() called twice" }
        started = true
        if (!withFiles) return finish()
        for (uri in fileUris()) {
            when (val outcome = media.delete(uri)) {
                FileDelete.Deleted -> fates[uri] = Fate.Deleted
                FileDelete.Missing -> fates[uri] = Fate.Missing
                is FileDelete.Failed -> fates[uri] = Fate.Failed
                is FileDelete.NeedsConsent -> waiting.addLast(uri to outcome.prompt)
            }
        }
        return next()
    }

    /** Call with the result of the system dialog from the last [DeleteStep.AskConsent]. */
    fun onConsent(granted: Boolean): DeleteStep {
        if (asking.isEmpty()) return finish()
        val answered = asking
        asking = emptyList()
        if (!granted) {
            answered.forEach { fates[it] = Fate.Declined }
            // One "no" is enough: do not keep popping dialogs for the remaining files.
            while (waiting.isNotEmpty()) fates[waiting.removeFirst().first] = Fate.Declined
            return finish()
        }
        for (uri in answered) {
            fates[uri] = if (batch) {
                // Android 11+: the system already deleted the files when the user accepted.
                if (media.stillExists(uri)) Fate.Failed else Fate.Deleted
            } else {
                // Android 10: accepting only grants write access; delete again now.
                when (media.delete(uri)) {
                    FileDelete.Deleted, FileDelete.Missing -> Fate.Deleted
                    else -> Fate.Failed
                }
            }
        }
        return next()
    }

    private fun next(): DeleteStep {
        if (waiting.isEmpty()) return finish()
        if (media.sdkInt >= 30) {
            val uris = waiting.map { it.first }
            waiting.clear()
            val prompt = media.batchConsent(uris)
            if (prompt == null) {
                uris.forEach { fates[it] = Fate.Failed }
                return finish()
            }
            batch = true
            asking = uris
            return DeleteStep.AskConsent(prompt)
        }
        while (waiting.isNotEmpty()) {
            val (uri, prompt) = waiting.removeFirst()
            if (prompt == null) {
                fates[uri] = Fate.Failed
                continue
            }
            batch = false
            asking = listOf(uri)
            return DeleteStep.AskConsent(prompt)
        }
        return finish()
    }

    private fun finish(): DeleteStep.Done {
        val removed = mutableListOf<HistoryEntry>()
        val kept = mutableListOf<HistoryEntry>()
        for (entry in entries) {
            val uri = entry.contentUri
            val safe = !withFiles || uri == null || fates[uri] == Fate.Deleted || fates[uri] == Fate.Missing
            if (safe) removed += entry else kept += entry
        }
        return DeleteStep.Done(
            DeleteResult(
                withFiles = withFiles,
                removed = removed,
                kept = kept,
                filesDeleted = fates.values.count { it == Fate.Deleted },
                filesMissing = fates.values.count { it == Fate.Missing },
                filesFailed = fates.values.count { it == Fate.Failed },
                filesDeclined = fates.values.count { it == Fate.Declined },
            ),
        )
    }

    private fun fileUris(): List<String> = entries.mapNotNull { it.contentUri }.distinct()
}
