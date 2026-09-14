<p align="center">
  <img src="https://img.shields.io/badge/AetherST-Tunnel-007AFF?style=for-the-badge&logo=shield&logoColor=white" alt="AetherST Logo" width="200">
</p>

<h1 align="center">AetherST Tunnel</h1>

<p align="center">
  <strong>Advanced, High-Performance Censorship Circumvention Client for Android & Windows</strong>
</p>

<p align="center">
  <a href="https://github.com/Mehrdad-esn/AetherST-Windows/releases">
    <img src="https://img.shields.io/github/v/release/Mehrdad-esn/AetherST-Windows?style=for-the-badge&color=007AFF" alt="Release">
  </a>
  <a href="LICENSE">
    <img src="https://img.shields.io/badge/License-Proprietary-orange?style=for-the-badge" alt="License">
  </a>
  <a href="https://github.com/Mehrdad-esn/AetherST-Windows/stargazers">
    <img src="https://img.shields.io/github/stars/Mehrdad-esn/AetherST-Windows?style=for-the-badge&color=FFD700" alt="Stars">
  </a>
  <img src="https://img.shields.io/badge/Platform-Android-3DDC84?style=for-the-badge&logo=android&logoColor=white" alt="Platform Android">
  <img src="https://img.shields.io/badge/Platform-Windows-0078D4?style=for-the-badge&logo=windows&logoColor=white" alt="Platform Windows">
</p>

---

## 📖 Overview

**AetherST Tunnel** is a production-grade VPN and Proxy client for Android and Windows, meticulously engineered to provide secure and stable connectivity in highly restricted network environments. By combining the power of the **Aether Core** with proven tunnel engines, AetherST offers a robust solution against Deep Packet Inspection (DPI) and protocol-based blocking across multiple platforms.

## 📱 Versions & Platforms

- **Windows Client (this port):** `v1.7` — sections below marked *(Windows)* describe exactly what this build contains (every version number verified against the shipped binaries). Installs in place over older versions (same MSI product identity); older installs are offered updates automatically via the in-app update prompt.
- **Android Client (upstream):** `v1.7.0` — sections marked *(Android)* describe the upstream mobile client for reference; they are **not** all present on Windows (see the Windows notes).

## ✨ Features (Windows)

- 🛡️ **Stealth Connectivity:** Specifically optimized to bypass protocol fingerprinting and DPI.
- 🚀 **Advanced Transports:** **MASQUE**, **WireGuard**, **Gool (WG-in-WG)**. (Cloudflare Zero Trust was removed from this port by design.)
- 🔗 **Psiphon Chain (Windows):** Optional second layer via a bundled GPL helper built from psiphon-tunnel-core `v2.0.41` sources (same version as Android), chained over MASQUE/WireGuard/Gool with Auto, Fallback, Always, and Psiphon-only modes plus selectable egress region.
- 🔀 **OpenVPN Hybrid (Windows-only):** Chain any `.ovpn` config over the app proxy tunnel (file picker included).
- 📡 **Intelligent Scanning:** Real-time gateway discovery with data-plane validation before connection.
- ⚡ **Native Performance:** Aether engine `v1.8.0` (verified with `aether -v`) + HEV `v2.17.1` (verified).
- 🖥️ **Desktop UI:** iOS-inspired dashboard (Compose Multiplatform) with Fa/En localization, DNS benchmark optimizer, tray, single-instance guard, native MSI.
- 🛠️ **Developer-Ready:** Built-in diagnostics, real-time logging, and flexible protocol presets.
- ⛔ **Not on Windows:** Tor chain (the shipped `aether.exe` has no Tor flags — verified with `aether --help`), MASQUE-in-MASQUE (no `--mim` flag in the shipped engine), Cloudflare Zero Trust (removed).

## 🛠️ Supported Protocols

AetherST Tunnel leverages cutting-edge protocols to ensure connectivity even in the most hostile network environments:

### 🎭 MASQUE (HTTP/3 & HTTP/2)
The flagship protocol for stealth. By tunneling traffic over QUIC (H3) or TLS (H2), it makes VPN traffic look like standard web browsing, making it highly resilient to Deep Packet Inspection (DPI).

