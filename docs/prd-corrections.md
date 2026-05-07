# RVC PRD Corrections

This document records corrections to the external "Schematic Version Control" PRD and clarifies which parts should guide the current RVC implementation.

The implementation authority is:

1. The instructions in this document.
2. `docs/tech/rvc.md`.
3. Existing implementation constraints in the Litematica codebase.

Do not treat the external PRD as authoritative when it conflicts with these corrections.

## Corrections

### 1. `history.json` Is a Wrong Design

The PRD's proposed `history.json` metadata log is incorrect for RVC.

RVC is intentionally Git-backed. Commit history, commit IDs, parent relationships, author, timestamp, branch pointers, and messages should come from Git itself. Duplicating this into `history.json` would create two competing sources of truth and introduce consistency bugs.

Required direction:

- Use Git commits as the permanent history log.
- Use JGit to read history for UI display.
- Store RVC-specific commit metadata in Git commit objects where needed.
- Do not add a separate `history.json` history ledger.

### 2. Ignore PRD Directory Structure Advice

Do not follow any PRD recommendation about repository directory layout unless it is explicitly re-approved in RVC docs or implementation notes.

The external PRD proposes files such as `history.json` and a `/data/` directory. That structure is not authoritative for this project.

Current RVC repository structure is intentionally simple and Git-native:

- `index.json`: versioned RVC project metadata.
- `index.schematic`: versioned schematic content saved with `SchematicaSchematic`.
- `README.md`: generated project description.
- `.gitignore`: Git ignore rules for local-only files.
- `local.json`: local-only state, ignored by Git.

Future structure changes should be designed from the Git-backed model, not copied from the PRD.

### 3. `local.json`, `index.json`, Sub-Regions, and Master Origin

`local.json` must never be synchronized. It is local clone state only and must remain ignored by Git.

Reason: different clones of the same RVC repository may be used in different Minecraft worlds or at different physical positions. The same schematic project may therefore need a different Master Origin per clone.

Responsibilities:

- `index.json` records versioned project metadata that must be shared between clones.
- `index.json` must record all sub-region definitions using coordinates relative to the project coordinate system.
- `local.json` records local-only workspace information.
- `local.json` stores the Master Origin for this clone.
- `local.json` may store local UI/workspace state that should not affect collaborators.
- `local.json` must be listed in `.gitignore`.

Important consequence:

Sub-region layout is shared project data and belongs in `index.json`, not `local.json`. Master Origin is per-clone local data and belongs in `local.json`, not `index.json`.

The current implementation stores `local_selection` in `local.json`. This was added to prevent commits from depending on the currently selected in-game area. That is useful as a short-term safety measure, but the final design should move shared sub-region layout into `index.json` while keeping clone-specific Master Origin in `local.json`.

### 4. Current Sub-Region Serialization Needs Fixing

Status: need fixing.

The PRD correctly identifies that tracked areas must be explicit sub-regions, but the current implementation does not yet serialize them in the final desired model.

Current behavior:

- The project captures the current Litematica `AreaSelection`.
- Commit code reads a saved local selection first.
- It falls back to the current in-game selection only when local selection data is unavailable.
- All valid boxes are collapsed into one enclosing cuboid before saving `index.schematic`.

Problems:

- Sub-region metadata is not stored in versioned `index.json`.
- Per-sub-region identity and relative positions are not preserved as first-class project metadata.
- Collapsing all boxes into one enclosing cuboid can include blocks between independent sub-regions.
- The current model cannot accurately support future branch, diff, merge, or update-area workflows.

Required direction:

- Store sub-region names, sizes, and relative positions in `index.json`.
- Keep sub-region definitions versioned with the project.
- Avoid treating multiple independent sub-regions as one enclosing cuboid in the long-term design.
- Keep fallback code explicit and visibly named when fallback to current selection is necessary.

### 5. Repeated Directory Structure Warning

Do not follow PRD directory structure advice.

This is intentionally repeated because multiple PRD sections imply non-Git-native storage, especially `history.json` and `/data/`. RVC should not adopt those suggestions by default.

Any future storage change must answer:

- Why Git's native object/history model is not enough.
- Whether the data is shared project state or clone-local workspace state.
- Whether the data belongs in Git, in `index.json`, in `local.json`, or in generated/transient files.

### 6. Post-Commit Tracking and Verification Needs Fixing

Status: need fixing.

The PRD describes a useful post-commit workflow: after a commit, the committed state should be visible as a persistent ghost overlay and the world should be compared against that committed state.

Current behavior:

- A commit writes `index.schematic` and creates a Git commit.
- The RVC project GUI refreshes the commit history.
- No ghost overlay is loaded.
- No verifier state is enabled.
- No clean/dirty indicator exists.

Required direction:

- After commit, load or update a ghost overlay representing the committed schematic state.
- Reuse Litematica verifier/rendering behavior where practical.
- Provide a clean/dirty signal comparing the current world against the last committed state.
- Make overlay visibility follow existing Litematica rendering controls where possible.

### 7. History UI Actions Need Fixing

Status: need fixing.

The PRD expects history entries to support workflows such as checkout and inspection. The current RVC project page only displays a flat commit list.

Current behavior:

- The project page lists commit ID, message, author, and timestamp.
- Top-level buttons include `Update areas`, `Commit`, `Push`, and `Pull`.
- Individual history rows do not expose actions.

Required direction:

- Add row-level actions for history entries when the underlying operations are implemented.
- At minimum, design space for future `Checkout`, `Inspect`, and possibly `Diff` actions.
- Do not implement destructive world-changing actions without preview and safety checks.
- Do not confuse Git push/pull with PRD history inspection features.

### 8. Remote Sync Guidance from the PRD Should Be Ignored

Do not follow the PRD's instruction to hold off on remotes.

RVC is Git-backed, and remote synchronization is an important part of the MVP workflow. The current implementation may include push and pull UI, including first-time remote URL configuration.

Required direction:

- Keep push/pull as valid RVC project operations.
- Use JGit for remote operations.
- Keep remote URL configuration local to the Git repository config.
- Do not let the PRD's "hold off on Remotes & Cloning" note block remote sync work.

## Summary of Current Fix Priorities

The following implemented areas are intentionally accepted:

- Git is the source of truth for history.
- No `history.json`.
- Git-native commit history UI.
- Push and pull may exist in the MVP.
- `local.json` must stay ignored and local-only.

The following areas need fixing:

- Move shared sub-region definitions into versioned `index.json`.
- Keep Master Origin in local-only `local.json`.
- Stop relying on one enclosing cuboid as the long-term storage model.
- Add post-commit ghost overlay and clean/dirty verification.
- Add meaningful history row actions once safe checkout/inspect workflows exist.
