class HttpRequest(buffer: ByteArray) {
    var method: String
    var host: String
    var port: Int

    init {
        try {
            val string = buffer.toString(Charsets.UTF_8)
            val lines = string.replace("\r", "").split("\n")
            val meta = lines[0].split(" ")
            method = meta[0]
            val hostLine = lines.find { it.lowercase().contains("host:") } ?: ""
            val hostWord = hostLine.split(" ")[1].split(":")
            when {
                hostWord.size == 2 -> {
                host = hostWord[0]
                port = hostWord[1].toInt()
                }
                else -> {
                    host = hostWord[0]
                    port = 80
                }
            }
        } catch(e: Exception) {
            println("Exception at parsing with\n\r${e.printStackTrace()}")
            method = "FAIL"
            host = ""
            port = -1
        }
    }
}