### 🛡️ WireGuard
A modern, high-performance VPN protocol that uses state-of-the-art cryptography. It is optimized for maximum speed and minimal battery drain on mobile devices.

### 🌀 Gool (Warp-in-Warp / WG-in-WG)
A specialized nested WireGuard configuration. By wrapping one WireGuard tunnel inside another, it provides an additional layer of encryption and obfuscation, effectively bypassing many restrictive firewalls and improving stability.

### 🔗 Psiphon Chain (Windows)
An optional second layer powered by `psiphon-helper.exe`, built from the open-source psiphon-tunnel-core `v2.0.41` sources shipped in `psiphon-helper/` (GPL-3.0, same version as the Android app). Two directions, like Android: core-first (apps → helper on `127.0.0.1:3080` SOCKS → Aether core → internet) or psiphon-first (helper dials direct, core chains through it via the engine's `--upstream` flag). Modes: Auto, Fallback (= core first, psiphon-first fallback), Always, and Psiphon-only (no Aether core). Selectable egress region (auto-discovered list is cached), local HTTP proxy on `socks+1`. (No MASQUE race on desktop: the order picker decides the direction deterministically.)

### 🧅 Tor Chain (Android only — NOT in this Windows build)
Upstream Android has a Tor second layer (Tor inside tunnel / tunnel through Tor / Tor only, BridgeDB bridges). The Windows engine (`aether.exe`, verified `1.8.0`) exposes no `--tor*` flags, so Tor chain is not shipped here. The Tor toggle is hidden on desktop; imported Android configs selecting Tor fall back to Psiphon.

---

## 🏗️ Technical Architecture

### Aether engine `v2.0.0` (Windows binary, verified with `aether -v`)
Official build from [CluvexStudio/Aether `v2.0.0`](https://github.com/CluvexStudio/Aether/releases/tag/v2.0.0) (AGPL-3.0 — SHA256-verified download; same engine generation as the Android `libaether.so`, which also reports `2.0.0`). Shipped unmodified; full source at the linked tag.
The layer responsible for:
- Encrypted tunnel management.
- Dynamic gateway health checks.
- Multi-protocol handling (MASQUE, WG, Gool, MASQUE-in-MASQUE) plus `--upstream` chaining.
- The binary also contains Tor/Zero Trust-team flags, but this port never passes `--tor*` (Tor chain excluded by design) and Zero Trust was removed from the app entirely.

### [HEV SOCKS5 Tunnel v2.17.1](https://github.com/heiher/hev-socks5-tunnel/releases/tag/2.17.1)
The native bridge between the system and Aether (Android Native):
- Mature user-space TCP/IP stack.
- Zero-copy packet processing.
- Efficient UDP over SOCKS5 translation.

### [Psiphon Tunnel Core v2.0.41](https://github.com/Psiphon-Labs/psiphon-tunnel-core/releases/tag/v2.0.41) (GPL-3.0)
The optional second-layer circumvention engine:
- Open-source Psiphon client core for restricted networks.
- Provides foreign exit IPs with selectable egress region.
- On Windows it runs as the separate `psiphon-helper.exe` (built from `psiphon-helper/` sources) and chains over the Aether transports (MASQUE, WG, Gool).

### Compose Multiplatform UI
A unified UI layer sharing logic between Android and Desktop:
- Reactive state management using Kotlin Flows.
- Shared domain logic for IP lookup and configuration management.
- Native system integrations for each platform.

## 🪟 About This Windows Port

This repository is a community Windows port of the upstream project **[immaghzbad/AetherST](https://github.com/immaghzbad/AetherST)** by the **PowerSigma Team** (all upstream credits below remain with them). It re-targets the upstream `v1.7.0` codebase to the desktop via the shared Compose Multiplatform code.

**Maintainer (Windows port):** [Mehrdad-esn](https://github.com/Mehrdad-esn) — repository: [Mehrdad-esn/AetherST-Windows](https://github.com/Mehrdad-esn/AetherST-Windows). Upstream authorship and all upstream credits stay with the PowerSigma Team; see [LICENSE](LICENSE) and [Credits](#-credits).

### 🤖 Built with Vibe-Coding (AI-assisted development)

This Windows port was developed **vibe-coding style**: designed, written and iterated together with an AI coding assistant from the upstream Android source and the maintainer's instructions — feature by feature (desktop TUN helper, system proxy/DNS, tray, MSI installer, OpenVPN Hybrid, desktop Psiphon chain, onboarding, i18n). Every shipped behavior above was verified against real builds and logs; anything the Windows engine cannot enforce was removed rather than faked (see *Not on Windows*). What's actually in the Windows build is listed under Features above — nothing more. Notable deliberate divergences: Cloudflare Zero Trust removed; Tor chain unavailable (engine limitation, see above); Psiphon chain re-implemented for desktop (see below); OpenVPN Hybrid added. Desktop plumbing (TUN helper, system proxy/DNS, tray, single-instance guard, MSI) follows the upstream Windows client's own approach.

### 🔗 Psiphon Chain on Windows (this port)

Same tunnel-core version as Android (`v2.0.41`), but as a separate `psiphon-helper.exe` process built from the sources in `psiphon-helper/` (GPL-3.0 — full build instructions there; Go 1.26.x required). The helper exposes `127.0.0.1:3080` (SOCKS) and chains through the Aether core, so the exit IP is the Psiphon egress. Server entries ship from the same `server_entries.txt` the Android build uses.

### 🔀 OpenVPN Hybrid (exclusive to this Windows port)

Chains a full OpenVPN client over the app's proxy tunnel (the Aether core runs in WireGuard proxy mode underneath):

1. Select **OpenVPN Hybrid** as the protocol (dashboard chips or Settings).
2. Click **Choose .ovpn file…** (or paste the path) into **OpenVPN Config File**, plus username/password if the config requires `auth-user-pass`.
3. Connect. On first use the official OpenVPN Community build is downloaded at runtime from `swupdate.openvpn.org` and extracted locally — OpenVPN binaries are **not** bundled with this repository.

## 🚀 Getting Started

### Installation
1. Go to the [Releases](https://github.com/Mehrdad-esn/AetherST-Windows/releases) page.
2. **Windows:** Download `AetherST-1.7.0.msi` (release **1.7**) and install it (upgrades older versions in place). Older installs are also offered this update automatically through the in-app update prompt with an Update button.
3. **Android (upstream):** Get the APK from the [upstream releases](https://github.com/immaghzbad/AetherST/releases) page (`arm64-v8a` recommended).

### Build from Source
- **IDE:** Android Studio Ladybug (2024.2.1) or newer.
- **JDK:** 17
- **NDK:** 30.0.15729638 (for Android native components).
- **Gradle Tasks:**
  - Android: `./gradlew :app:assembleRelease`
  - Desktop (no Android SDK needed): `./gradlew -PskipAndroid=true :composeApp:packageMsi`
  - Desktop run: `./gradlew :composeApp:run`

## ⚙️ CI/CD & Security

The project uses **GitHub Actions** for automated Multi-APK and Desktop releases.

## 💬 Community

Stay updated and get support through our official channels:

- 📢 **Telegram:** [PowerSigma](https://t.me/PowerSigma)
- 👨‍💻 **Developer:** [@immaghzbad](https://github.com/immaghzbad)
- 🪟 **Windows port maintainer:** [Mehrdad-esn](https://github.com/Mehrdad-esn) — [AetherST-Windows](https://github.com/Mehrdad-esn/AetherST-Windows)

## 🙏 Credits

This project uses the following open-source resources:

- [flag-icons](https://github.com/lipis/flag-icons) — Country flag icons for multi-language and region UI elements.
- [Vazirmatn](https://github.com/rastikerdar/vazirmatn) — Open-source Persian (Farsi) typeface used for RTL language support.
- [Inter](https://github.com/rsms/inter) — Open-source English typeface used for the interface typeface.
- [psiphon-tunnel-core v2.0.41](https://github.com/Psiphon-Labs/psiphon-tunnel-core/releases/tag/v2.0.41) — Open-source Psiphon client core powering the optional Psiphon Chain layer.

---
<p align="center">
  Built with 💙 by <b>PowerSigma Team</b>
</p>
