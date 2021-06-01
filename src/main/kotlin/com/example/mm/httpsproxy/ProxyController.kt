package com.example.mm.httpsproxy

import com.example.mm.httpsproxy.config.Properties
import org.springframework.http.*
import org.springframework.stereotype.Controller
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.client.HttpStatusCodeException
import org.springframework.web.client.RestTemplate
import org.springframework.web.util.UriComponentsBuilder
import java.net.URI
import java.time.LocalTime
import javax.servlet.http.HttpServletRequest


@Controller
class ProxyController(private val properties: Properties) {

    private val server = properties.server
    private val port = properties.port
    private val blackList = properties.blackList
    private val blackRegexes = properties.blackRegexes
    private val redirectBlockedUri = properties.redirectBlockedUri

    @RequestMapping("/**")
    fun handleRequest(
        @RequestBody(required = false) body: String?,
        method: HttpMethod,
        request: HttpServletRequest
    ): ResponseEntity<String> {
        val requestServer = request.serverName
        val requestPort = request.serverPort
        println("Got request from ${request.remoteHost}:${request.remotePort} to ${requestServer}:${requestPort}")

        // if server's name is censored then client is redirected to static page
        if (blackList.any { requestServer.contains(it) } || blackRegexes.any { requestServer.matches(Regex(it)) }) {
            val restTemplate = RestTemplate()
            return restTemplate.exchange(redirectBlockedUri, HttpMethod.GET, HttpEntity(null, null), String::class.java)
        }
        return mirrorHTTPRequest(body, method, request)
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
}