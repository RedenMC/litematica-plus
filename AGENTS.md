# Agent Guide

This repository is a Litematica fork with an RVC feature layer. Treat the legacy `fi.dy.masa.litematica` code as upstream-style Litematica code and the `me.zly2006.rvc` package as the newer RVC implementation.

## General Rules
When talking to me, sacrifice grammar for the sake of conciseness and shortness.

## First Read

When a task touches RVC behavior, read these in order:

1. `docs/prd-corrections.md` - highest-level product authority when it conflicts with the external PRD.
2. `docs/TODO.md` - current known gaps and priority order.
3. `docs/agent/rvc-architecture.md` - agent-maintained map of RVC goals, invariants, and files.
4. `docs/agent/rvc-workflows.md` - operation flows and current implementation status.
5. Relevant source files under `src/main/java/me/zly2006/rvc/`.

For broad repository orientation, start with `docs/agent/codebase-map.md`. For build and verification details, use `docs/agent/testing-and-build.md`.

## Non-Negotiable RVC Rules

- Git is the source of truth for history. Do not add `history.json` or another parallel commit ledger.
- RVC project repos are Git repos under the game run directory's `rvc-projects/` folder.
- Versioned project state belongs in `index.json` and `index.nbt`.
- Clone-local workspace state belongs in `local.json`; it must stay ignored by Git.
- `index.json` owns shared sub-region definitions. `local.json` owns the clone-local Master Origin.
- `index.nbt` is a vanilla structure file. RVC must not hand-roll palette/block/entity NBT parsing when existing Minecraft/Litematica loaders can be used.
- Independent sub-regions must remain explicit. When exporting to one enclosing vanilla structure volume, untracked gaps must be represented as `minecraft:structure_void`, not real world blocks and not air that overwrites unrelated space.
- Any operation that changes the active RVC state must update the visible in-game state as well as the Git/filesystem state: restore or refresh the schematic state, overlay, and verifier when available.
- Checkout/pull/restore operations are world-affecting or potentially destructive. Require clear confirmation paths and never modify untracked space between sub-regions.
- Prefer JGit for repository operations. Keep remote URL configuration in Git config, not RVC metadata.

## Code Style

- In legacy `fi.dy.masa.litematica` code, preserve the existing style: use `== false` for boolean checks and put the opening brace of `if`/`else` blocks on the next line.
- In newer `me.zly2006.rvc` code, use modern Java style: `!condition` is fine, and opening braces can stay on the same line when matching nearby code.
- Avoid broad refactors in upstream Litematica areas unless the task explicitly requires them.
- Keep RVC changes inside `me.zly2006.rvc` when possible. Only touch `fi.dy.masa.litematica` integration points for menu/buttons/placement/verifier hooks.
- It is fine to reference upstream Litematica patterns, but prefer current Minecraft APIs in new RVC code instead of copying deprecated upstream calls.

## Important Source Areas

- `src/main/java/me/zly2006/rvc/` - RVC GUI, Git service, repository writer, structure exporter, player identity.
- `src/main/java/fi/dy/masa/litematica/gui/GuiMainMenu.java` - RVC Project Manager entry point.
- `src/main/java/fi/dy/masa/litematica/gui/GuiSchematicSaveBase.java` and `GuiSchematicSave.java` - "Create RVC project" flow from a Litematica selection.
- `src/main/java/fi/dy/masa/litematica/selection/` - area selections and sub-region boxes.
- `src/main/java/fi/dy/masa/litematica/schematic/placement/` - schematic placement and temporary schematic world helpers.
- `src/main/java/fi/dy/masa/litematica/schematic/verifier/` - verifier used for clean/dirty tracking.
- `src/main/resources/assets/litematica/lang/` - translations; RVC keys currently exist in `en_us.json` and `zh_cn.json`.
- `src/integrationTest/java/me/zly2006/rvc/` - current integration coverage for RVC repo semantics.

## Build And Test

- Java target: 25.
- Minecraft/Fabric versions are in `gradle.properties`.
- Run the RVC integration suite with `./gradlew integrationTest`.
- Run the full verification lifecycle with `./gradlew check`.
- If dependency resolution fails because of network restrictions, ask for approval before rerunning with network access.

## Current Product Priorities

Use `docs/TODO.md` as the live priority list. The highest-risk areas are:

- Implementing `Update areas`.
- Adding stronger preview/confirmation and dirty/conflict handling around checkout/pull/reset flows.
- Making in-game restore server-authoritative or explicitly refusing unsupported multiplayer paths.
- Defining entity cleanup/restore semantics.
- Replacing the simple history display/Inspect stub with real branch/diff/history views.

## Documentation Map

- `docs/agent/codebase-map.md` - repository and subsystem map.
- `docs/agent/rvc-architecture.md` - RVC data model, authority order, invariants, and source roles.
- `docs/agent/rvc-workflows.md` - create/commit/checkout/pull/push/update-area workflows.
- `docs/agent/testing-and-build.md` - Gradle tasks, environment assumptions, and test coverage notes.
