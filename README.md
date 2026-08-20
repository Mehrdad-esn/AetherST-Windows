<p align="center">
  <img src="https://img.shields.io/badge/AetherST-1.4.2-007AFF?style=for-the-badge&logo=shield&logoColor=white" alt="AetherST">
  <img src="https://img.shields.io/badge/Platform-Windows%20x64-0078D6?style=for-the-badge&logo=windows&logoColor=white" alt="Platform">
  <img src="https://img.shields.io/badge/Engine-Aether%20Core%201.7.0-34C759?style=for-the-badge&logo=rust&logoColor=white" alt="Engine">
  <img src="https://img.shields.io/badge/Tests-33%20green-34C759?style=for-the-badge&logo=checkmarx&logoColor=white" alt="Tests">
  <img src="https://img.shields.io/badge/Installer-MSI-FFD700?style=for-the-badge&logo=windows&logoColor=white" alt="Installer">
</p>

<h1 align="center">AetherST — Windows Port</h1>

<p align="center">
  <strong>Native Windows client for the AetherST censorship-circumvention tunnel.</strong><br>
  The Android client's UI, rebuilt with <strong>Compose Multiplatform</strong> and re-architected for the desktop.
</p>

---

## 🤖 A word from the author (Mehrdad)

**I did not write this code.** Every line — the port itself, the desktop integration, the installer, the tests — was written by **DeepSeek v4 Flash (the AI, running in opencode)**, based on the original Android source and on instructions I gave during a chat. My role was directing the work: explaining what I wanted, reviewing the results, and saying "fix this, remove that". The full list of the instructions I gave is [at the bottom of this README](#-instructions-i-gave-during-the-chat).

> This is not a fork of the Android app. It is a **re-implementation** of it for Windows: the Android UI (Jetpack Compose) was migrated to Compose Multiplatform, the Android-specific plumbing was replaced with desktop equivalents, and the whole thing was verified with an automated test suite.

---

## ✨ What this app is

AetherST Windows is a privacy tunnel client that connects through the **Aether Core** — a Rust engine that discovers gateways, obfuscates traffic and exposes a local **SOCKS5** proxy. Everything else in the app orbits that proxy.

| | |
|---|---|
| 🛡️ **Stealth protocols** | **MASQUE** (HTTP/2 & HTTP/3), **WireGuard**, **Gool (WireGuard-in-WireGuard)**, **Cloudflare Zero Trust** |
| 🔀 **OpenVPN Hybrid** *(Windows-only)* | Route a full **OpenVPN** client through the app's proxy tunnel — see [OpenVPN Hybrid](#-openvpn-hybrid) |
| 🌫️ **Obfuscation profiles** | Firewall, GFW, Balanced, Aggressive, Light, Off |
| 📡 **Gateway scanning** | Turbo / Balanced / Thorough / Stealth / Ironclad — with end-to-end data-plane validation before connecting |
| 🧭 **Routing rules** | Domain/IP/port-level **block**, **direct** and **tunnel** rules with preset packs (Iran direct, ad & DNS block, adult content block) |
| 📊 **Dashboard** | Live status, session traffic, elapsed time, public IP info, ping, one-tap protocol switching |
| ⚙️ **Full settings** | Connection mode, HTTP/2 fallback, packet fragmentation, MTU optimizer, DNS, keepalive, validation, presets, log levels, config backup/export |
| 🖥️ **Desktop integration** | System tray, single-instance guard, custom title bar, close-to-tray, native MSI installer |
| ✅ **Tested** | 33 automated tests — config persistence, presets, migration, routing rules, OpenVPN settings |

### Two connection modes

- **Proxy Only** *(default, no admin needed)* — AetherST exposes `127.0.0.1:1819` (SOCKS5) and `127.0.0.1:1820` (HTTP CONNECT). Point any app at these addresses to tunnel it.
- **Tunnel** *(requires elevation)* — the **HEV Tun2Socks** engine creates a `AetherST` TUN adapter with its own DNS and routes your whole system through it. A hidden elevated helper sets up the adapter, routes and DNS, and tears everything down when you disconnect.

---

## 📦 Install

1. Download `AetherST-1.4.2.msi`.
2. Double-click it and accept the UAC prompt. It installs to `C:\Program Files\AetherST` and creates a desktop shortcut.
3. To uninstall: `Settings → Apps → AetherST` (or re-run the MSI), then a clean registry removal.

> No installer leftovers, no background services, no admin at runtime (except when you enable **Tunnel** mode).

## 🚀 Quick start

1. Launch AetherST. The first run walks you through a quick onboarding that actually tests every protocol against your network.
2. From the dashboard, pick a protocol — **MASQUE** is the default.
3. Hit **Connect**. The app scans for the best gateway, validates it end-to-end, and shows `PROTECTED & CONNECTED`.
4. Point your apps at `127.0.0.1:1819` (SOCKS5) — or switch to **Tunnel** mode in Settings to route everything.

### What's where

- **Dashboard** — connect button, protocol chips, IP/ping, traffic.
- **Settings** — everything: connection mode, transport, noise, scan strategy, fragmentation, routing rules, Zero Trust auth, MTU, backup.
- **Logs** — real-time app and core logs with level filtering (`Off … Debug`).
- **About** — credits, versions and the Windows-port story.

---

## 🔀 OpenVPN Hybrid

A feature unique to this Windows port. It chains two tunnels:

```
your apps ──► AetherST proxy (SOCKS5 127.0.0.1:1819) ──► Aether Core (WG proxy mode) ──► Internet
        └────────► OpenVPN client (--socks-proxy 127.0.0.1:1819) ──► TUN adapter ──► Internet
```

1. Select **OpenVPN Hybrid** as the protocol (in Settings or the dashboard chips).
2. Tap **OpenVPN Config File** and pick any `.ovpn` file.
3. Connect. AetherST automatically downloads the official OpenVPN Community build, extracts it, wraps your config with `socks-proxy 127.0.0.1:1819` and launches it over the app's tunnel.

The result: a full OpenVPN tunnel that itself travels inside the AetherST tunnel — useful when you need OpenVPN's own routing/filtering on top of stealth transport.

---

## 🛠️ Settings that actually work on Windows

Every option below is wired to the real engine — nothing decorative:

- **Connection mode** — Proxy Only / Tunnel
- **Transport** — MASQUE, WireGuard, Gool, Zero Trust, OpenVPN Hybrid
- **HTTP/2 Fallback** and **Packet Fragmentation** (size/delay) — MASQUE TCP/TLS path
- **Noise profile**, **Scan strategy**, **Network stack** (IPv4 / IPv6 / Dual)
- **Skip data-plane check**, **Quick gateway reconnect**, **No profile retry**, **Keepalive**, **Validate/Reconnect seconds**
- **Domain & IP routing** (block/direct/tunnel rules)
- **Cloudflare Zero Trust** — team enrolment, service tokens, email login, organization gateway
- **Smart reconnect** with max-retry limit
- **DNS servers**, **MTU optimizer** (probes your network and applies the best MTU)
- **Core log level** — actually passed to the engine (`--log-level`), not just stored
- **Presets** — Custom / Bypass UDP-TLS / Ironclad Stealth / Turbo Speed
- **Backup** — export/import the full configuration (`.astf`), reset to defaults

> Android-only features that the Windows engine cannot enforce (per-app split tunneling, kill switch, IPv6-leak toggle, engine switcher) were **deliberately removed** rather than shown as dead switches. The UI only ever shows what really works.

---

## 🧩 Parts of this repository

| Path | What it is |
|---|---|
| `composeApp/` | **The Windows port** — Compose Multiplatform UI, desktop core (process runner, TUN helper, proxies, OpenVPN connector), data layer, 33 tests |
| `app/` | The **original Android client v1.4.2** source (kept for reference and credit) — see [`app/README.md`](app/README.md) |
| `update.json` | Version manifest served over GitHub Pages/raw for the in-app update check |

### How the Windows port works

- **`AetherProcessRunner`** launches the bundled `aether.exe` core with CLI flags + `AETHER_*` env vars mapped from the config, supervises it and parses its output into structured logs.
- **`TunHelper`** builds an elevated PowerShell helper that creates the TUN adapter (via the bundled `wintun.dll`), sets DNS, installs routes, monitors the tunnel and cleans up on exit.
- **`RoutingEngine`** renders routing rules into the core's own rule file (`[block]` / `[direct]` sections).
- **`OpenVpnConnector`** resolves, downloads and extracts OpenVPN Community, generates a wrapper config and spawns `openvpn.exe` with `--socks-proxy`.
- **Desktop layer** — tray, single-instance guard, close-to-tray, custom title bar, native `FileDialog`s, crash-report screen.

---

## 🧪 Build from source

Requirements: JDK 17+, Windows 10/11 x64.

```powershell
git clone https://github.com/Mehrdad-esn/AetherST-Windows.git
cd AetherST-Windows
.\gradlew.bat -PdesktopOnly :composeApp:test        # run the 33 tests
.\gradlew.bat -PdesktopOnly :composeApp:packageMsi  # build the MSI
```

The MSI lands in `composeApp\build\compose\binaries\main\msi\`.

---

## 🙏 Credits — per part

| Part | Made by |
|---|---|
| **Android client v1.4.2** (UI concept, texts, config model, presets) | **[PowerSigma Team](https://github.com/immaghzbad/AetherST)** — Kotlin + Jetpack Compose |
| **Aether Core v1.7.0** (the Rust engine: gateway scan, MASQUE/WG/GOOL/ZeroTrust, obfuscation) | **[CluvexStudio](https://github.com/CluvexStudio)** — C/Rust |
| **HEV SOCKS5 Tunnel v2.15.0** (TUN-to-SOCKS bridge, `hev-socks5-tunnel.exe` + `wintun.dll`) | **[heiher](https://github.com/heiher)** — C, MIT |
| **Windows port** (everything in `composeApp/`) | **DeepSeek v4 Flash** — an AI, working inside opencode, directed by Mehrdad (@Mehrdad-esn) |

The country flags bundled in the app come from [lipis/flag-icons](https://github.com/lipis/flag-icons) (MIT).

---

## 🎓 Instructions I gave during the chat

The port was built step-by-step from these instructions (paraphrased, in the order I gave them):

1. Port the Android app to Windows with **Compose Multiplatform** and a native look.
2. Make the main window smaller (height 700) so it fits comfortably on a desktop screen.
3. Fix the bottom navigation bar — the labels were clipped; remove the downward text shift.
4. Use the app's own `icon.png` for the system tray icon and the title-bar badge.
5. Replace emoji country flags with real ones — emoji flags render as letters on Windows (bundled 271 SVG flags).
6. Align the About screen texts word-for-word with the original Android wording and restore the original makers' credit card ("Built with ❤ by PowerSigma Team").
7. Add a "Windows Port" credit card naming me and the AI honestly.
8. Make it a proper **MSI** installer that installs into Program Files, with no leftover files and a clean uninstall (we debugged a stale registry entry from an earlier build).
9. Add a **single-instance guard** and **close-to-tray** behavior.
10. Add an **OpenVPN Hybrid** protocol: run the core in WireGuard proxy mode and chain OpenVPN over the app's SOCKS5 proxy.
11. Show only real installed apps in split tunneling, filtering out libraries and junk (Anaconda, CUDA, runtimes, drivers, SDKs).
12. Then: **remove every fake feature** — after auditing, per-app split tunneling, kill switches, the IPv6-leak toggle and the engine switcher cannot be enforced by the Windows engine, so they were deleted instead of being shown as dead switches.
13. Make the core log-level picker actually pass `--log-level` to the engine.
14. Document everything honestly — and never mention removed features in the app or this README.

---

## 📄 License

This repository contains the Windows port (built with AI assistance) and the original Android source. See `LICENSE` for the terms of the original project; HEV components are MIT.