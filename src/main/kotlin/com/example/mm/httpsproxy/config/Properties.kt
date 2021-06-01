package com.example.mm.httpsproxy.config

import org.springframework.boot.context.properties.ConfigurationProperties

@ConfigurationProperties("proxy")
class Properties {
    val server = "localhost"
    val port = 8080
    val blackList = listOf("lh3.googleusercontent")
    val blackRegexes = listOf("\\w*t\\.com\\w*")
    val redirectBlockedUri = "https://support.google.com/webmasters/answer/6347750"
}