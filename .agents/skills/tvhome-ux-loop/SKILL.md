---
name: tvhome-ux-loop
description: This skill should be used when the user asks to redesign or polish the TV launcher, improve a carousel, fix card clipping or focus, or verify DPAD layout changes on Fire TV or Google TV.
---

# TV Home UX Loop

Use the real TV screen and remote model while iterating on launcher UI.

1. Read `AGENTS.md`, the platform case under `docs/cases/`, and `.agents/skills/fire-tv-home/SKILL.md` or `.agents/skills/google-tv-home/SKILL.md` as appropriate.
2. Inspect the current layout, listeners and screen capture before editing. Identify available space above the dock, actual content artwork, focus states and the DPAD routes.
3. Pick one visual direction from the product itself. Keep the existing typeface and palette unless the brief calls for a change. Prefer real Plex artwork and restrained user-triggered transitions over repeated badges, gradients, glow or autoplay.
4. Make the smallest complete change. Preserve left/right navigation, vertical return paths, visible focus, readable metadata, accessibility descriptions and dock clearance.
5. Build with `scripts/Build-Launcher.ps1`. Install only on the device and platform authorized for the task, using `scripts/TvHome.ps1`; preserve app data and Amazon Home.
6. Capture a screenshot outside the repository. Review the full screen at its actual resolution, including the title, selected card edges and dock. Use DPAD to inspect focus movement and the return path.
7. Fix what the screenshot reveals, rebuild and repeat until the content is legible, the selected state is obvious and no row is clipped. Record the tested device, resolution and remaining visual limitations in the relevant Markdown documentation.
8. Do not commit screenshots, real device addresses, serials, accounts or library contents. For documentation-only work, do not connect to a device.
