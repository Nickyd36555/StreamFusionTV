# Stream Fusion TV

Standalone Android TV application combining a Stremio-compatible catalog/add-on
client with an independently implemented IPTV and XMLTV experience.

Implemented:
- Movies and series catalog browsing and search
- Stremio-compatible add-on URL configuration and HTTP stream selection
- Xtream Codes authentication through `player_api.php`
- Xtream live channels, category names, and XMLTV guide data
- Stremio-compatible movie and series catalogs, search, and add-on streams
- Direct Xtream live playback URLs
- M3U/M3U+ fallback support
- XMLTV parsing and TV guide rows
- Channel filtering, media library, and channel favorites
- AndroidX Media3/ExoPlayer playback
- Android TV launcher and remote-focus navigation

The project does not contain Stremio or TiviMate proprietary application code,
billing, licensing, or premium checks. It implements interoperable protocols
and equivalent user-facing workflows independently.

TiviMate proprietary code, billing, licensing, and premium checks are not included or bypassed.

## Updates

Set `githubRepository=OWNER/REPOSITORY` in `gradle.properties`. On launch, the app
checks the repository's latest GitHub Release. Release tags must contain the numeric
Android version code (for example `stream-fusion-tv-v3`) and the release must include
an APK asset named `StreamFusionTV-*.apk`. Other releases are ignored.
Android requires the user to approve APK installation; normal apps cannot silently
install updates.
