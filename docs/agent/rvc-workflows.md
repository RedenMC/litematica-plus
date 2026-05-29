# RVC Workflows

This file maps product flows to current source paths and implementation status.

## Create Project From Selection

Entry point:

- Litematica Save Schematic GUI.
- `GuiSchematicSave.shouldShowCreateRvcProjectButton()` returns true.
- `GuiSchematicSaveBase.ButtonListenerCreateRvcProject` calls `RvcProjectService.createProject(...)`.

Current behavior:

1. Requires player, client world, non-empty current area selection, and non-blank repository name.
2. Creates repo under `<game run dir>/rvc-projects/<project name>`.
3. Converts the selection into semantic site `main`.
4. Writes versioned `rvc.json` with site, region, and chunk refs.
5. Writes local-only `local.json` with active site and site origin.
6. Captures tracked blocks/block entities into content-addressed `.rvcchunk` objects.
7. Creates initial Git commit with message `init`.
8. Opens `GuiRvcProjectManager`.

Legacy note: older `index.json`/`index.nbt` repos remain readable/listable, but new projects use semantic storage.

Product note: `docs/TODO.md` says newly-created projects should probably open directly with tracking state or be highlighted in the manager.

## Project Manager

Entry points:

- Litematica main menu button: `GuiMainMenu.ButtonListenerChangeMenu.ButtonType.RVC_PROJECT_MANAGER`.
- After creating a project from Save Schematic.

Implementation:

- `GuiRvcProjectManager` lists valid repos found by `RvcProjectService.listProjects(...)`.
- A valid project must have `.git`, either semantic `rvc.json` or legacy `index.json`, and be openable by JGit.
- The project browser root is `<game run dir>/rvc-projects/`.
- The project browser `Delete Project` action shows a confirmation dialog, then recursively deletes only validated RVC repositories under `<game run dir>/rvc-projects/`.
- After deletion, the browser clears selection, refreshes the directory listing, and reports success or failure in the GUI.

## Commit

Entry point:

- `GuiRvcProject` Commit button.

Current behavior:

1. Blocks commits on detached HEAD and prompts to checkout `master` first.
2. Prompts for a non-blank message.
3. If the repo has `rvc.json`, reads semantic manifest and `local.json`.
4. Captures the active site's tracked chunks through `RvcMinecraftWorldReader`; in singleplayer this uses the integrated server's matching `ServerLevel` on the server thread.
5. Updates `rvc.json` chunk refs and writes missing `objects/sha256/**.rvcchunk` files.
6. Commits semantic files through `RvcSemanticRepository`/`RvcRepository`.
7. If the repo is legacy `index.json`/`index.nbt`, uses the older structure export path and reloads overlay/verifier.
8. If a semantic commit has no content changes, the service returns `null` and the GUI reports `Nothing to commit`.

Implementation notes:

- The method name `createStructureFromIndexSubRegionsOrFallbackToCurrentPositionUtilsGetValidBoxes(...)` is intentionally explicit about fallback behavior.
- Detached HEAD commits are rejected in `RvcRepository.requireCommittableHead(...)`.

## Scan Changes

Entry point:

- `GuiRvcProject` Scan changes button.

Current behavior:

Semantic `rvc.json` repos:

1. Requires a client world.
2. Resolves the active site from `local.json`.
3. Uses the integrated server's matching `ServerLevel` in singleplayer when available.
4. Hashes current tracked chunks through `RvcCaptureEngine.scanSite(...)`.
5. Does not write missing object files and does not update `rvc.json`.
6. Compares current hashes to the manifest chunk refs.
7. Reports clean, dirty, or unknown in the GUI.

Legacy `index.nbt` repos:

- The button reports that semantic projects are the supported scan path for now.

Known gaps:

- Scan result is not yet reused as enforced preflight for commit/checkout/pull/reset/merge.
- Scan state is not persisted and has no stale invalidation yet.
- Dedicated-server authoritative scan still needs server-side RVC support.

## Tracking Overlay

Entry points:

- After commit.
- After checkout.
- After pull.
- `GuiRvcProject.loadTrackingOverlay()`.

Current behavior:

Legacy `index.nbt` repos:

1. Remove prior RVC placement if tracked by the GUI.
2. Load `index.nbt` from the repository.
3. Resolve placement origin from tracked boxes reconstructed from `index.json` and `local.json`.
4. Add a `SchematicPlacement` named `RVC: <projectName>`.
5. Start `SchematicVerifier` if client and schematic worlds are available.
6. Display clean/dirty status after verifier completion.

Semantic `rvc.json` repos:

- Not implemented yet.
- Overlay/export/restore calls are explicitly blocked with an error.
- Next required slice is building a Litematica/vanilla structure view from semantic chunks.

Important limitation: this path refreshes overlay/verifier. Full server-authoritative world mutation semantics still need design and implementation where required by the product rule.

