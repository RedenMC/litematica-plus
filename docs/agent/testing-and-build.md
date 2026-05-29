# Testing And Build

## Environment

- Build tool: Gradle wrapper.
- Mod platform: Fabric Loom.
- Java source/target: Java 25.
- Minecraft target: configured in `gradle.properties`, currently `26.1.2`.
- JGit is shaded via `build.gradle`.

## Useful Commands

Run RVC integration tests:

```bash
./gradlew integrationTest
```

If the system Java points at a JRE-style Java 25 without `javac`, use the local JDK:

```bash
JAVA_HOME=/home/arnav/.jdks/temurin-25.0.3 ./gradlew integrationTest
```

Run the full verification lifecycle:

```bash
./gradlew check
```

Build artifacts:

```bash
./gradlew build
```

Run a dev client if the Loom task is available:

```bash
./gradlew runClient
```

Network-dependent Gradle commands may fail in restricted environments when dependencies are not cached. If that happens, request approval before rerunning with network access.

## Current Test Coverage

`RvcRepositoryIntegrationTest` currently covers:

- Initial repo file creation and first Git commit.
- Vanilla `index.nbt` validation and compressed structure persistence.
- Rejection of raw non-NBT bytes.
- Commit parent behavior and newest-first history listing.
- Project listing under `repos/`.
- Push to a bare remote.
- Pushing the remembered branch while HEAD is detached.
- `local.json` ignored by Git.
- Versioned sub-regions in `index.json` and local Master Origin in `local.json`.
- Untracked gaps between independent sub-regions.
- Checkout working-tree behavior and visible branch-scoped history.
- Detached HEAD commit rejection.
- Dirty tracked changes and `reset --hard` recovery behavior.

`RvcSemanticStorageIntegrationTest` currently covers:

- Deterministic semantic chunk encoding and decode round trip.
- Content-addressed chunk object write/read and duplicate reuse.
- `rvc.json` and `local.json` round trips.
- Same-dimension multi-site manifest validity.
- Overlapping region rejection for MVP.
- Canonical Minecraft block state strings.
- Canonical block entity NBT with sorted keys and removed absolute position.
- Selection-to-semantic-site mapping.
- Untracked gaps in semantic chunks.
- Changed fake-world content only changes intersecting chunk refs.
- Local site origin mapping before world reads.
- Semantic repo init commits manifest/object files and excludes `local.json`.
- Semantic no-op commit does not move `HEAD`.
- Semantic update commit reuses unchanged chunk refs.

## Known Test Gaps

The current tests do not exercise:

- Real client/server world block restoration.
- Overlay rendering correctness.
- Schematic verifier clean/dirty behavior in a real client world.
- GUI button interaction flows.
- Pull conflict handling and auth failures.
- Semantic export/overlay/restore.
- Semantic manual scan changes.
- Integrated-server/dedicated-server authoritative capture.
- Scheduled tick capture.
- Entity capture/cleanup/restore semantics.
- Update-area, diff, merge, or branch creation workflows.

Add tests proportional to risk. For RVC service changes, extend the integration suite first when behavior can be verified without a full Minecraft client. For GUI-only behavior, at least keep logic factored so service behavior remains testable.
