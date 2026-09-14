package io.github.immaghzbad.aetherst.core

object HevTun2SocksConfig {
    fun generate(
        socksAddress: String,
        socksPort: Int,
        mtu: Int,
        ipv4Address: String = "198.18.0.1",
        ipv6Address: String = "fd00::1",
        udp: Boolean = true
    ): String {
        val sb = StringBuilder()
        sb.append("tunnel:\n")
        sb.append("  mtu: $mtu\n")
        sb.append("  ipv4: $ipv4Address\n")
        sb.append("  ipv6: '$ipv6Address'\n")

        sb.append("\nsocks5:\n")
        sb.append("  address: $socksAddress\n")
        sb.append("  port: $socksPort\n")
        if (udp) {
            sb.append("  udp: udp\n")
        }

        sb.append("\nmisc:\n")
        sb.append("  log-level: warn\n")
        sb.append("  connect-timeout: 5000\n")
        sb.append("  read-write-timeout: 60000\n")

        return sb.toString()
    }
}
