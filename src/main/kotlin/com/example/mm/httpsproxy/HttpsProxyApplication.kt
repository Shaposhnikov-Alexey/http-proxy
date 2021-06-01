package com.example.mm.httpsproxy

import org.springframework.boot.autoconfigure.SpringBootApplication
import org.springframework.boot.context.properties.ConfigurationPropertiesScan
import org.springframework.boot.context.properties.EnableConfigurationProperties
import org.springframework.boot.runApplication

@SpringBootApplication
@ConfigurationPropertiesScan("com.example.mm")
class HttpsProxyApplication

fun main(args: Array<String>) {
    runApplication<HttpsProxyApplication>(*args)
}
