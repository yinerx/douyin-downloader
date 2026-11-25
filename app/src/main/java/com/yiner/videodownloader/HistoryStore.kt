package com.yiner.videodownloader

import android.content.Context
import org.json.JSONArray
import org.json.JSONObject
import java.io.File

internal data class HistoryEntry(
    val awemeId: String,
    val title: String,
    val author: String,
    val savedAt: Long,
    val success: Boolean,
    val message: String,
    val contentUri: String?,
    val platform: String = "抖音",
    /** 用户分享进来的原始链接。失败记录靠它复制和重新下载。 */
    val sourceUrl: String = "",
    /** 就地更新时用来认同一条记录。旧记录没有这个字段。 */
    val id: String = "",
)

internal class HistoryStore(private val file: File) {
    constructor(context: Context) : this(File(context.filesDir, "history.json"))

    private val lock = Any()

    fun load(): List<HistoryEntry> = synchronized(lock) { read() }

    fun add(entry: HistoryEntry) {
        synchronized(lock) {
            write((listOf(entry) + read()).take(HISTORY_LIMIT))
        }
    }

    /** Removes every stored entry equal to one of [entries], or sharing a non-blank [HistoryEntry.id]. */
    fun remove(entries: Collection<HistoryEntry>): Int {
        if (entries.isEmpty()) return 0
        synchronized(lock) {
            val gone = entries.toSet()
            val ids = entries.map { it.id }.filter { it.isNotBlank() }.toSet()
            val current = read()
            val next = current.filterNot { it in gone || (it.id.isNotBlank() && it.id in ids) }
            if (next.size != current.size) write(next)
            return current.size - next.size
        }
    }

    /** Replaces the row with this [id], keeping its place in the list. */
    fun update(id: String, transform: (HistoryEntry) -> HistoryEntry): HistoryEntry? {
        if (id.isBlank()) return null
        synchronized(lock) {
            val current = read()
            val index = current.indexOfFirst { it.id == id }
            if (index < 0) return null
            val updated = transform(current[index]).copy(id = id)
            val next = current.toMutableList()
            next[index] = updated
            write(next)
            return updated
        }
    }

    /**
     * Undo for [remove]: puts [removed] back where they were in [snapshot] (the history as it
     * was right before the delete). Entries added since then stay on top.
     */
    fun restore(snapshot: List<HistoryEntry>, removed: Collection<HistoryEntry>) {
        if (removed.isEmpty()) return
        synchronized(lock) {
            write(restoredOrder(read(), snapshot, removed))
        }
    }

    private fun write(next: List<HistoryEntry>) {
        val array = JSONArray()
        next.forEach { item ->
            array.put(
                JSONObject()
                    .put("awemeId", item.awemeId)
                    .put("title", item.title)
                    .put("author", item.author)
                    .put("savedAt", item.savedAt)
                    .put("success", item.success)
                    .put("message", item.message)
                    .put("contentUri", item.contentUri ?: "")
                    .put("platform", item.platform)
                    .put("sourceUrl", item.sourceUrl)
                    .put("id", item.id),
            )
        }
        file.writeText(array.toString(2))
    }

    private fun read(): List<HistoryEntry> {
        if (!file.exists()) return emptyList()
        return runCatching {
            val array = JSONArray(file.readText())
            buildList {
                for (index in 0 until array.length()) {
                    val item = array.getJSONObject(index)
                    add(
                        HistoryEntry(
                            awemeId = item.optString("awemeId"),
                            title = item.optString("title"),
                            author = item.optString("author"),
                            savedAt = item.optLong("savedAt"),
                            success = item.optBoolean("success"),
                            message = item.optString("message"),
                            contentUri = item.optString("contentUri").takeIf { it.isNotBlank() },
                            platform = item.optString("platform").ifBlank { "抖音" },
                            sourceUrl = item.optString("sourceUrl"),
                            id = item.optString("id"),
                        ),
                    )
                }
            }
        }.getOrDefault(emptyList())
    }
}

/** Order after undoing a delete; see [HistoryStore.restore]. Capped like [HistoryStore.add]. */
internal fun restoredOrder(
    current: List<HistoryEntry>,
    snapshot: List<HistoryEntry>,
    removed: Collection<HistoryEntry>,
): List<HistoryEntry> {
    val present = current.toSet()
    val returning = removed.toSet()
    val before = snapshot.toSet()
    val added = current.filter { it !in before }
    val restored = snapshot.filter { it in present || it in returning }
    return (added + restored).distinct().take(HISTORY_LIMIT)
}

internal const val HISTORY_LIMIT = 30
