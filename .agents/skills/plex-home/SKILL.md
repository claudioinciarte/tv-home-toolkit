---
name: plex-home
description: This skill should be used when the user asks to change Plex linking, server or library selection, recent additions, artwork, ratings, Plex settings, or to troubleshoot Plex authentication and connectivity.
---

# Plex Home

Use the repository's Plex workflow for implementation and troubleshooting.

1. Read `AGENTS.md`, `docs/plex-home.md`, and the relevant Plex settings, manager, catalog, adapter, layout and tests before editing.
2. Keep authentication in this launcher. Use Plex's PIN flow; never read another package's private files or copy its token. Never collect or log the user's Plex password, PIN, JWT, server token or private library data.
3. Preserve Android Keystore encryption, TLS validation, the existing server connection order, cache generation checks, unlink cleanup and the catalogue cap. Do not add a full library scan or direct IMDb/Rotten Tomatoes requests for this row.
4. Keep the meaning precise: Plex `addedAt` means added to the library, not the actual download completion time. Show ratings only when Plex returns a recognized source and value.
5. On connection failures, distinguish account authentication, server authorization and DNS/TLS connectivity. Preserve the server/library selection and cached catalogue rules documented in `docs/plex-home.md`.
6. Update the Plex documentation when visible behavior, stored data or network behavior changes. Keep tokens, usernames, real IPs, catalogue dumps and device screenshots out of commits.
7. Build with `scripts/Build-Launcher.ps1`. Run the relevant unit checks when the current request asks for verification or when changing pure catalogue/rating logic. For Plex UI changes, use `tvhome-ux-loop` and only a previously authorized device.
