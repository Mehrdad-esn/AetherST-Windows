<p align="center">
  <img src="https://img.shields.io/badge/AetherST-Tunnel-007AFF?style=for-the-badge&logo=shield&logoColor=white" alt="AetherST Logo" width="200">
</p>

<h1 align="center">AetherST Tunnel</h1>

<p align="center">
  <strong>Advanced, High-Performance Censorship Circumvention Client for Android</strong>
</p>

<p align="center">
  <a href="https://github.com/immaghzbad/AetherST/releases">
    <img src="https://img.shields.io/github/v/release/immaghzbad/AetherST?style=for-the-badge&color=007AFF" alt="Release">
  </a>
  <a href="LICENSE">
    <img src="https://img.shields.io/badge/License-Proprietary-orange?style=for-the-badge" alt="License">
  </a>
  <a href="https://github.com/immaghzbad/AetherST/stargazers">
    <img src="https://img.shields.io/github/stars/immaghzbad/AetherST?style=for-the-badge&color=FFD700" alt="Stars">
  </a>
  <img src="https://img.shields.io/badge/Platform-Android-3DDC84?style=for-the-badge&logo=android&logoColor=white" alt="Platform">
</p>

---

## 📖 Overview

**AetherST Tunnel** is a production-grade VPN client for Android, meticulously engineered to provide secure and stable connectivity in highly restricted network environments. By combining the power of the **Aether Core** with the proven **HEV SOCKS5** engine, AetherST offers a robust solution against Deep Packet Inspection (DPI) and protocol-based blocking.

## ✨ Features

- 🛡️ **Stealth Connectivity:** Specifically optimized to bypass protocol fingerprinting and DPI.
- 🚀 **Advanced Transports:** Comprehensive support for **MASQUE**, **WireGuard**, **Gool (WG-in-WG)**, and **Cloudflare Zero Trust**.
- 📡 **Intelligent Scanning:** Real-time gateway discovery with data-plane validation before connection.
- ⚡ **Native Performance:** Powered by a C-based native packet engine (HEV) for high throughput and low latency.
- 📱 **Modern UI:** Clean, iOS-inspired dashboard built with **Jetpack Compose** for a premium user experience.
- 🛠️ **Developer-Ready:** Built-in diagnostics, real-time logging, and flexible protocol presets.

## 🛠️ Supported Protocols

AetherST Tunnel leverages cutting-edge protocols to ensure connectivity even in the most hostile network environments:

### 🎭 MASQUE (HTTP/3 & HTTP/2)
The flagship protocol for stealth. By tunneling traffic over QUIC (H3) or TLS (H2), it makes VPN traffic look like standard web browsing, making it highly resilient to Deep Packet Inspection (DPI).

### 🛡️ WireGuard
A modern, high-performance VPN protocol that uses state-of-the-art cryptography. It is optimized for maximum speed and minimal battery drain on mobile devices.

### 🌀 Gool (Warp-in-Warp / WG-in-WG)
A specialized nested WireGuard configuration (Nested WireGuard). By wrapping one WireGuard tunnel inside another, it provides an additional layer of encryption and obfuscation, effectively bypassing many restrictive firewalls and improving stability.

### ☁️ Cloudflare Zero Trust (Teams)
Enterprise-grade security for individuals and organizations. It allows you to route your traffic through Cloudflare's global network using Gateway filtering and Service Tokens, ensuring zero-trust access control and protection against malware and phishing.

---

## 📸 Screenshots

<p align="center">
  <i>Coming Soon!</i>
</p>

## 🏗️ Technical Architecture

### [Aether Core (v1.5.0)](https://github.com/CluvexStudio/Aether)
The orchestration layer responsible for:
- Encrypted tunnel management.
- Dynamic gateway health checks.
- Multi-protocol handling (MASQUE, WG).

### [HEV SOCKS5 Tunnel (v2.15.0)](https://github.com/heiher/hev-socks5-tunnel)
The native bridge between Android's VpnService and Aether:
- Mature user-space TCP/IP stack.
- Zero-copy packet processing.
- Efficient UDP over SOCKS5 translation.

### SocksTunBridge
A high-performance Kotlin/Java based tunnel engine:
- Direct SOCKS5 to TUN translation.
- Per-packet sniffing for intelligent routing.
- Lightweight alternative for devices with restricted native library support.

## 🚀 Getting Started

### Installation
1. Go to the [Releases](https://github.com/immaghzbad/AetherST/releases) page.
2. Download the APK compatible with your device architecture (`arm64-v8a` is recommended for most modern phones).
3. Install and grant the necessary VPN and Notification permissions.

### Build from Source
If you wish to build the project locally:
- **Android Studio:** Ladybug (2024.2.1) or newer.
- **JDK:** 17
- **NDK:** 30.0.15729638

## ⚙️ CI/CD & Security

The project uses **GitHub Actions** for automated Multi-APK releases. To set up your own fork, configure these **Secrets**:

| Secret | Description |
| :--- | :--- |
| `KEYSTORE_BASE64` | Base64 string of your `.jks` file |
| `KEYSTORE_PASSWORD` | Password for the keystore |
| `KEY_ALIAS` | Key alias |
| `KEY_PASSWORD` | Key password |

> [!IMPORTANT]
> Ensure **Workflow permissions** are set to **Read and write permissions** in your repository settings under *Actions > General*.

## 💬 Community

Stay updated and get support through our official channels:

- 📢 **Telegram:** [PowerSigma](https://t.me/PowerSigma)
- 👨‍💻 **Developer:** [@immaghzbad](https://github.com/immaghzbad)

## ⚖️ License

Developed by **PowerSigma Team**.
- **Aether Core** is property of CluvexStudio.
- **HEV SOCKS5 Tunnel** is used under the MIT License.
- **AetherST Source** is available for educational use and contributions. Redistribution is permitted only with clear attribution and a link to this repository.

---
<p align="center">
  Built with 💙 by <b>PowerSigma Team</b>
</p>
