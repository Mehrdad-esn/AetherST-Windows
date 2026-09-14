# psiphon-helper (GPL-3.0)

Standalone Psiphon client for the AetherST Windows port. It runs
[psiphon-tunnel-core](https://github.com/Psiphon-Labs/psiphon-tunnel-core)
(`v2.0.41`, same version as the Android app) as a local SOCKS/HTTP proxy, so
the desktop app can chain traffic through Psiphon exactly like Android does —
without needing Android-only binaries.

## License (read this)

- `psiphon-tunnel-core` is **GPL-3.0**. This helper links it and is therefore
  distributed under **GPL-3.0** as well.
- The main Kotlin app talks to the helper only as a **separate process over
  local SOCKS/HTTP** (same pattern as the OpenVPN Hybrid mode) and keeps its
  own license. Upstream credits (PowerSigma, Psiphon Labs) are preserved in
  the main `LICENSE`/`README`.
- Full source of this helper is in this directory (`main.go`, `go.mod`,
  `go.sum`). The `upstream/` source clone is **not** committed; fetch it with
  git (see below) to rebuild from scratch.

## Build (Windows, no admin needed)

You need Go **1.26.x** (the pinned toolchain below). Do NOT use Go 1.27+:
`psiphon-tls` asserts the old `crypto/tls.ConnectionState` layout and panics
at startup on newer toolchains (verified).

```powershell
# 1. portable Go (example)
# download https://go.dev/dl/go1.26.8.windows-amd64.zip, extract, add bin/ to PATH

# 2. fetch the exact upstream source (not committed)
git clone --depth 1 --branch v2.0.41 https://github.com/Psiphon-Labs/psiphon-tunnel-core upstream

# 3. build
$env:GOTOOLCHAIN = "local"
$env:CGO_ENABLED = "0"
go mod tidy
go build -ldflags="-s -w" -o "../composeApp/src/desktopMain/resources/bin/psiphon-helper.exe" .
```

The committed `psiphon-helper.exe` in app resources was built exactly this
way (Go 1.26.8, `-ldflags="-s -w"`, CGO disabled).

## Runtime

```
psiphon-helper --socks-port 3080 --http-port 3081 \
  --data-dir <writable-dir> --server-entries <server_entries.txt> \
  [--egress-region US] [--upstream socks5://127.0.0.1:1819]
```

- `--server-entries` is written at runtime by the app from its bundled copy
  (same file the Android build uses).
- `--upstream` makes Psiphon dial *through* another proxy (used to chain
  Psiphon over the Aether core). Omit it for direct (Psiphon-only) mode.
- Status comes on stdout as `PSIPHON_EVENT noticeType=<...>` lines:
  `ListeningSocksProxyPort`, `ListeningHttpProxyPort`,
  `AvailableEgressRegions regions=US,DE,...`, `Tunnels count=1`.
- Stop with SIGTERM / process kill (BoltDB datastore is crash-safe).
