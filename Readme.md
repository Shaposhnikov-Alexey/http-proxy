## HTTP/HTTPS Proxy server with Kotlin and Ktor [branch main]

This is a simple proxy server that parses http requests and either lets the client and server exchange some data via HTTP 
or open TSL connection and tunnel all the data between them. 

To run this proxy simply start the main.kt (fun main) in your IDE or run the application with any other means.

Proxy server starts with the IP address of its host machine and port with number "3000". 

For this proxy server were used Ktor's Sockets and Kotlin's coroutines.

## HTTP Proxy with Spring Boot and Kotlin [branch spring]

I've tried to implement HTTP/HTTPS proxy server using spring's "/**" endpoint and eventually found out that HTTPS is meant to be run on Socket connection.

Though, I don't wanna delete all the work completely so for now it should stay a separate branch...