# RVC TODO

Last reviewed: 2026-05-07.

This document lists the remaining "do it later" work found during a review of the current RVC implementation. It focuses on RVC code and the Litematica UI paths touched by RVC.

Important rule: if an operation changes the active RVC state, it must not stop at Git or filesystem changes. It must also update the visible in-game state: world blocks, ghost overlay, and verifier state where available.

## P0 - Correctness And Safety

### Implement `Update areas`

Current state:

- The RVC project page has an `Update areas` button.
- `GuiRvcProject.updateAreas()` only shows `TODO`.
- Translation key `litematica.message.rvc_project.update_areas_todo` is still literally `TODO`.

Required behavior:

- Read the current Litematica area selection.
- Show a confirmation/preview of changed sub-regions.
- Update versioned `index.json` sub-region definitions.
- Update local-only `local.json` Master Origin if the user explicitly requests it.
- Recommit the updated area metadata and structure content.
- Refresh the in-game overlay/verifier after the update.

Relevant files:

- `src/main/java/me/zly2006/rvc/GuiRvcProject.java`
- `src/main/java/me/zly2006/rvc/RvcProjectService.java`
- `src/main/resources/assets/litematica/lang/en_us.json`
- `src/main/resources/assets/litematica/lang/zh_cn.json`

### Add Preview And Confirmation For World-Changing Operations

Current state:

- `Checkout` now updates the Git working tree and restores blocks into the current world.
- `Pull` now restores the working tree into the current world after pulling.
- These operations are destructive for tracked sub-regions and currently run immediately after a button click.

Required behavior:

- Show which commit/version will be applied.
- Show affected sub-region count and bounds.
- Warn that current world blocks inside tracked sub-regions will be overwritten.
- Require explicit confirmation before writing blocks.
- Keep the rule that untracked space between sub-regions must not be modified.

Relevant files:

- `src/main/java/me/zly2006/rvc/GuiRvcProject.java`
- `src/main/java/me/zly2006/rvc/RvcProjectService.java`
- `src/main/java/me/zly2006/rvc/RvcStructure.java`

### Handle Git Dirty State, Merge Conflicts, And Failed Pulls

Current state:

- `checkoutCommitToWorkingTree()` delegates to JGit checkout.
- `pull()` delegates to JGit pull and returns `OK` or `FAILED`.
- The GUI does not distinguish dirty working tree, merge conflict, auth failure, detached HEAD surprises, or non-fast-forward cases.
- `Pull` attempts game restoration after JGit reports success, but conflict states need explicit guarding and messaging.

Required behavior:

- Check `git.status()` before checkout and pull.
- Block destructive operations when the working tree has uncommitted RVC changes unless the user explicitly chooses a recovery path.
- Detect merge conflicts and refuse to restore into the world while the repository is conflicted.
- Display actionable error messages for dirty tree, conflict, no remote, auth failure, and non-fast-forward cases.

Relevant files:

- `src/main/java/me/zly2006/rvc/RvcProjectService.java`
- `src/main/java/me/zly2006/rvc/GuiRvcProject.java`

### Make In-Game Restore Server-Authoritative

Current state:

- Restore uses `Level#setBlock` on the current client world.
- This is enough for local/integrated testing paths but may not be server-authoritative on multiplayer servers.
- There is no permission check, command mode, or server-side apply path.

Required behavior:

- Decide the supported restore modes: single-player direct world write, integrated-server task, multiplayer command placement, or server-side RVC support.
- Refuse checkout/pull restore when the current world cannot be modified authoritatively.
- Report a clear message instead of silently creating client-only visual changes.

Relevant files:

- `src/main/java/me/zly2006/rvc/RvcProjectService.java`
- `src/main/java/me/zly2006/rvc/GuiRvcProject.java`

### Entity Restore Needs Cleanup Semantics

Current state:

- Schematic export masks entities outside tracked sub-regions.
- Restore can place entities from the schematic.
- Existing entities in the target sub-regions are not cleared or reconciled before restore.

Risk:

- Checkout/pull can duplicate entities or leave stale entities that were removed in the checked-out version.

Required behavior:

- Define whether RVC tracks entities by default.
- If entities are tracked, remove/reconcile existing entities inside tracked sub-regions before spawning restored entities.
- If entities are not tracked, disable entity placement during restore and document that behavior.

Relevant file:

- `src/main/java/me/zly2006/rvc/RvcProjectService.java`

## P1 - User Workflows

### Upgrade `Inspect` From A Message To A Real View

Current state:

- History rows have an `Inspect` button.
- It only displays the short commit id and message in the GUI message area.

Required behavior:

- Show commit id, parent ids, author, time, message, and RVC metadata.
- Show changed files and whether `index.json` or `index.nbt` changed.
- Show sub-region metadata at that commit.
- Provide entry points for diff and checkout preview.

Relevant file:

- `src/main/java/me/zly2006/rvc/GuiRvcProject.java`

### Add Diff Workflow

Current state:

- No diff button or diff view exists.
- The PRD mentions history inspection workflows, but no schematic diff is implemented.

Required behavior:

- Compare two commits.
- Show metadata changes from `index.json`.
- Show schematic/block changes at least as counts per sub-region.
- Ideally reuse Litematica verifier/overlay concepts to visualize changed blocks.

### Add Branch Awareness

Current state:

- The history list uses `git log --all`, so it can show commits from multiple refs and detached checkout history.
- The UI does not show the current branch, detached HEAD state, remote branch, or active commit.
- Checkout currently detaches HEAD when checking out a commit id.

Required behavior:

- Show current branch or detached HEAD state in the project page.
- Highlight the active commit.
- Provide a safe way to create a branch from a checked-out commit.
- Avoid confusing all-ref history with the active branch history.

Relevant files:

- `src/main/java/me/zly2006/rvc/RvcProjectService.java`
- `src/main/java/me/zly2006/rvc/GuiRvcProject.java`

### Improve Push/Pull UX

Current state:

- First push prompts for a remote URL.
- Push result statuses are collected but not displayed.
- Pull only reports `OK` or `FAILED`.
- SSH auth progress and failure details are not surfaced cleanly.

Required behavior:

- Display remote URL and current branch.
- Show per-ref push status.
- Show pull result details: fast-forward, merge, already up to date, conflict, failed.
- Consider a remote settings button instead of only prompting on first push.

Relevant file:

- `src/main/java/me/zly2006/rvc/RvcProjectService.java`

### Open Newly Created Project Directly With Tracking State

Current state:

- Creating an RVC project from the Save Schematic page creates the repo and then opens the project manager.
- The world already contains the selected build, but the RVC project page and tracking overlay are not opened immediately.

Required behavior:

- Decide whether create should open the project manager or the project page.
- If opening the project manager remains required, consider auto-selecting/highlighting the new project.
- If opening the project page, load the tracking overlay/verifier for the initial commit.

Relevant file:

- `src/main/java/fi/dy/masa/litematica/gui/GuiSchematicSaveBase.java`

## P1 - Data Model And Compatibility

### Remove Or Migrate Legacy `local_selection`

Current state:

- `local.json` can still contain `local_selection`.
- `readProjectAreaSelection()` falls back to `local_selection` when `index.json` sub-regions or `master_origin` are missing.

Risk:

- The fallback is useful for old local repos, but it keeps a second selection representation alive.

Required behavior:

- Add an explicit migration from `local_selection` to `index.json` sub-regions plus `master_origin`.
- After migration, keep fallback only for read-only recovery or remove it.

Relevant file:

- `src/main/java/me/zly2006/rvc/RvcProjectService.java`

### Add `index.json` Schema Validation And Migration

Current state:

- `index.json` has `rvc_version`, `name`, and `sub_regions`.
- Parsing is permissive and silently skips malformed sub-regions.
- There is no schema validation, migration path, or user-facing repair message.

Required behavior:

- Validate `rvc_version`.
- Validate sub-region names and coordinate arrays.
- Report invalid project metadata clearly in the GUI.
- Add migration hooks before bumping `rvc_version`.

Relevant file:

- `src/main/java/me/zly2006/rvc/RvcProjectService.java`

### Decide How To Store Large Binary Structure Data

Current state:

- `index.nbt` is committed directly into Git.

Risk:

- Large builds can make repositories heavy.
- GitHub sync may become slow or expensive.

Required behavior:

- Decide whether direct Git storage is acceptable for MVP.
- If not, evaluate Git LFS or chunked storage.
- Do not adopt PRD directory suggestions unless they fit the Git-backed model.

Relevant file:

- `src/main/java/me/zly2006/rvc/RvcRepository.java`

## P2 - UI Polish

### Replace Raw Text History With A Proper List Widget

Current state:

- `GuiRvcProject` manually draws history rows and manually places row buttons.

Required behavior:

- Use or create a list widget with scrolling.
- Keep buttons aligned for long messages and narrow screens.
- Add hover text for commit ids and actions.

Relevant file:

- `src/main/java/me/zly2006/rvc/GuiRvcProject.java`

### Add RVC Translations For All Supported Languages

Current state:

- RVC strings are added in `en_us.json` and `zh_cn.json`.
- Other language files do not have RVC-specific translations.

Required behavior:

- Add fallback-safe translations or ensure missing language keys degrade acceptably.
- At minimum, mirror English strings into other language files if this project expects complete key coverage.

Relevant directory:

- `src/main/resources/assets/litematica/lang/`

## P2 - Testing

### Add Real Game-State Integration Tests

Current state:

- Integration tests cover Git commits, metadata, checkout working tree behavior, and vanilla structure serialization.
- They do not verify real in-game block restoration because there is no dedicated fake/client world test harness yet.

Required behavior:

- Build a test harness that can assert world block changes without mocks.
- Test checkout restores tracked blocks.
- Test checkout does not touch untracked gaps between sub-regions.
- Test pull restores the world after a successful pull.
- Test tile entity and entity behavior once semantics are defined.

Relevant tests:

- `src/integrationTest/java/me/zly2006/rvc/RvcRepositoryIntegrationTest.java`

### Add GUI Interaction Tests

Current state:

- RVC GUI compiles and can be manually tested in-game.
- There are no automated GUI interaction tests.

Required behavior:

- Test create project button flow from Save Schematic page.
- Test Project Manager project list and Open button.
- Test Project page Commit, Pull, Push, Inspect, and Checkout flows.
- Test confirm dialogs once added.

## Non-RVC TODOs Observed During Review

These are not part of the current RVC task but appeared in the searched files:

- `GuiSchematicSave.java` has an existing `// TODO` around `SchematicSaveInfo`.
- `WidgetSchematicVerificationResult.java` has an existing `// FIXME`.

They should be tracked separately unless they block RVC workflows.
