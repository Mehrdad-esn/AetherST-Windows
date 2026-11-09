# Engine version research

Why the Windows build ships Aether engine **v2.4.0**. The 2026-09-14 research
below records the original move to v2.0.0 (kept for history); the 2026-11-10
re-base to v2.4.0 is recorded after it.

## Findings

1. **Android engine is v2.0.0.** Strings in
   `app/src/main/jniLibs/arm64-v8a/libaether.so` contain `aether 2.0.0` plus
   `--tor-only`, `--tor-reverse`, `--mim`, `--upstream` flags.
   Source: binary introspection of the upstream 1.7.0 snapshot.
2. **Previously bundled Windows engine was v1.8.0** (`aether -v`), with none
   of the above flags (`aether --help` checked line by line). The `2.0.0`
   number in the old README/About came from upstream docs, not the binary.
3. **Official v2.0.0 Windows build exists.** CluvexStudio/Aether tag `v2.0.0`
   (published 2026-09-12) ships `aether-windows-x86_64.zip` (14.8 MB).
   Source: `GET /repos/CluvexStudio/Aether/releases` (authenticated GitHub API).
4. **Integrity verified.** SHA256 of the download matches both the published
   `.sha256` file and `SHA256SUMS.txt`:
   `95a2abcb34c6bb22207214b32d1e58f55a304b352d26e3bf6a0c05cda7a2c4df`.
   Extracted binary reports `aether 2.0.0`; `--help` (297 lines) lists
   `--tor/--tor-reverse/--tor-only`, `--upstream`, `--mim*`, `--protocol`.
5. **HEV is v2.17.1** on both sides: desktop binary prints
   `Version: 2.17.1 9a06bc6`; Android README names the same version.
6. **Psiphon is v2.0.41** on both sides: Android `.aar` filename; desktop
   helper built from the `v2.0.41` tag of psiphon-tunnel-core (see
   `psiphon-helper/README.md`).

## License analysis (bundling the v2.0.0 Windows binary)

- Aether is **AGPL-3.0** (GitHub license API + LICENSE body).
- Redistribution is allowed: binary shipped **unmodified**, with the
  copyright/license preserved (`LICENSE-AGPL-3.0.txt` in app resources, fetched
  from the engine author's own repo), a visible credit + exact-tag source link
  in `README.md`/release notes.
- No copyleft spillover into the app: the Kotlin app drives `aether.exe`
  only as a **separate OS process** (CLI args, env vars, stdout parsing,
  local SOCKS) — separate work, same pattern as the OpenVPN (GPL) and
  psiphon-helper (GPL) integrations.
- AGPL §13 (network use of modified versions) does not trigger: the binary
  is unmodified and runs locally as a client tunnel, not as a network service.

## Update — 2026-11-10 re-base to Aether v2.4.0 / HEV v2.19.3

- **Aether engine → v2.4.0.** Official `CluvexStudio/Aether` tag `v2.4.0`
  (published 2026-10-24). Zip SHA256
  `21b1444628d37121e873878e2eddd7932215418af1a2a12470caee5809026f1a`; the
  extracted `aether.exe` (24,467,456 B) prints `aether 2.4.0`.
- **Behaviour change handled:** in 2.4.0 the bare `--gool`/`AETHER_GOOL_MODE`
  now means the new **WARP-over-MASQUE** gool; the old WireGuard-in-WireGuard
  gool is selected explicitly with `--gool-classic` / `AETHER_GOOL_MODE=classic`
  (or any `AETHER_WIW_*` var). The app sends `AETHER_GOOL_MODE=classic` for the
  classic engine and `AETHER_GOOL_MODE=masque` for the modern one, so the mode
  is deterministic regardless of inherited env.
- **New capability surfaced:** `--exit-loc` / `--exit-loc-secs` (exit-country
  lock) is wired to a new Security-page control, and the modern gool gets a
  `--gool-peer` inner-endpoint override.
- **HEV → v2.19.3.** `heiher/hev-socks5-tunnel` tag `2.19.3` (2026-10-25);
  `hev-socks5-tunnel.exe` = 326,144 B (same `msys-2.0.dll` / `wintun.dll`).
- **Psiphon → v2.0.42.** `Psiphon-Labs/psiphon-tunnel-core` tag `v2.0.42`
  (2026-10-19); `psiphon-helper.exe` rebuilt from source (see
  `psiphon-helper/README.md`).
- **Engine flags now present that this port still does not use:** 2.4.0 ships
  `--tor*` (and `pt/lyrebird.exe`) and a built-in `--psiphon`. Tor is excluded
  by design; the second layer stays the separate `psiphon-helper.exe`.
- **License:** unchanged (Aether AGPL-3.0, HEV MIT, psiphon-tunnel-core
  GPL-3.0); all binaries shipped unmodified as separate processes.
