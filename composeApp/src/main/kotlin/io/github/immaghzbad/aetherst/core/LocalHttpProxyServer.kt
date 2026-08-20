package io.github.immaghzbad.aetherst.core

import io.github.immaghzbad.aetherst.data.LogRepository
import io.github.immaghzbad.aetherst.model.RoutingMode
import java.io.InputStream
import java.io.OutputStream
import java.net.InetAddress
import java.net.InetSocketAddress
import java.net.ServerSocket
import java.net.Socket
import java.util.Locale
import java.util.concurrent.Executors
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicLong

class LocalHttpProxyServer(
    private val listenHost: String = "127.0.0.1",
    private val listenPort: Int = 10809,
    private val targetHost: String = "127.0.0.1",
    private val targetPort: Int = 1819,
    private val routingEngine: RoutingEngine
) {
    data class Stats(val txBytes: Long = 0, val rxBytes: Long = 0)

    private val isRunning = AtomicBoolean(false)
    private var serverSocket: ServerSocket? = null
    private val executor = Executors.newCachedThreadPool()
    private var mainThread: Thread? = null
    private val txBytes = AtomicLong(0)
    private val rxBytes = AtomicLong(0)

    fun getStats(): Stats = Stats(txBytes.get(), rxBytes.get())

    fun start() {
        if (isRunning.getAndSet(true)) return
        mainThread = Thread {
            try {
                serverSocket = ServerSocket(listenPort, 50, InetAddress.getByName(listenHost))
                while (isRunning.get()) {
                    val client = serverSocket?.accept() ?: break
                    executor.execute { handleRelay(client) }
                }
            } catch (_: Exception) {
            } finally {
                stop()
            }
        }
        mainThread?.start()
    }

    private fun handleRelay(clientSocket: Socket) {
        var targetSocket: Socket? = null
        try {
            clientSocket.tcpNoDelay = true
            clientSocket.soTimeout = 30000
            val clientIn = clientSocket.getInputStream()
            val clientOut = clientSocket.getOutputStream()

            val requestBytes = readHttpRequestHeader(clientIn)
            if (requestBytes.isEmpty()) return

            val requestText = String(requestBytes, Charsets.ISO_8859_1)
            val requestLine = requestText.substringBefore("\r\n")
            val parts = requestLine.split(" ")
            if (parts.size < 3) return
            val method = parts[0].uppercase(Locale.ROOT)
            val target = parts[1]
            LogRepository.i("[HttpProxy] ${clientSocket.inetAddress.hostAddress}:${clientSocket.port} -> $requestLine")

            var isConnect = false
            var targetDomain: String? = null
            var remotePort: Int
            var targetIp: String? = null

            if (method == "CONNECT") {
                isConnect = true
                val authority = target.substringBefore("/")
                val hostPort = authority.split(":")
                targetDomain = hostPort[0]
                remotePort = hostPort.getOrNull(1)?.toIntOrNull() ?: 443
            } else {
                val url = java.net.URI(target)
                targetDomain = url.host
                remotePort = if (url.port != -1) url.port else if (url.scheme == "https") 443 else 80
            }

            val cachedDomain = DnsMap.get(targetDomain ?: "")
            var decision = routingEngine.resolve(
                targetDomain ?: "",
                remotePort,
                cachedDomain ?: targetDomain,
                if (isConnect) targetDomain else null,
                if (!isConnect) targetDomain else null
            )

            if (decision.mode == RoutingMode.BLOCK) {
                clientOut.write("HTTP/1.1 403 Forbidden\r\nContent-Length: 0\r\nConnection: close\r\n\r\n".toByteArray())
                clientOut.flush()
                clientSocket.close()
                return
            }

            if (decision.mode == RoutingMode.DIRECT) {
                try {
                    val directSocket = Socket()
                    directSocket.tcpNoDelay = true
                    directSocket.connect(InetSocketAddress(targetDomain, remotePort), 5000)

                    if (isConnect) {
                        clientOut.write("HTTP/1.1 200 Connection established\r\n\r\n".toByteArray())
                        clientOut.flush()
                    } else {
                        directSocket.getOutputStream().write(requestBytes)
                        directSocket.getOutputStream().flush()
                    }

                    val dIn = directSocket.getInputStream()
                    val dOut = directSocket.getOutputStream()
                    val t1 = Thread { pipe(dIn, clientOut, rxBytes, "direct->client") }
                    val t2 = Thread { pipe(clientIn, dOut, txBytes, "client->direct") }
                    t1.start()
                    t2.start()
                    t1.join(300000)
                    t2.join(300000)
                    runCatching { directSocket.close() }
                } catch (_: Exception) {}
                return
            }

            targetSocket = Socket()
            targetSocket.tcpNoDelay = true
            targetSocket.soTimeout = 30000
            targetSocket.connect(InetSocketAddress(targetHost, this.targetPort), 5000)

            val targetIn = targetSocket.getInputStream()
            val targetOut = targetSocket.getOutputStream()

            targetOut.write(byteArrayOf(0x05, 0x01, 0x00))
            targetOut.flush()
            val authResponse = ByteArray(2)
            readExact(targetIn, authResponse)

            val db = targetDomain?.toByteArray()
            val connectRequest: ByteArray
            if (db != null) {
                connectRequest = ByteArray(7 + db.size)
                connectRequest[0] = 0x05
                connectRequest[1] = 0x01
                connectRequest[2] = 0x00
                connectRequest[3] = 0x03
                connectRequest[4] = db.size.toByte()
                System.arraycopy(db, 0, connectRequest, 5, db.size)
                connectRequest[5 + db.size] = (remotePort shr 8).toByte()
                connectRequest[6 + db.size] = (remotePort and 0xFF).toByte()
            } else {
                val ipBytes = InetAddress.getByName(targetIp ?: targetDomain).address
                connectRequest = ByteArray(6 + ipBytes.size)
                connectRequest[0] = 0x05
                connectRequest[1] = 0x01
                connectRequest[2] = 0x00
                connectRequest[3] = if (ipBytes.size == 4) 0x01.toByte() else 0x04.toByte()
                System.arraycopy(ipBytes, 0, connectRequest, 4, ipBytes.size)
                connectRequest[4 + ipBytes.size] = (remotePort shr 8).toByte()
                connectRequest[5 + ipBytes.size] = (remotePort and 0xFF).toByte()
            }
            targetOut.write(connectRequest)
            targetOut.flush()

            val targetReplyHeader = ByteArray(4)
            if (readExact(targetIn, targetReplyHeader) < 4) return
            val bndAddr: ByteArray = when (targetReplyHeader[3]) {
                0x01.toByte() -> {
                    val b = ByteArray(6)
                    readExact(targetIn, b)
                    b
                }
                0x04.toByte() -> {
                    val b = ByteArray(18)
                    readExact(targetIn, b)
                    b
                }
                0x03.toByte() -> {
                    val len = targetIn.read()
                    val b = ByteArray(len + 2)
                    readExact(targetIn, b)
                    val full = ByteArray(1 + b.size)
                    full[0] = len.toByte()
                    System.arraycopy(b, 0, full, 1, b.size)
                    full
                }
                else -> ByteArray(0)
            }

            if (targetReplyHeader[1] != 0x00.toByte()) {
                LogRepository.w("[HttpProxy] SOCKS connect to $targetDomain:$targetPort failed: ${targetReplyHeader[1].toInt()}")
                clientOut.write("HTTP/1.1 502 Bad Gateway\r\nContent-Length: 0\r\nConnection: close\r\n\r\n".toByteArray())
                clientOut.flush()
                return
            }

            LogRepository.i("[HttpProxy] Tunnel established: $targetDomain:$remotePort via $targetHost:${this.targetPort}")
            LogRepository.i("[HttpProxy] Writing 200 to ${clientSocket.inetAddress.hostAddress}:${clientSocket.port}")

            if (isConnect) {
                clientOut.write("HTTP/1.1 200 Connection established\r\n\r\n".toByteArray())
                clientOut.flush()
                LogRepository.i("[HttpProxy] 200 flushed; starting pipes")
                val t1 = Thread { pipe(targetIn, clientOut, rxBytes, "tunnel->client") }
                val t2 = Thread { pipe(clientIn, targetOut, txBytes, "client->tunnel") }
                t1.start()
                t2.start()
                t1.join(300000)
                t2.join(300000)
            } else {
                clientOut.write("HTTP/1.1 200 Connection established\r\n\r\n".toByteArray())
                clientOut.flush()
                val relayToTarget = Thread {
                    try {
                        targetOut.write(requestBytes)
                        targetOut.flush()
                        txBytes.addAndGet(requestBytes.size.toLong())
                        pipe(clientIn, targetOut, txBytes, "client->tunnel")
                    } catch (_: Exception) {}
                }
                val relayToClient = Thread { pipe(targetIn, clientOut, rxBytes, "tunnel->client") }
                relayToTarget.start()
                relayToClient.start()
                relayToTarget.join(300000)
                relayToClient.join(300000)
            }
        } catch (e: Exception) {
            LogRepository.e("[HttpProxy] Relay error: ${e.localizedMessage}")
        } finally {
            runCatching { targetSocket?.close() }
            runCatching { clientSocket.close() }
        }
    }

    private fun readHttpRequestHeader(ins: InputStream): ByteArray {
        val buffer = java.io.ByteArrayOutputStream()
        var prev = -1
        var crlfCount = 0
        val maxBytes = 8192
        var total = 0
        while (total < maxBytes) {
            val b = ins.read()
            if (b < 0) break
            buffer.write(b)
            total++
            if (prev == 13 && b == 10) {
                crlfCount++
                if (crlfCount == 2) break
            } else {
                crlfCount = 0
            }
            prev = b
        }
        return buffer.toByteArray()
    }

    private fun readExact(ins: InputStream, b: ByteArray): Int {
        var o = 0
        while (o < b.size) {
            val c = ins.read(b, o, b.size - o)
            if (c < 0) return o
            o += c
        }
        return o
    }

    private fun pipe(ins: InputStream, out: OutputStream, counter: AtomicLong, name: String) {
        try {
            val buffer = ByteArray(32768)
            var total = 0L
            while (isRunning.get()) {
                val n = ins.read(buffer)
                if (n <= 0) break
                out.write(buffer, 0, n)
                out.flush()
                counter.addAndGet(n.toLong())
                total += n
            }
            LogRepository.d("[HttpProxy] pipe $name done ($total bytes)")
        } catch (e: Exception) {
            LogRepository.w("[HttpProxy] pipe $name error: ${e.localizedMessage}")
        }
    }

    fun stop() {
        if (!isRunning.getAndSet(false)) return
        runCatching { serverSocket?.close() }
        serverSocket = null
        mainThread?.interrupt()
        mainThread = null
    }
}