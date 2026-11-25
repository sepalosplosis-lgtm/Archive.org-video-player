# Archive Player Android — development build

**Status:** early development scaffold. It has not yet been compiled, installed, or device-tested. The Room/DataStore/repository files in v0.3 are groundwork and are not all connected to the existing screen yet.

## Phone-only cloud build
1. Download and unzip this project.
2. On GitHub, create a private repository.
3. Upload the project's files/folders (including `.github/workflows/android.yml`) to the repository root.
4. Open **Actions** → **Build Archive Player APK** → **Run workflow**.
5. Once the run succeeds, download the artifact `ArchivePlayer-debug-apk`, unzip it, and install the APK. Android may ask to permit installation from your browser/file manager.

The debug APK is for testing only; it is not a signed release build. The cloud build may fail until we resolve source/Gradle issues; report the build error or send a screenshot and we can fix it.

## Features in the prototype
- Media3 playback UI, audio/video local picker
- Archive.org search prototype and direct-file resolver
- Playback speed 0.25×–8×, pitch control
- PiP entry, CRT overlay
- Basic Android EQ/BassBoost/PresetReverb API
- DownloadManager handoff, basic bookmarks/queue
- v0.3 groundwork: Room entities/DAO for library, playlists, playlist items, bookmarks; DataStore settings; separate Archive.org search and file list repository

## Roadmap to v1.0
- v0.4: MediaSession and reliable background playback
- v0.5: persistent downloads with progress/pause/resume/offline library
- v0.6: complete effects controls, multi-band EQ and quality pitch-shifting
- v0.7: CRT/VHS filters and picture controls
- v0.8: Archive details and selectable formats/subtitles
- v0.9: external storage, accessibility, PiP/lifecycle, compatibility testing
- v1.0: release build, privacy/permissions review, tests, polish and documentation

Downloads should be limited to media with an available download and appropriate permission/rights. Archive.org items vary in availability, formats, and licensing.
