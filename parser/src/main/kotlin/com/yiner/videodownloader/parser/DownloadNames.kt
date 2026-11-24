package com.yiner.videodownloader.parser

object DownloadNames {
    fun fileName(title: String, awemeId: String): String {
        val base = named(sanitize(title), awemeId)
        return "$base.mp4"
    }

    /**
     * X 的文件名是「作者_帖子ID.mp4」。
     * 一条帖子里有多个视频时，第 1 个不加序号，从第 2 个起追加 `_2`、`_3`。
     * [index] 是这条帖子里的序号（从 1 开始），不是本次勾选的顺序。
     */
    fun xFileName(author: String, statusId: String, index: Int, videoCount: Int): String {
        val base = named(sanitize(author), statusId)
        val name = if (videoCount > 1 && index > 1) "${base}_$index" else base
        return "$name.mp4"
    }

    private fun named(clean: String, id: String): String = if (clean.isEmpty()) id else "${clean}_$id"

    private fun sanitize(value: String): String = value
        .replace(Regex("""[\\/:*?"<>|\u0000-\u001F]"""), " ")
        .replace(Regex("""\s+"""), " ")
        .trim()
        .take(40)
        .trim()
}
