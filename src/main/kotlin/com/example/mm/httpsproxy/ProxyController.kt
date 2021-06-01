package com.example.mm.httpsproxy

import com.example.mm.httpsproxy.config.Properties
import org.apache.tomcat.jni.Socket
import org.springframework.http.*
import org.springframework.stereotype.Controller
import org.springframework.util.SocketUtils
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.client.HttpStatusCodeException
import org.springframework.web.client.RestTemplate
import org.springframework.web.util.UriComponentsBuilder
import java.net.URI
import java.net.http.WebSocket
import java.time.LocalTime
import javax.servlet.http.HttpServletRequest
import javax.servlet.http.HttpServletResponse


@Controller
class ProxyController(private val properties: Properties) {

    private val server = properties.server
    private val port = properties.port
    private val blackList = properties.blackList
    private val blackRegexes = properties.blackRegexes
    private val redirectBlockedUri = properties.redirectBlockedUri

    // it saves ssl connections as map with corresponding connect-time that times out after 30 minutes
    // sadly it doesn't have auto-refresh for now
    private val clientsConnectionCacheMap: MutableMap<Pair<String, String>, LocalTime> = mutableMapOf()

    @RequestMapping("/**")
    fun handleRequest(
        @RequestBody(required = false) body: String?,
        method: HttpMethod,
        request: HttpServletRequest,
        response: HttpServletResponse
    ): ResponseEntity<String> {
        val requestServer = request.serverName
        val requestPort = request.serverPort
        println("Got request from ${request.remoteHost}:${request.remotePort} to ${requestServer}:${requestPort}")

        // if server's name is censored then client is redirected to static page
        if (blackList.any { requestServer.contains(it) } || blackRegexes.any { requestServer.matches(Regex(it)) }) {
            val restTemplate = RestTemplate()
            return restTemplate.exchange(redirectBlockedUri, HttpMethod.GET, HttpEntity(null, null), String::class.java)
        }

        return when {
            request.method == "CONNECT" -> initSSLConnection(body, method, request)
            clientsConnectionCacheMap.containsKey(Pair(request.remoteAddr, requestServer)) -> {
                val oldTime = clientsConnectionCacheMap[Pair(request.remoteAddr, requestServer)]
                val now = LocalTime.now()
                if (oldTime == null || ((now.hour - oldTime.hour) * 60 + (now.minute + 60 - oldTime.minute) % 60) >= 30) {
                    clientsConnectionCacheMap.remove(Pair(request.remoteAddr, requestServer))
                    return ResponseEntity.status(HttpStatus.REQUEST_TIMEOUT)
                        .body("SSL connection to server [$requestServer] has expired. Please try again and reconnect")
                }
                return tunnelHTTPSData()
            }
            else -> mirrorHTTPRequest(body, method, request)
        }
    }

    private fun mirrorHTTPRequest(
        body: String?,
        method: HttpMethod,
        request: HttpServletRequest
    ): ResponseEntity<String> {
        val requestUrl = request.requestURI
        val requestQuery = request.queryString
        val requestServer = request.serverName
        val requestPort = request.serverPort
        //next code simply mirrors server's response to client
        //it only applies to HTTP protocols and doesn't support netty exception handling
        val uriDefault = URI("http", null, requestServer, requestPort, null, null, null)
        val uri = UriComponentsBuilder.fromUri(uriDefault)
            .path(requestUrl)
            .query(requestQuery)
            .build(true).toUri()

        val headers = HttpHeaders()
        val headerNames = request.headerNames
        println(
            "Request metadata is:\n\turi -> $requestUrl\n\tquery -> $requestQuery\n\theaders' names -> ${
                headerNames.toList().joinToString()
            }"
        )
        while (headerNames.hasMoreElements()) {
            val headerName = headerNames.nextElement()
            headers.set(headerName, request.getHeader(headerName))
        }

        val httpEntity = HttpEntity(body, headers)
        val restTemplate = RestTemplate()
        return try {
            restTemplate.exchange(uri, method, httpEntity, String::class.java)
        } catch (e: HttpStatusCodeException) {
            println("Request failed with code [${e.rawStatusCode}] and message [${e.localizedMessage}]")
            ResponseEntity.status(e.rawStatusCode)
                .headers(e.responseHeaders)
                .body(e.responseBodyAsString)
        }
    }

    private fun initSSLConnection(
        body: String?,
        method: HttpMethod,
        request: HttpServletRequest
    ): ResponseEntity<String> {
        val requestServer = request.serverName
        val requestPort = request.serverPort
        val uriDefault = URI("http", null, requestServer, requestPort, null, null, null)
        val uri = UriComponentsBuilder.fromUri(uriDefault)
            .build(true).toUri()

        val httpEntity = HttpEntity(null, null)
        val restTemplate = RestTemplate()
        return try {
            val response = restTemplate.exchange(uri, HttpMethod.valueOf("CONNECT"), httpEntity, String::class.java)
            val reply = "HTTP/1.1 200 Connection established\\r\\ProxyServer-agent: HttpsProxyApplication"
            ResponseEntity.status(HttpStatus.OK)
                .body("Connection established\\r\\ProxyServer-agent: HttpsProxyApplication")
        } catch (e: HttpStatusCodeException) {
            println("Request failed with code [${e.rawStatusCode}] and message [${e.localizedMessage}]")
            ResponseEntity.status(e.rawStatusCode)
                .headers(e.responseHeaders)
                .body(e.responseBodyAsString)
        }
    }

    private fun tunnelHTTPSData(): ResponseEntity<String> {
        TODO()
    }
}