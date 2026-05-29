# RVC MVP Slices

This document records the semantic-storage MVP work completed in the current implementation pass and breaks the remaining MVP into thin, testable slices.

## Current Direction

RVC uses Git as the history/sync control plane and RVC semantic chunks as canonical structure content.

- Git owns commits, branches, remotes, authorship, merge-base, push, and pull.
- RVC owns project/site/region semantics, tracked masks, block states, block entities, ticks, entities, scans, export, and restore.
- `.litematic` and vanilla structure `.nbt` are export/compatibility formats, not the long-term canonical storage.
- Dirty state should come from authoritative scans/hashes, not from event history.
- Singleplayer should use integrated-server state. Multiplayer requires server-side RVC for reliable capture/restore.

## Done In This Pass

- Documented the long-term storage direction in `docs/PRD.md`, `docs/prd-corrections.md`, `docs/tech/rvc.md`, and `docs/tech/rvc-semantic-storage.md`.
- Added the semantic object model:
  - `RvcChunkCoordinate`
  - `RvcChunk`
  - `RvcChunkCodec`
  - `RvcChunkStore`
  - `RvcManifest`
  - `RvcLocalState`
  - `RvcIntPosition`
- Added capture plumbing:
  - `RvcWorldReader`
  - `RvcCapturePlanner`
  - `RvcCaptureEngine`
  - `RvcSemanticRepository`
- Added Git integration for semantic repos:
  - `RvcRepository.commitFilePatterns(...)`
  - semantic init/commit stages `rvc.json`, `objects/sha256`, `README.md`, and `.gitignore`
  - `local.json` remains ignored
- Added Minecraft-world capture bridge:
  - `RvcMinecraftWorldReader`
  - canonical block state strings
  - canonical block entity NBT
  - absolute block entity `x/y/z` removed before hashing
- Updated project service behavior:
  - new projects created from the Litematica save flow now create semantic repos
  - commits against semantic repos capture active-site semantic chunks
  - singleplayer semantic init/commit capture uses the integrated server's matching `ServerLevel` when available
  - manual scan changes hashes active-site semantic chunks without writing objects or changing `rvc.json`
  - `Update areas` updates active-site semantic regions from the current Litematica selection and commits current content
  - project manager accepts both semantic `rvc.json` repos and legacy `index.json` repos
  - semantic checkout/pull/overlay restore is explicitly blocked until export/restore exists
- Polished the RVC project/project-manager UI:
  - project browser delete confirmation and validated recursive delete under `run/rvc-projects`
  - project browser navigation rooted at `rvc-projects`
  - commit history search, row selection, mouse-wheel scrolling, and stable scrollbar gutter
  - selected commit metadata panel with Title, Author, optional Description, Date, Version, and Changes
  - conditional scrollbar rendering for history, metadata, and project browser panels
  - sidebar action buttons anchored to align Close Project with the commit history panel bottom edge
- Added integration tests for semantic storage, fake-world capture, object reuse, manifest/local state, Minecraft block state encoding, canonical NBT, and semantic repo init/commit.

Verified with:

```bash
JAVA_HOME=/home/arnav/.jdks/temurin-25.0.3 ./gradlew integrationTest
```

## Manual Test Target Now

Only test semantic init/commit in singleplayer for now.

Expected to work:

- create a project from a Litematica area selection
- repo appears under `run/rvc-projects/<project>`
- repo contains `rvc.json`, `local.json`, `objects/sha256/**.rvcchunk`, `README.md`, `.gitignore`, and `.git`
- commit after changing tracked blocks/block states/block entities
- chest inventory/block entity changes should hash in singleplayer because capture reads integrated-server state
- `Scan changes` reports clean after an unchanged commit and dirty after tracked block/block entity changes
- `Update areas` can expand or shrink the tracked selection and commit the result
- project page commit history can be searched and scrolled
- selected commit metadata shows title/author/date/version/changes and only shows a scrollbar if content overflows
- unchanged semantic chunks reuse old object hashes
- exact no-op capture creates no Git commit at service level

Known not ready:

- semantic checkout restore
- semantic pull restore
- semantic overlay/verifier loading
- export to `.litematic`
- world association warnings for portable repos loaded in a different world
- update/resize tracked regions
- dedicated-server authoritative capture
- entity capture
- scheduled tick capture

## Thin Vertical Slices

### Slice 1: Semantic Chunk Core

Status: done.

Verification:

- deterministic `.rvcchunk` encoding
- decode round trip
- content-addressed object write/read
- duplicate content reuses object path

### Slice 2: Manifest, Local State, And Fake-World Capture

Status: done.

Verification:

- `rvc.json` and `local.json` round trip
- same-dimension multi-site manifest is valid
- overlapping regions are rejected for MVP
- gaps between regions stay untracked
- changed fake-world content changes only intersecting RVC chunk hash
- local site origin is applied before world reads

### Slice 3: Semantic Git Repository Init/Commit

Status: done.

Verification:

- init creates a Git repo and first commit
- semantic commit includes manifest/object files but excludes `local.json`
- no-op semantic commit returns `null` and does not move `HEAD`
- reverting content to an older chunk state reuses the older object hash

