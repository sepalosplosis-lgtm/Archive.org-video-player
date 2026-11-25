# Archive Player roadmap to v1.0

## Scope
Android-first player for local audio/video and permitted downloadable/streamable Archive.org items. Offline access is for files the user is allowed to download. No promise of downloading from unrelated streaming services.

## Milestones
- v0.1: UI and playback proof of concept
- v0.2: Cloud build workflow
- v0.3: Room library, bookmark, playlist data layer; DataStore settings; Archive search/file-choice repository
- v0.4: MediaSession service and persistent background playback controls
- v0.5: Download queue, progress, pause/resume/cancel, offline library and error states
- v0.6: Audio effects UI, multi-band EQ, bass/reverb; independent pitch-shift research/implementation
- v0.7: Video filters (CRT/VHS) and image controls
- v0.8: Archive item detail page, file-format/resolution selection, subtitle support
- v0.9: External storage browsing, accessibility, landscape and PiP lifecycle, stability tests
- v1.0: Release build, crash/compatibility testing, privacy review, documentation

## Current status
The v0.3 code adds persistence primitives and a separate Archive.org repository. The existing UI has not yet been fully connected to these systems. This is a development scaffold, not a verified build. Cloud build is intended to expose compile errors early.
