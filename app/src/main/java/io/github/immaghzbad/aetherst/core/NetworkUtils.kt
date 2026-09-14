package io.github.immaghzbad.aetherst.core

import java.net.Inet4Address
import java.net.NetworkInterface
import java.util.Collections

object NetworkUtils {
    fun getLocalIpAddress(): String? {
        try {
            val interfaces = Collections.list(NetworkInterface.getNetworkInterfaces())
            val candidates = mutableListOf<Pair<Int, String>>()

            for (iface in interfaces) {
                if (!iface.isUp || iface.isLoopback || iface.isPointToPoint) continue
                
                val name = iface.name.lowercase()
                
                if (name.contains("rmnet") || name.contains("pbp") || name.contains("radio") || 
                    name.contains("p2p") || name == "wlan0" || name.startsWith("ncm")) continue

                val addresses = Collections.list(iface.inetAddresses)
                for (addr in addresses) {
                    if (addr is Inet4Address) {
                        val ip = addr.hostAddress ?: continue
                        if (ip == "198.18.0.1" || ip.startsWith("127.")) continue

                        val priority = when {
                            name.startsWith("ap") || name.contains("softap") -> 1
                            name.startsWith("wlan") -> 2
                            name.startsWith("rndis") || (name.contains("usb") && !name.startsWith("ncm")) -> 3
                            else -> null
                        }
                        
                        if (priority != null) {
                            candidates.add(priority to ip)
                        }
                    }
                }
            }

            return candidates.minByOrNull { it.first }?.second
        } catch (e: Exception) {
            e.printStackTrace()
        }
        return null
    }
}