### Slice 4: Minecraft Reader And UI Entry Point

Status: backend done, manual QA pending.

Verification:

- canonical block state strings include all properties sorted by name
- block entity NBT is deterministic and ignores absolute position
- create-project flow initializes semantic repos from Litematica selections
- semantic init/commit captures from integrated-server `ServerLevel` in singleplayer when available

Manual QA:

- run client
- create singleplayer project
- inspect repo files
- change tracked blocks/block states/chest inventory
- commit
- inspect Git log and `rvc.json`

### Slice 5: Semantic Commit UX Polish

Status: done.

Goal:

- Make the project GUI report no-op semantic commits correctly instead of always showing generic committed success.
- Show that semantic overlay/export is not ready without treating successful commits as tracking failures.
- Keep legacy `index.nbt` repo behavior unchanged.

Verification:

- no-op commit keeps history length unchanged and shows a clear message
- real semantic commit creates a history entry
- legacy commit still reloads overlay/verifier

### Slice 6: Manual Scan Changes

Status: done for active semantic site.

Goal:

- Add scan that hashes current tracked chunks without writing objects or changing `rvc.json`.
- Expose clean/dirty/unknown state for the active site.

Verification:

- scan clean project reports clean
- block change inside tracked region reports dirty chunk
- change outside tracked region reports clean
- unloaded/unavailable authoritative chunk reports unknown

Still pending:

- Reuse scan result as preflight for commit/checkout/pull/reset/merge.
- Dedicated-server authoritative scan path.

### Slice 7: Update Areas

Status: done for active semantic site.

Goal:

- Let users expand/shrink/rename regions by using current Litematica selection and clicking `Update areas`.
- Update versioned `rvc.json` regions.
- Keep `local.json` origin local-only and only change it with explicit user choice.

Verification:

- expanding a region captures newly tracked blocks on next commit
- shrinking a region removes chunk refs with no tracked positions
- gaps remain untracked
- overlapping same-site regions are rejected with clear message

Still pending:

- Legacy `index.json` update areas.
- Rich preview of added/removed/renamed regions before commit.
- Explicit local-origin update/move flow.

### Slice 8: Semantic Export/Overlay/Restore

Status: pending.

Goal:

- Build a Litematica/vanilla structure view from semantic chunks.
- Load ghost overlay/verifier from semantic repo state.
- Restore tracked positions without touching untracked gaps.

Verification:

- semantic project can show overlay after init/commit
- checkout restores semantic content visually/in-world in singleplayer
- untracked gaps are not written as air
- block entities restore at reconstructed positions

### Slice 9: Integrated-Server Authority

Status: partially done for semantic init/commit.

Goal:

- In singleplayer, capture/scan/restore via integrated server state instead of client-only world state.

Done:

- Semantic init/commit resolves the integrated server's matching `ServerLevel`.
- Capture runs on the server thread via `MinecraftServer#submit(...)`.
- Manual semantic scan changes uses the same integrated-server capture path.

Still pending:

- Semantic restore/checkout/pull is still blocked until export/restore exists.

Verification:

- chest/container block entity inventory changes are captured from authoritative server state during init/commit
- command/mod/world-simulation changes are captured from authoritative server state
- unloaded tracked chunks are handled explicitly
- client-only mismatch cannot be reported as clean

### Slice 10: Dedicated Server Support

Status: pending.

Goal:

- Add server-side RVC path for multiplayer.
- Client UI requests scan/commit/restore from server-side mod.
- Refuse reliable commit/restore on servers without server-side support.

Research note:

- Investigate Servux first instead of designing from scratch.
- Servux is a server-side Fabric mod for masa client mods and current metadata describes Litematica server-side save/paste support with full tile entity data.
- Candidate sources: `https://modrinth.com/mod/servux`, `https://github.com/maruohon/servux`.

Verification:

- dedicated server can authoritatively scan tracked chunks
- client-only multiplayer reports unsupported/unknown, not clean
- permission checks prevent unauthorized restore/commit actions

### Slice 11: Scheduled Ticks

Status: pending.

Goal:

- Capture pending block ticks and pending fluid ticks only.
- Treat tick conflicts as simulation metadata, not normal user-facing merge conflicts.

Verification:

- pending block/fluid ticks in tracked positions affect chunk hash
- ticks outside tracked positions are ignored
- invalid/ambiguous ticks can be dropped with summary warning

### Slice 12: Entities

Status: pending.

Goal:

- Decide entity tracking defaults and cleanup semantics.
- Add entity records to `.rvcchunk` only after restore behavior is safe.

Verification:

- tracked entities serialize deterministically
- restore does not duplicate stale entities
- disabled entity tracking is explicit and documented

## Current Highest Risks

- Semantic projects cannot yet export/restore/overlay, so checkout and pull are blocked for them.
- Client-only multiplayer capture is not authoritative; dedicated-server support needs a server-side RVC path.
- `Update areas` has only the basic semantic active-site path; richer preview, legacy support, and explicit origin moves are still missing.
