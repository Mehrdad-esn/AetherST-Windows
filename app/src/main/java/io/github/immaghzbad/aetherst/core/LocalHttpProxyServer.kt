package io.github.immaghzbad.aetherst.core

import android.net.VpnService
import io.github.immaghzbad.aetherst.data.LogRepository
import java.io.InputStream
import java.io.OutputStream
import java.net.InetAddress
import java.net.InetSocketAddress
import java.net.Proxy
import java.net.ServerSocket
import java.net.Socket
import java.util.concurrent.Executors
import java.util.concurrent.atomic.AtomicBoolean

class LocalHttpProxyServer(
    private val vpnService: VpnService?,
    private val listenHost: String = "127.0.0.1",
    private val listenPort: Int = 1820,
    private val socksHost: String = "127.0.0.1",
    private val socksPort: Int = 1819
) {
    private val isRunning = AtomicBoolean(false)
    private var serverSocket: ServerSocket? = null
    private val executor = Executors.newCachedThreadPool()
    private var mainThread: Thread? = null

    fun start() {
        if (isRunning.getAndSet(true)) return
        mainThread = Thread {
            try {
                serverSocket = ServerSocket(listenPort, 100, InetAddress.getByName(listenHost))
                LogRepository.i("[HTTP Proxy] Listening on $listenHost:$listenPort, forwarding to SOCKS5 $socksHost:$socksPort")
                while (isRunning.get()) {
                    val client = serverSocket?.accept() ?: break
                    executor.execute { handleClient(client) }
                }
            } catch (e: Exception) {
                if (isRunning.get()) LogRepository.e("[HTTP Proxy] Server error: ${e.localizedMessage}")
            } finally {
                stop()
            }
        }
        mainThread?.start()
    }

    private fun handleClient(clientSocket: Socket) {
        try {
            clientSocket.soTimeout = 30000
            val input = clientSocket.getInputStream()
            val header = readHeader(input) ?: return
            
            val firstLine = header.lineSequence().firstOrNull() ?: return
            val parts = firstLine.split(" ")
            if (parts.size < 2) return
            
            val method = parts[0]
            val url = parts[1]
            
            if (method.uppercase() == "CONNECT") {
                handleConnect(clientSocket, url)
            } else {
                handlePlainHttp(clientSocket, method, url, header)
            }
        } catch (e: Exception) {
            LogRepository.e("[HTTP Proxy] Client handler failed: ${e.localizedMessage}")
            runCatching { clientSocket.close() }
        }
    }

    private fun handleConnect(clientSocket: Socket, target: String) {
        var targetSocket: Socket? = null
        try {
            val parts = target.split(":")
            val host = parts[0]
            val port = parts.getOrNull(1)?.toIntOrNull() ?: 443
            
            val socksProxy = Proxy(Proxy.Type.SOCKS, InetSocketAddress(socksHost, socksPort))
            targetSocket = Socket(socksProxy)
            vpnService?.protect(targetSocket)
            targetSocket.connect(InetSocketAddress(host, port), 10000)
            
            val clientOut = clientSocket.getOutputStream()
            clientOut.write("HTTP/1.1 200 Connection Established\r\n\r\n".toByteArray())
            clientOut.flush()
            
            val t1 = Thread { pipe(clientSocket.getInputStream(), targetSocket.getOutputStream()) }
            val t2 = Thread { pipe(targetSocket.getInputStream(), clientSocket.getOutputStream()) }
            t1.start()
            t2.start()
            t1.join(300000)
            t2.join(300000)
        } catch (e: Exception) {
            LogRepository.e("[HTTP Proxy] CONNECT failed: ${e.localizedMessage}")
            runCatching { clientSocket.getOutputStream().write("HTTP/1.1 502 Bad Gateway\r\n\r\n".toByteArray()) }
        } finally {
            runCatching { targetSocket?.close() }
            runCatching { clientSocket.close() }
        }
    }

    private fun handlePlainHttp(clientSocket: Socket, method: String, url: String, fullHeader: String) {
        var targetSocket: Socket? = null
        try {
            var cleanUrl = url
            if (cleanUrl.startsWith("http://", ignoreCase = true)) {
                cleanUrl = cleanUrl.substring(7)
            }
            val slashIdx = cleanUrl.indexOf('/')
            val hostPart = if (slashIdx != -1) cleanUrl.substring(0, slashIdx) else cleanUrl
            val pathPart = if (slashIdx != -1) cleanUrl.substring(slashIdx) else "/"
            
            val hostParts = hostPart.split(":")
            val host = hostParts[0]
            val port = hostParts.getOrNull(1)?.toIntOrNull() ?: 80
            
            val socksProxy = Proxy(Proxy.Type.SOCKS, InetSocketAddress(socksHost, socksPort))
            targetSocket = Socket(socksProxy)
            vpnService?.protect(targetSocket)
            targetSocket.connect(InetSocketAddress(host, port), 10000)
            
            val targetOut = targetSocket.getOutputStream()
            val newRequest = "$method $pathPart HTTP/1.1\r\n" + 
                fullHeader.lineSequence().drop(1).joinToString("\r\n")
            
            targetOut.write(newRequest.toByteArray())
            targetOut.flush()
            
            val t1 = Thread { pipe(clientSocket.getInputStream(), targetSocket.getOutputStream()) }
            val t2 = Thread { pipe(targetSocket.getInputStream(), clientSocket.getOutputStream()) }
            t1.start()
            t2.start()
            t1.join(300000)
            t2.join(300000)
        } catch (e: Exception) {
            LogRepository.e("[HTTP Proxy] Request relay failed: ${e.localizedMessage}")
            runCatching { clientSocket.getOutputStream().write("HTTP/1.1 502 Bad Gateway\r\n\r\n".toByteArray()) }
        } finally {
            runCatching { targetSocket?.close() }
            runCatching { clientSocket.close() }
        }
    }

    private fun readHeader(input: InputStream): String? {
        val buffer = StringBuilder()
        val data = ByteArray(1)
        while (isRunning.get()) {
            val n = input.read(data)
            if (n <= 0) break
            buffer.append(data[0].toInt().toChar())
            if (buffer.endsWith("\r\n\r\n")) return buffer.toString()
            if (buffer.length > 8192) return null
        }
        return null
    }

    private fun pipe(ins: InputStream, out: OutputStream) {
        try {
            val buffer = ByteArray(32768)
            while (isRunning.get()) {
                val n = ins.read(buffer)
                if (n <= 0) break
                out.write(buffer, 0, n)
                out.flush()
            }
        } catch (_: Exception) {}
    }

    fun stop() {
        if (!isRunning.getAndSet(false)) return
        runCatching { serverSocket?.close() }
        serverSocket = null
        mainThread?.interrupt()
        mainThread = null
    }
}
