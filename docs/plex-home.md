# Plex on Home

Version 2.3 adds an optional “Recently added on Plex” carousel in the open area above the favorites dock. It is available from Settings on Fire TV and Google TV. The Fire TV Home button setting remains Fire TV only.

## Link and choose content

Link the launcher at Plex with the four digit code shown on the TV. Android isolates app data, so the launcher cannot read the signed-in account from the Plex app. The launcher keeps its own Plex session and shows the account name it linked.

This is Plex's supported PIN flow: Plex creates a temporary code, the user claims it on their Plex account, and Plex returns an access token for that client. Android assigns apps separate identities and restricts access to each app's private files, so the launcher cannot safely reuse Plex's locally stored token. The Fire TV system can identify its own Amazon account, but that does not grant a third-party launcher access to a Plex session. See [Android's app sandbox](https://developer.android.com/guide/components/fundamentals) and [Plex's authentication API](https://developer.plex.tv/pms/).

Choose one or more servers and either all movie and series libraries on those servers or specific libraries. “All” applies only to the selected servers and includes eligible libraries added later. Server identity and library IDs are saved, so a server name change does not change the selection.

Select “Save and show” after setup. The carousel contains up to 20 items ordered by Plex's addedAt value, which is when Plex added the media to its library. Movies appear individually. New episodes are grouped under their series and ordered by the most recently added episode. Selecting a card updates the backdrop, series/episode details, and an IMDb or Rotten Tomatoes rating when Plex includes one. A series card opens the series details; it does not start an arbitrary episode.

## Network and stored data

Plex cloud account and server requests use HTTPS with the platform TLS checks enabled. Server connection URIs come from the linked Plex account’s resource list; HTTPS local connections are tried before remote and relay connections. Redirects are rejected. The account JWT goes only to Plex cloud; Plex resource tokens are encrypted with Android Keystore and used for the selected server’s library and poster requests. Poster requests use the same server and a server-side size limit.

Plex PIN authentication registers an RSA key in Android Keystore and uses the RS256 JWT flow. The private key never leaves the device. The short-lived Plex token is encrypted with an AES-GCM key in Android Keystore and refreshed when its expiry is near. The app uses private preferences and allowBackup=false. Unlinking clears the token, key material, server/library selections and cached titles; an in-flight catalogue refresh cannot restore the cleared cache.

The cached catalogue contains title, server and Plex item IDs, type, episode title and label, year, Plex-provided rating and source, poster and backdrop paths, and Plex addedAt timestamp. It is scoped to the linked username. Ratings are read from the selected Plex server metadata; the launcher makes no IMDb or Rotten Tomatoes requests. If a server is unavailable, its cached titles can remain visible; a 401/403 response removes that server's inaccessible cached catalogue. The cache is limited to 20 items and image bitmaps to 12 MB in memory. No full library crawl is performed.

Plex tokens can grant more account access than this row needs. The account JWT is used for account identity and server discovery. Per-server tokens are used for library listing, catalogue queries and poster reads; this feature does not modify Plex libraries or play media itself.

## Opening a title

The launcher sends an app.plex.tv details URI to the installed Plex Android app. If the app does not accept that URI, the launcher falls back to opening Plex and tells the user to find the title there. That fallback is intentional; direct movie and series detail routing still needs validation against the Plex TV app versions on Fire TV and Google TV.

Plex Home managed users/PIN profiles are not switched from this settings screen. The linked Plex identity and the server permissions attached to it determine which libraries are available. Plex does not report a completed download time through these queries; this row means recently added to the Plex library.

## Limits

- Popularity trends and Continue Watching are not included.
- A matching title on separate servers may appear twice.
- Insecure HTTP-only server connections are rejected.
- A missing server or library is not silently replaced with a different selection.
- No Plex username/password is collected by this app.

## User interface

The visual direction, card dimensions, remote navigation and Fire TV checks are documented in [Plex Home UX](plex-home-ux.md).
