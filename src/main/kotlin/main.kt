import io.ktor.network.selector.*
import io.ktor.network.sockets.*
import io.ktor.utils.io.*
import kotlinx.coroutines.*

const val host = "0.0.0.0"
const val port = 3000

fun main(args: Array<String>) {
    runBlocking {
        val tcpSocketBuilder = aSocket(ActorSelectorManager(Dispatchers.IO)).tcp()
        val proxyServer: ServerSocket?

        try {
            proxyServer = tcpSocketBuilder.bind(host, port) {
                reuseAddress = true
            }
        } catch (e: Exception) {
            println("Couldn't start proxy server on host [$host] and port [$port]\n\t${e.printStackTrace()}")
            return@runBlocking
        }
        println("Started proxy server at ${proxyServer.localAddress}")

        while (true) {
            val client = proxyServer.accept()
            launch {
                try {
                    val buffer = ByteArray(8192)
                    val clientReader = client.openReadChannel()
                    val clientWriter = client.openWriteChannel(true)

                    // actually Ktor's documentation is pretty shallow so I'm not sure whether it is supposed to first
                    // call awaitContent() and only then read data or not
                    // readAvailable() ensures that it is suspendable method that waits until some data is received
                    // whatever, it works without awaitContent() but let's just left it here
//                    clientReader.awaitContent()
                    var messageSize = clientReader.readAvailable(buffer)
                    val request = HttpRequest(buffer)
                    when {
                        request.method == "CONNECT" -> tunnelHttps(
                            request,
                            client,
                            clientReader,
                            clientWriter,
                            buffer,
                            tcpSocketBuilder
                        )
                        request.method == "FAIL" || buffer.isEmpty() -> {
                            println("Failed to connect to client or receive request from ${client.remoteAddress}")
                            // this blocks the coroutine's flow but whatever, it's the end of the coroutine now
                            client.close()
                            cancel()
                        }
                        else -> {
                            var server: Socket? = null
                            try {
                                server = tcpSocketBuilder.connect(request.host, request.port)
                            } catch (e: Exception) {
                                println("Failed to connect to ${request.host}:${request.port}")
                                client.close()
                                cancel()
                            }
                            println("Connected to ${request.host}:${request.port}")
                            val serverReader = server!!.openReadChannel()
                            val serverWriter = server.openWriteChannel()

                            // simple HTTP exchange between client and server
                            // after that the sockets close and proxy server awaits for new request
                            serverWriter.writeAvailable(buffer, 0, messageSize)
                            serverWriter.flush()

//                            serverReader.awaitContent()
                            messageSize = serverReader.readAvailable(buffer)

                            clientWriter.writeAvailable(buffer, 0, messageSize)
                            clientWriter.flush()

                            client.close()
                            server.close()
                        }
                    }
                } catch (e: Exception) {
                    println("Something went wrong during communicating with client\n\t${e.printStackTrace()}")
                    client.close()
                    cancel()
                }
            }
        }
    }
}

suspend fun tunnelHttps(
    request: HttpRequest,
    client: Socket,
    clientReader: ByteReadChannel,
    clientWriter: ByteWriteChannel,
    buffer: ByteArray,
    tcpSocketBuilder: TcpSocketBuilder
) {
    val server: Socket?
    try {
        server = tcpSocketBuilder.connect(request.host, request.port)
    } catch (e: Exception) {
        println("Failed to connect to ${request.host}:${request.port}\n\t${e.printStackTrace()}")
        client.close()
        return
    }
    println("Connected to ${request.host}:${request.port}")
    val serverReader = server.openReadChannel()
    val serverWriter = server.openWriteChannel()

    // tell the client that the connection is set and we are tunneling proxy
    val successConnectionString = "HTTP/1.1 200 OK\r\nProxyServer-agent: kotlin-https-proxy\r\n\r\n"
    clientWriter.writeAvailable(successConnectionString.toByteArray())
    clientWriter.flush()

    while (!client.isClosed && !clientReader.isClosedForRead && !clientWriter.isClosedForWrite
        && !server.isClosed && !serverReader.isClosedForRead && !serverWriter.isClosedForWrite
    ) {
        // without delays before checking for reading/writing availability websites simply don't load
        delay(20)
        while (clientReader.availableForRead > 0) {
            val messageSize = clientReader.readAvailable(buffer)
            serverWriter.writeFully(buffer, 0, messageSize)
        }
        serverWriter.flush()

        delay(20)
        while (serverReader.availableForRead > 0) {
            val messageSize = serverReader.readAvailable(buffer)
            clientWriter.writeFully(buffer, 0, messageSize)
        }
        clientWriter.flush()
    }
    server.close()
    client.close()
}
