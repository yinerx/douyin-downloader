package app.douyin.save.parser

enum class Platform {
    DOUYIN,
    X,
}

data class SharedLink(val platform: Platform, val url: String)