## Checkout Commit

Entry point:

- History row Checkout button in `GuiRvcProject`.

Current behavior:

Legacy `index.nbt` repos:

1. Requires a client world.
2. Checks tracked working-tree dirty state.
3. If dirty, prompts before `reset --hard`.
4. Prompts before checkout.
5. Uses JGit checkout to move the working tree to the selected commit, causing detached HEAD.
6. Reloads overlay/verifier for the checked-out state.
7. Stores the last active branch in Git config so history can remain scoped to the branch.

Semantic `rvc.json` repos:

- Checkout restore is blocked until semantic export/restore exists.

Known gaps:

- Preview should show target commit, affected sub-region count, bounds, and overwrite warning.
- Dirty/conflict/auth/non-fast-forward errors need more specific handling.
- The checkout operation should satisfy the visible in-game-state rule, including safe world restoration where supported.

## Checkout Branch Before Commit

Entry point:

- When the project page detects detached HEAD.
- When committing while detached.

Current behavior:

- Shows a "Checkout master" style path using `RvcProjectService.DEFAULT_BRANCH`, currently JGit's `master`.
- Can reset dirty tracked changes before checkout if confirmed.
- After checkout, commit prompt can continue.

Branch naming note: the default branch is `Constants.MASTER`. If this project moves to `main`, update service logic, tests, and translations together.

## Pull

Entry point:

- `GuiRvcProject` Pull button.

Current behavior:

Legacy `index.nbt` repos:

1. Requires a client world.
2. Checks tracked dirty state.
3. If dirty, prompts before `reset --hard`.
4. Prompts before pull.
5. Calls JGit `pull()`.
6. Reloads overlay/verifier from the updated working tree.
7. Reports `OK` or `FAILED`.

Semantic `rvc.json` repos:

- Pull restore is blocked until semantic export/restore exists.

Known gaps:

- Must avoid restoring into the world while the repo is conflicted.
- Needs specific messages for dirty tree, conflict, no remote, auth failure, non-fast-forward, and already-up-to-date cases.
- Needs richer preview/confirmation before any world-affecting restore.

## Push

Entry point:

- `GuiRvcProject` Push button.

Current behavior:

1. If no `origin` remote exists, prompts for remote URL.
2. Stores remote URL in Git config.
3. Pushes the remembered/history branch, not a detached HEAD commit.
4. Returns per-ref push statuses from JGit, though the GUI currently only shows a generic success message.

Known gaps:

- Display remote URL/current branch.
- Surface per-ref push statuses and SSH/auth errors cleanly.

## Remote URL

Entry point:

- `GuiRvcProject` Remote button.
- First-time `GuiRvcProject` Push flow when `origin` is not configured.

Current behavior:

1. The project page displays the configured `origin` URL or `not set`.
2. The Remote button opens a text input prefilled with the current `origin` URL.
3. Saving from the Remote button updates Git config only.
4. Saving from the first-time Push prompt updates Git config, then attempts push.
5. `RvcProjectService.setRemote(...)` writes `remote.origin.url`, `remote.origin.fetch`, and branch tracking config for the active or remembered local branch.
6. Blank remote URLs are rejected.

## Update Areas

Entry point:

- `GuiRvcProject` Update areas button.

Current behavior:

Semantic `rvc.json` repos:

1. Requires player, client world, current Litematica area selection, and non-detached HEAD.
2. Shows a basic confirmation with selected region count.
3. Keeps the existing local-only site origin from `local.json`.
4. Converts current selection boxes into active-site `rvc.json` regions relative to that origin.
5. Preserves existing region IDs when a box keeps the same name, or when only the box name changes and bounds stay the same.
6. Recaptures active-site tracked chunks from the current world; in singleplayer this uses integrated-server state.
7. Commits updated `rvc.json` and chunk objects with message `update areas`.

Legacy `index.nbt` repos:

- The button reports unsupported for now.

Known gaps:

- Needs richer preview of added/removed/renamed regions and bounds.
- Needs explicit local-origin update controls.
- Legacy `index.json` update areas is not implemented.
- Semantic overlay/verifier refresh is blocked until semantic export/overlay exists.

## Future Diff/Merge/Inspect

The PRD describes visual history inspection, branch creation, merge conflict resolution, component clustering, and verifier-like diff overlays. Current implementation does not include these beyond simple history rows and an Inspect message stub.

When implementing these features:

- Keep Git as the history source.
- Use `rvc.json` for semantic project/site/region/chunk comparisons.
- Use vanilla structure/Litematica loaders for legacy `index.nbt`.
- Reuse verifier/overlay concepts where practical.
- Resolve structural conflicts before block/content conflicts.
- Do not adopt PRD storage suggestions like `history.json` or `/data/` without re-approval in RVC docs.
