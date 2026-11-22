package app.douyin.save

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
)

internal class HistoryStore(private val file: File) {
    constructor(context: Context) : this(File(context.filesDir, "history.json"))

    private val lock = Any()

    fun load(): List<HistoryEntry> = synchronized(lock) { read() }

    fun add(entry: HistoryEntry) {
        synchronized(lock) {
            write((listOf(entry) + read()).take(30))
        }
    }

    /** Removes every stored entry equal to one of [entries]. Returns how many were removed. */
    fun remove(entries: Collection<HistoryEntry>): Int {
        if (entries.isEmpty()) return 0
        synchronized(lock) {
            val gone = entries.toSet()
            val current = read()
            val next = current.filterNot { it in gone }
            if (next.size != current.size) write(next)
            return current.size - next.size
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
                    .put("contentUri", item.contentUri ?: ""),
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
                        ),
                    )
                }
            }
        }.getOrDefault(emptyList())
    }
}
