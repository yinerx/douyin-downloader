package app.douyin.save.parser

/** 解析失败时抛出。message 直接显示给用户。 */
class ParseException(message: String) : Exception(message)
