interface Logger {
    fun log(tag: String, message: String)
}

class DefaultLogger : Logger {
    override fun log(tag: String, message: String) {
        println("$tag: $message")
    }
}