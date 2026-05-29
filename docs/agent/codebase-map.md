# Codebase Map

This project is a Fabric/Loom Java mod repo based on Litematica, with a newer Git-backed RVC feature added on top.

## Root And Build

- `build.gradle` - Fabric Loom build, Shadow plugin, Java 25 target, JGit shaded into the mod, integration-test source set.
- `gradle.properties` - mod metadata and Minecraft/Fabric/malilib versions. Current target is Minecraft `26.1.2`.
- `settings.gradle` - plugin repositories.
- `README.md` - short project description.
- `docs/` - PRD, corrections, TODOs, and technical notes.
- `run/` - local Minecraft run directory. RVC project repos are expected under `run/repos/` during local runs.
- `bin/`, `build/`, `.gradle/`, `.idea/` - generated or local tooling state.

## RVC Package

All new RVC classes should live under `src/main/java/me/zly2006/rvc/`.

- `RvcProjectService.java` - high-level application service: project creation, metadata read/write, commit history, checkout/pull/push helpers, overlay/verifier loading, project listing, branch bookkeeping.
- `RvcRepository.java` - low-level Git-backed repository writer: creates/updates core files, validates vanilla structure bytes, creates commits with JGit object insertion and RVC metadata headers.
- `RvcStructure.java` - exports tracked Litematica boxes from the live world into one vanilla `StructureTemplate`, filling untracked enclosing-volume gaps with `minecraft:structure_void`.
- `RvcSemanticRepository.java` - semantic repo init/commit, writes `rvc.json`, `local.json`, object refs, generated README, and Git ignore rules.
- `RvcManifest.java` - versioned semantic project manifest model for sites, regions, content settings, and chunk refs.
- `RvcLocalState.java` - local-only semantic placement state: active site and per-site origins.
- `RvcChunk.java` - immutable semantic chunk content model.
- `RvcChunkCodec.java` - deterministic `.rvcchunk` binary encoder/decoder.
- `RvcChunkStore.java` - SHA-256 content-addressed object paths and read/write helpers.
- `RvcChunkCoordinate.java` - project-relative RVC chunk coordinate keys.
- `RvcIntPosition.java` - simple integer position value object.
- `RvcCapturePlanner.java` - converts site regions into per-chunk tracked masks.
- `RvcCaptureEngine.java` - captures tracked chunks through `RvcWorldReader` and writes semantic objects.
- `RvcWorldReader.java` - abstraction for block state/block entity reads.
- `RvcMinecraftWorldReader.java` - Minecraft `Level` reader for canonical block states and block entity NBT.
- `RvcCanonicalNbt.java` - deterministic NBT writer used for block entity hashing.
- `GuiRvcProject.java` - RVC project screen: commit, push, pull, checkout branch/commit, inspect stub, tracking status, confirmations.
- `GuiRvcProjectManager.java` - lists valid RVC repos under the game run directory's `repos/` folder and opens project pages.
- `RvcPlayerIdentity.java` - converts the Minecraft player identity into a Git `PersonIdent`.

## Litematica Integration Points

These are legacy/upstream-style files. Keep edits minimal and stylistically consistent.

- `fi/dy/masa/litematica/gui/GuiMainMenu.java` - adds the RVC Project Manager button to the main Litematica menu.
- `fi/dy/masa/litematica/gui/GuiSchematicSave.java` - enables the Create RVC Project button for the standard save-from-selection flow.
- `fi/dy/masa/litematica/gui/GuiSchematicSaveBase.java` - implements the Create RVC Project button listener and calls `RvcProjectService.createProject(...)`.
- `fi/dy/masa/litematica/data/DataManager.java` - central access to selection and schematic placement managers.

## Core Litematica Areas

- `selection/` - `AreaSelection`, `Box`, selection modes, and selection manager. RVC uses selections as tracked sub-region definitions.
- `schematic/` - schematic formats, metadata, conversion, placement, transmission, verifier. RVC reuses vanilla structure loading and Litematica placement/verifier behavior.
- `schematic/placement/` - `SchematicPlacement`, placement manager, temporary schematic world helpers. RVC creates placements for active project overlays.
- `schematic/verifier/` - verifier used to compare client world vs schematic world and report clean/dirty status.
- `world/` - schematic world handling used by verifier/overlay logic.
- `render/` - schematic and overlay rendering.
- `scheduler/tasks/` - paste, save, fill, delete, count tasks. Relevant when implementing server-authoritative restore or paste behavior.
- `gui/` and `gui/widgets/` - malilib screens and widgets. RVC screens currently use simple manual drawing/buttons.
- `mixin/` - Minecraft hooks by feature area. Avoid touching unless the task requires behavior Minecraft does not expose directly.
- `materials/`, `util/`, `config/`, `event/`, `network/`, `compat/` - normal Litematica support subsystems.

## Resources

- `src/main/resources/fabric.mod.json` - Fabric mod metadata.
- `src/main/resources/mixins.litematica.json` - mixin config.
- `src/main/resources/litematica.accesswidener` - access widener.
- `src/main/resources/assets/litematica/lang/` - translations. RVC keys are present in `en_us.json` and `zh_cn.json`; other languages may not yet include RVC translations.
- `src/main/resources/assets/litematica/models`, `blockstates`, `textures`, `shaders` - rendering assets for Litematica fallback visuals and UI.

## Tests

- `src/integrationTest/java/me/zly2006/rvc/RvcRepositoryIntegrationTest.java` - executable integration test suite run via Gradle `integrationTest`.
- `src/integrationTest/java/me/zly2006/rvc/RvcSemanticStorageIntegrationTest.java` - semantic chunk/object/manifest/capture/repo integration coverage.
- `src/integrationTest/java/me/zly2006/rvc/IntegrationTestSupport.java` - small assertion/test runner helper.

Coverage is currently strongest for Git repository structure, metadata, checkout/branch history behavior, ignored `local.json`, sub-region metadata, semantic chunk encoding, semantic object reuse, fake-world capture, canonical Minecraft block state/NBT encoding, and semantic repo init/commit. Real client/server world mutation and GUI interactions are not covered yet.
