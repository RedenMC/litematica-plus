# RVC Workflows

This file maps product flows to current source paths and implementation status.

## Create Project From Selection

Entry point:

- Litematica Save Schematic GUI.
- `GuiSchematicSave.shouldShowCreateRvcProjectButton()` returns true.
- `GuiSchematicSaveBase.ButtonListenerCreateRvcProject` calls `RvcProjectService.createProject(...)`.

Current behavior:

1. Requires player, client world, non-empty current area selection, and non-blank repository name.
2. Creates repo under `<game run dir>/repos/<project name>`.
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

## Commit

Entry point:

- `GuiRvcProject` Commit button.

Current behavior:

1. Blocks commits on detached HEAD and prompts to checkout `master` first.
2. Prompts for a non-blank message.
3. If the repo has `rvc.json`, reads semantic manifest and `local.json`.
4. Captures the active site's tracked chunks through `RvcMinecraftWorldReader`.
5. Updates `rvc.json` chunk refs and writes missing `objects/sha256/**.rvcchunk` files.
6. Commits semantic files through `RvcSemanticRepository`/`RvcRepository`.
7. If the repo is legacy `index.json`/`index.nbt`, uses the older structure export path and reloads overlay/verifier.

Implementation notes:

- The method name `createStructureFromIndexSubRegionsOrFallbackToCurrentPositionUtilsGetValidBoxes(...)` is intentionally explicit about fallback behavior.
- Detached HEAD commits are rejected in `RvcRepository.requireCommittableHead(...)`.
- Semantic no-op commits return `null` at service level and should get clearer GUI feedback in the next slice.

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

- Stub only: displays `litematica.message.rvc_project.update_areas_todo`.

Required direction from `docs/TODO.md`:

1. Read current Litematica area selection.
2. Preview changed sub-regions.
3. For semantic repos, update versioned `rvc.json` region definitions.
4. For legacy repos, update versioned `index.json` sub-region definitions.
5. Update `local.json` origin only if explicitly requested.
6. Recommit updated area metadata and content.
7. Refresh overlay/verifier when implemented for the repo format.

## Future Diff/Merge/Inspect

The PRD describes visual history inspection, branch creation, merge conflict resolution, component clustering, and verifier-like diff overlays. Current implementation does not include these beyond simple history rows and an Inspect message stub.

When implementing these features:

- Keep Git as the history source.
- Use `rvc.json` for semantic project/site/region/chunk comparisons.
- Use vanilla structure/Litematica loaders for legacy `index.nbt`.
- Reuse verifier/overlay concepts where practical.
- Resolve structural conflicts before block/content conflicts.
- Do not adopt PRD storage suggestions like `history.json` or `/data/` without re-approval in RVC docs.
