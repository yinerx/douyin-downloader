package app.douyin.save.parser

object DownloadNames {
    fun fileName(title: String, awemeId: String): String {
        val clean = title
            .replace(Regex("""[\\/:*?"<>|\u0000-\u001F]"""), " ")
            .replace(Regex("""\s+"""), " ")
            .trim()
            .take(40)
            .trim()
        val base = if (clean.isEmpty()) awemeId else "${clean}_$awemeId"
        return "$base.mp4"
    }
}
