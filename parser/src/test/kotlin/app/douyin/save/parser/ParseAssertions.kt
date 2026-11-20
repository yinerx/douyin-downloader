package app.douyin.save.parser

internal fun expectParseError(block: () -> Unit): String {
    try {
        block()
    } catch (error: ParseException) {
        return error.message ?: ""
    }
    throw AssertionError("expected ParseException")
}
