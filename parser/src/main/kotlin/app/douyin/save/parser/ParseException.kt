package app.douyin.save.parser

/**
 * 解析失败时抛出。message 直接显示给用户。
 * [definitive] 为 false 时，调用方可以换一条途径再试（例如 X 的备用接口）。
 */
class ParseException(
    message: String,
    val definitive: Boolean = true,
) : Exception(message)
