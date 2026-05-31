# RVC Architecture Notes

RVC adds Git-backed version control for Minecraft structure projects inside Litematica. A project is a normal Git repository containing shared schematic state plus local workspace state.

## Authority Order

When docs conflict, use this order:

1. `docs/prd-corrections.md`
2. `docs/TODO.md`
3. `docs/tech/rvc.md`
4. Existing implementation constraints in the Litematica codebase
5. `docs/PRD.md`

The external PRD is useful for product intent, but it is not authoritative when it conflicts with corrections.

One known conflict: `docs/tech/git-ops.md` says checkout should not modify client/server world. `docs/prd-corrections.md` supersedes that. Current required direction is that operations changing active RVC state must also update visible in-game state: schematic state, overlay, and verifier when available.

## Product Goal

RVC should let builders turn Litematica selections into version-controlled structure projects:

- Convert a current Litematica area selection into an RVC repo.
- Commit meaningful versions of tracked sub-regions.
- See the active committed state as an overlay and verifier-backed clean/dirty signal.
- Checkout or pull a version and see the active project state update in-game.
- Eventually support branch, diff, inspect, merge, conflict resolution, and safer area update flows.

## Project Repository Format

There are now two repository formats in the codebase.

Semantic format is the active MVP direction for newly-created projects:

- `rvc.json` - versioned project manifest, sites, regions, and chunk object refs.
- `objects/sha256/**.rvcchunk` - immutable content-addressed semantic chunks.
- `README.md` - generated project description.
- `.gitignore` - must ignore `/local.json`.
- `local.json` - clone-local workspace/site placement state, never committed.

Legacy structure format still exists for compatibility and existing tests:

- `index.json` - versioned RVC project metadata and shared sub-region definitions.
- `index.nbt` - versioned vanilla structure data for the tracked contents.
- `README.md` - generated project description.
- `.gitignore` - must ignore `/local.json`.
- `local.json` - clone-local workspace state, never committed.

Do not add `history.json`. Git commits, parents, branch refs, authors, timestamps, and messages are the history model.

See `docs/tech/rvc-semantic-storage.md` for the semantic storage schema and `docs/agent/rvc-mvp-slices.md` for implementation status.

## Shared Vs Local State

`index.json` is shared project state:

- `rvc_version`
- project `name`
- `sub_regions`
- sub-region names, relative `pos1`, relative `pos2`, and `size`

`local.json` is local clone state:

- `master_origin`
- semantic `sites[site_id].origin`
- semantic `active_site`
- optional legacy `local_selection`
- future UI/workspace state that should not affect collaborators

The Master Origin is intentionally local because the same RVC repo may be cloned into different Minecraft worlds or placed at different coordinates.

RVC projects are portable by default. A repo is not hard-bound to the world folder that created it; `rvc.json` stores shared structure/site content, while `local.json` decides how this clone maps the active site into a local world through dimension, origin, and optional world hint. Loading a project in another world should be non-destructive. Any future restore/paste into that world must be an explicit, confirmed action after the user sets or accepts the local placement.

## Coordinate Model

RVC uses a layered coordinate model:

- Sub-region block contents are stored relative to their region/project coordinate system.
- Shared sub-region definitions in `index.json` are relative to the local project coordinate system used when exporting.
- `local.json` supplies the local Master Origin for reconstructing world coordinates in a clone.
- Overlay placement origin must be computed from `index.json` plus local Master Origin, not guessed from paths or an arbitrary origin.

In current code, `RvcProjectService.readProjectAreaSelection(...)` reconstructs an `AreaSelection` by combining `index.json` relative sub-regions with `local.json` `master_origin`.

For semantic repos, `RvcCaptureEngine` maps `rvc.json` site regions and `local.json` site origin into world positions:

```text
world_pos = local_site_origin + project_relative_pos
project_relative_pos = rvc_chunk_coord * chunk_size + local_chunk_pos
```

An RVC chunk is a project-relative storage chunk, not a Minecraft chunk section.

Semantic sub-regions are non-owning tracking masks. The effective tracked area is the union of all sub-region volumes for the active site, so overlapping sub-regions are allowed and a shared block coordinate is captured once into the relevant RVC chunk. Export should preserve the user-defined sub-region names/bounds, even if they overlap.

## Semantic Capture Model

Current semantic capture flow:

1. Convert a Litematica selection into one MVP site named `main`.
2. Store region definitions in `rvc.json`.
3. Store the site origin and active site in local-only `local.json`.
4. Build a union tracked mask for every intersecting `16x16x16` RVC chunk.
5. Read tracked block states through `RvcWorldReader`.
6. Read block entity NBT where available.
7. Encode deterministic `.rvcchunk` bytes.
8. Hash with SHA-256 through Java `MessageDigest`.
9. Write object file only when missing.
10. Update `rvc.json` chunk refs and commit with JGit.

Manual semantic scan uses the same capture planning and hashing path, but resolves object IDs without writing object files and does not update `rvc.json`.

Current reader implementations:

- `RvcMinecraftWorldReader` reads a Minecraft `Level`.
- `RvcProjectService` routes semantic init/commit/scan capture to the integrated server's matching `ServerLevel` in singleplayer when available, and runs that capture on the server thread.
- Integration tests use fake `RvcWorldReader` implementations.

Current limitations:

- Scheduled block/fluid ticks are in the file format but not captured yet.
- Entities are reserved in the file format but not captured yet.
- Client-only multiplayer capture still falls back to client `Level`; dedicated server support requires a server-side RVC path.
- Dedicated-server support should investigate Servux first. Servux is a server-side Fabric mod for masa client mods, and current public metadata describes Litematica server-side save/paste support with full tile entity data.

## Structure Export Model

RVC stores `index.nbt` as a vanilla `StructureTemplate`.

Current export flow in `RvcStructure.createFromWorld(...)`:

1. Compute the enclosing cuboid of all valid tracked boxes.
2. Create a temporary schematic world for that volume.
3. Fill the whole temporary volume with `minecraft:structure_void`.
4. Copy blocks and block entities only for positions inside tracked boxes.
5. Call `StructureTemplate#fillFromWorld(...)`.

The `structure_void` step is essential. It prevents independent sub-region gaps from committing real world blocks and prevents restore/placement from overwriting unrelated world space as air.

## Git Model

`RvcRepository` owns low-level Git persistence:

- Uses JGit.
- Creates Git repos on first commit.
- Commits either semantic files (`rvc.json`, `objects/sha256`, `README.md`, `.gitignore`) or legacy files (`index.json`, `index.nbt`, `README.md`, `.gitignore`).
- Rejects detached-HEAD commits once a repo already has commits.
- Inserts custom commit headers:
  - `rvc-version 1`
  - `x-created-by rvc`
- Uses Minecraft player name as Git author/committer name.
- Uses `{uuid}@minecraft` as Git author/committer email.

`RvcProjectService` owns higher-level repo operations:

- Project path normalization and listing.
- Confirmed project deletion support through a validated recursive repository delete.
- Sub-region metadata read/write.
- Semantic project init and commit dispatch for newly-created repos.
- Manual active-site semantic scan and clean/dirty/unknown comparison.
- Semantic active-site update areas from the current Litematica selection.
- Commit history display.
- Branch memory for detached checkout history.
- Push/pull/remote helpers.
- Overlay/verifier loading.

## Project UI Model

The RVC project UI intentionally treats Git as the history source and renders commit metadata from JGit-derived `CommitInfo` objects.

Current UI invariants:

- History rows are compact and row-scrollable. They reserve a stable scrollbar gutter so text layout does not change when overflow appears.
- Scrollbars in RVC-owned panels are conditional: draw them only when content actually overflows. Commit history and metadata reserve content space for stable text layout; the project browser lets row backgrounds span under the scrollbar.
- Commit metadata is a selected-commit detail panel, not a full diff view. It shows Title, Author, optional Description, Date, Version, and Changes.
- `Changes` is a placeholder until semantic diffing exists.
- Subregion data remains project metadata in manifests, but it is not currently shown in the commit metadata panel.
- Sidebar action buttons are anchored from the bottom of the project content area so their bottom edge visually aligns with the commit history panel.
- The Project Editor page is the MVP editor for the active semantic site only. It edits shared project name/sub-region metadata in `rvc.json`, edits local site origin in ignored `local.json`, and leaves content chunk recapture to Save Version/commit.
- Project Browser can create empty semantic repos manually. These repos have `rvc.json`/`local.json` and `.git` but no commits, zero sub-regions, and no chunk refs until the user opens the project/editor, adds a sub-region, and saves the first version.
- Multi-site UI, site-level restore, and temporary mixed-version site preview remain future workflows. The manifest can represent sites, but the MVP editor intentionally hides that complexity.

## Overlay And Verifier Model

After commit, checkout, or pull, the user should be able to see the active RVC state.

Current overlay path:

- Load `index.nbt` through `SchematicHolder.getOrLoad(...)`.
- Create a normal `SchematicPlacement` at the resolved schematic world origin.
- Add placement through `DataManager.getSchematicPlacementManager()`.
- If client and schematic worlds are available, start `SchematicVerifier`.
- Update GUI tracking status from verifier completion.

This deliberately reuses Litematica placement/rendering/verifier behavior instead of a separate RVC renderer.

Semantic overlay/export/restore is not implemented yet. `RvcProjectService` currently blocks semantic checkout, pull restore, and overlay load with explicit errors instead of pretending the world was updated.

## Current Implementation Gaps To Respect

Do not assume the PRD is fully implemented. Known current gaps include:

- Legacy `Update areas` is not implemented.
- Semantic export/overlay/restore is not implemented.
- Semantic scan changes is implemented only for active-site manual scan/preflight data, not persistent stale tracking or dedicated-server authority.
- `Inspect` is a message stub, not a real view.
- Checkout/pull confirmation exists but still needs richer affected-region preview and better dirty/conflict handling.
- Pull only reports coarse `OK`/`FAILED`.
- Restore semantics are not yet server-authoritative for multiplayer.
- Entity cleanup/reconciliation semantics are not complete.
- Branch, diff, merge, and visual conflict workflows are future work.
