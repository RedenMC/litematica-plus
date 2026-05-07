package me.zly2006.rvc;

import java.nio.charset.StandardCharsets;
import java.lang.reflect.Constructor;
import java.lang.reflect.Field;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;
import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import com.google.gson.JsonPrimitive;
import net.minecraft.core.BlockPos;
import net.minecraft.DetectedVersion;
import net.minecraft.SharedConstants;
import net.minecraft.server.Bootstrap;
import net.minecraft.world.level.block.Blocks;
import org.eclipse.jgit.api.Git;
import org.eclipse.jgit.lib.Constants;
import org.eclipse.jgit.lib.ObjectId;
import org.eclipse.jgit.lib.Repository;
import org.eclipse.jgit.revwalk.RevCommit;
import org.eclipse.jgit.revwalk.RevTree;
import org.eclipse.jgit.revwalk.RevWalk;
import org.eclipse.jgit.storage.file.FileRepositoryBuilder;
import org.eclipse.jgit.treewalk.TreeWalk;
import fi.dy.masa.litematica.schematic.SchematicaSchematic;
import fi.dy.masa.litematica.schematic.container.LitematicaBlockStateContainer;
import fi.dy.masa.litematica.selection.AreaSelection;
import fi.dy.masa.litematica.selection.Box;
import fi.dy.masa.malilib.util.data.json.JsonUtils;
import fi.dy.masa.malilib.util.nbt.NbtUtils;

public class RvcRepositoryIntegrationTest
{
    public static void main(String[] args) throws Exception
    {
        IntegrationTestSupport.run("init writes RVC files and creates the first Git commit", RvcRepositoryIntegrationTest::initWritesRvcFilesAndCreatesTheFirstGitCommit);
        IntegrationTestSupport.run("init commits an index schematic saved by SchematicaSchematic", RvcRepositoryIntegrationTest::initCommitsAnIndexSchematicSavedBySchematicaSchematic);
        IntegrationTestSupport.run("commit uses the supplied parent and history lists newest commits first", RvcRepositoryIntegrationTest::commitUsesSuppliedParentAndHistoryListsNewestFirst);
        IntegrationTestSupport.run("project service lists valid repositories and pushes to a remote", RvcRepositoryIntegrationTest::projectServiceListsValidRepositoriesAndPushesToRemote);
        IntegrationTestSupport.run("local selection is stored in local.json and ignored by Git", RvcRepositoryIntegrationTest::localSelectionIsStoredInLocalJsonAndIgnoredByGit);
        IntegrationTestSupport.run("sub-regions are versioned in index json and master origin is local only", RvcRepositoryIntegrationTest::subRegionsAreVersionedInIndexJsonAndMasterOriginIsLocalOnly);
        IntegrationTestSupport.run("schematica export masks blocks outside tracked sub-regions", RvcRepositoryIntegrationTest::schematicaExportMasksBlocksOutsideTrackedSubRegions);
        IntegrationTestSupport.run("checkout updates the working tree while preserving visible history", RvcRepositoryIntegrationTest::checkoutUpdatesWorkingTreeWhilePreservingVisibleHistory);
        IntegrationTestSupport.run("commit after checkout uses checked out commit as parent", RvcRepositoryIntegrationTest::commitAfterCheckoutUsesCheckedOutCommitAsParent);
    }

    private static void commitUsesSuppliedParentAndHistoryListsNewestFirst() throws Exception
    {
        Path repoDir = Files.createTempDirectory("rvc-commit-parent-");
        RvcPlayerIdentity player = new RvcPlayerIdentity("BuilderThree", UUID.fromString("123e4567-e89b-12d3-a456-426614174004"));

        RevCommit first = RvcRepository.commit(repoDir, "Parent Driven", createTinySchematicaSchematic(), player, null, "init");
        RevCommit second = RvcRepository.commit(repoDir, "Parent Driven", createTinySchematicaSchematic(), player, first.getId(), "update from world");

        try (Git git = Git.open(repoDir.toFile()))
        {
            Repository repository = git.getRepository();

            try (RevWalk revWalk = new RevWalk(repository))
            {
                RevCommit parsedSecond = revWalk.parseCommit(repository.resolve(Constants.HEAD));
                IntegrationTestSupport.assertEquals(second.getId(), parsedSecond.getId(), "HEAD should point at the second commit");
                IntegrationTestSupport.assertEquals(1, parsedSecond.getParentCount(), "second commit should use the supplied parent");
                IntegrationTestSupport.assertEquals(first.getId(), parsedSecond.getParent(0).getId(), "second commit parent id");
            }
        }

        List<RvcProjectService.CommitInfo> history = RvcProjectService.listCommits(repoDir);
        IntegrationTestSupport.assertEquals(2, history.size(), "history size");
        IntegrationTestSupport.assertEquals(second.getName(), history.get(0).id(), "newest commit first");
        IntegrationTestSupport.assertEquals("update from world", history.get(0).message(), "newest commit message");
        IntegrationTestSupport.assertEquals(first.getName(), history.get(1).id(), "oldest commit second");
    }

    private static void projectServiceListsValidRepositoriesAndPushesToRemote() throws Exception
    {
        Path runDir = Files.createTempDirectory("rvc-run-");
        Path reposDir = runDir.resolve("repos");
        Path validRepo = reposDir.resolve("Valid Project");
        Path invalidRepo = reposDir.resolve("Not Git");
        RvcPlayerIdentity player = new RvcPlayerIdentity("BuilderFour", UUID.fromString("123e4567-e89b-12d3-a456-426614174005"));

        RvcRepository.commit(validRepo, "Valid Project", createTinySchematicaSchematic(), player, null, "init");
        Files.createDirectories(invalidRepo);

        List<RvcProjectService.Project> projects = RvcProjectService.listProjects(runDir);
        IntegrationTestSupport.assertEquals(1, projects.size(), "only valid git repositories should be listed");
        IntegrationTestSupport.assertEquals("Valid Project", projects.get(0).name(), "listed project name");
        IntegrationTestSupport.assertEquals(validRepo, projects.get(0).directory(), "listed project directory");

        Path remoteDir = Files.createTempDirectory("rvc-remote-").resolve("remote.git");
        try (Git ignored = Git.init().setBare(true).setDirectory(remoteDir.toFile()).call())
        {
            RvcProjectService.setRemote(validRepo, remoteDir.toUri().toString());
            RvcProjectService.push(validRepo);
        }

        try (Repository remoteRepository = new FileRepositoryBuilder().setGitDir(remoteDir.toFile()).build())
        {
            IntegrationTestSupport.assertNotNull(remoteRepository.resolve(Constants.HEAD), "remote should receive pushed HEAD");
        }
    }

    private static void initWritesRvcFilesAndCreatesTheFirstGitCommit() throws Exception
    {
        Path repoDir = Files.createTempDirectory("rvc-init-");
        UUID playerUuid = UUID.fromString("123e4567-e89b-12d3-a456-426614174000");

        RvcRepository.init(
                repoDir,
                "Starter Build",
                "schematic bytes".getBytes(StandardCharsets.UTF_8),
                new RvcPlayerIdentity("BuilderOne", playerUuid)
        );

        IntegrationTestSupport.assertFileContains(repoDir.resolve("index.json"), "\"rvc_version\": 1");
        IntegrationTestSupport.assertFileContains(repoDir.resolve("index.json"), "\"name\": \"Starter Build\"");
        IntegrationTestSupport.assertFileContains(repoDir.resolve("index.schematic"), "schematic bytes");
        IntegrationTestSupport.assertFileContains(repoDir.resolve("README.md"), "# Starter Build");

        try (Git git = Git.open(repoDir.toFile()))
        {
            Repository repository = git.getRepository();

            try (RevWalk revWalk = new RevWalk(repository))
            {
                ObjectId headId = repository.resolve(Constants.HEAD);
                IntegrationTestSupport.assertNotNull(headId, "HEAD should point at the initial commit");

                RevCommit commit = revWalk.parseCommit(headId);
                IntegrationTestSupport.assertEquals(0, commit.getParentCount(), "initial commit should not have parents");
                IntegrationTestSupport.assertEquals("BuilderOne", commit.getAuthorIdent().getName(), "author name");
                IntegrationTestSupport.assertEquals(playerUuid + "@minecraft", commit.getAuthorIdent().getEmailAddress(), "author email");
                IntegrationTestSupport.assertEquals("BuilderOne", commit.getCommitterIdent().getName(), "committer name");
                IntegrationTestSupport.assertEquals(playerUuid + "@minecraft", commit.getCommitterIdent().getEmailAddress(), "committer email");
                IntegrationTestSupport.assertEquals("init", commit.getShortMessage(), "commit message");

                String rawCommit = new String(repository.open(headId).getBytes(), StandardCharsets.UTF_8);
                IntegrationTestSupport.assertTrue(rawCommit.contains("\nrvc-version 1\n"), "commit should contain rvc-version metadata");
                IntegrationTestSupport.assertTrue(rawCommit.contains("\nx-created-by rvc\n"), "commit should contain x-created-by metadata");

                Set<String> files = listTreeFiles(repository, commit.getTree());
                IntegrationTestSupport.assertEquals(Set.of(".gitignore", "README.md", "index.json", "index.schematic"), files, "committed files");
                IntegrationTestSupport.assertTrue(git.status().call().isClean(), "working tree should be clean after init commit");
            }
        }
    }

    private static void localSelectionIsStoredInLocalJsonAndIgnoredByGit() throws Exception
    {
        Path repoDir = Files.createTempDirectory("rvc-local-selection-");
        AreaSelection selection = createAreaSelectionFromJson("Stored Selection");

        RvcProjectService.writeLocalSelection(repoDir, selection);
        RvcRepository.commit(repoDir, "Local Selection", "schematic bytes".getBytes(StandardCharsets.UTF_8), new RvcPlayerIdentity("BuilderFive", UUID.fromString("123e4567-e89b-12d3-a456-426614174006")), null, "init");

        IntegrationTestSupport.assertFileContains(repoDir.resolve(RvcProjectService.LOCAL_JSON), "\"local_selection\"");
        IntegrationTestSupport.assertFileContains(repoDir.resolve(".gitignore"), "/local.json");

        AreaSelection loaded = RvcProjectService.readLocalSelection(repoDir);
        IntegrationTestSupport.assertNotNull(loaded, "local selection should be readable");
        IntegrationTestSupport.assertEquals("Stored Selection", loaded.getName(), "local selection name");
        IntegrationTestSupport.assertEquals(1, loaded.getAllSubRegionBoxes().size(), "local selection boxes");

        try (Git git = Git.open(repoDir.toFile()))
        {
            Repository repository = git.getRepository();

            try (RevWalk revWalk = new RevWalk(repository))
            {
                RevCommit commit = revWalk.parseCommit(repository.resolve(Constants.HEAD));
                Set<String> files = listTreeFiles(repository, commit.getTree());
                IntegrationTestSupport.assertTrue(files.contains(RvcProjectService.LOCAL_JSON) == false, "local.json should not be committed");
                IntegrationTestSupport.assertTrue(git.status().call().isClean(), "local.json should be ignored");
            }
        }
    }

    private static void subRegionsAreVersionedInIndexJsonAndMasterOriginIsLocalOnly() throws Exception
    {
        Path repoDir = Files.createTempDirectory("rvc-index-subregions-");
        AreaSelection selection = createAreaSelectionFromJson("Relative Selection");

        RvcProjectService.writeProjectMetadataWithSubRegions(repoDir, "Relative Project", selection);

        IntegrationTestSupport.assertFileContains(repoDir.resolve(RvcRepository.INDEX_JSON), "\"sub_regions\"");
        IntegrationTestSupport.assertFileContains(repoDir.resolve(RvcRepository.INDEX_JSON), "\"name\": \"main\"");
        IntegrationTestSupport.assertFileContains(repoDir.resolve(RvcRepository.INDEX_JSON), "\"pos1\": [");
        IntegrationTestSupport.assertFileContains(repoDir.resolve(RvcProjectService.LOCAL_JSON), "\"master_origin\"");
        IntegrationTestSupport.assertTrue(Files.readString(repoDir.resolve(RvcRepository.INDEX_JSON)).contains("master_origin") == false, "master origin must not be versioned in index.json");

        AreaSelection restored = RvcProjectService.readProjectAreaSelection(repoDir);
        IntegrationTestSupport.assertNotNull(restored, "index/local should restore project area selection");
        IntegrationTestSupport.assertEquals("Relative Project", restored.getName(), "restored project selection name");
        IntegrationTestSupport.assertEquals(1, restored.getAllSubRegionBoxes().size(), "restored sub-region count");
    }

    private static AreaSelection createAreaSelectionFromJson(String name)
    {
        JsonObject selection = new JsonObject();
        JsonArray boxes = new JsonArray();
        JsonObject box = new JsonObject();

        box.add("name", new JsonPrimitive("main"));
        box.add("pos1", JsonUtils.blockPosToJson(new BlockPos(1, 2, 3)));
        box.add("pos2", JsonUtils.blockPosToJson(new BlockPos(2, 3, 4)));
        boxes.add(box);

        selection.add("name", new JsonPrimitive(name));
        selection.add("current", new JsonPrimitive("main"));
        selection.add("boxes", boxes);

        return AreaSelection.fromJson(selection);
    }

    private static void schematicaExportMasksBlocksOutsideTrackedSubRegions() throws Exception
    {
        SchematicaSchematic schematic = createLineSchematicaSchematic(3);
        Box first = createBox("first", new BlockPos(10, 64, 10), new BlockPos(10, 64, 10));
        Box third = createBox("third", new BlockPos(12, 64, 10), new BlockPos(12, 64, 10));

        schematic.maskOutsideWorldBoxes(new BlockPos(10, 64, 10), List.of(first, third));

        net.minecraft.nbt.CompoundTag nbt = schematic.writeToNBT();
        byte[] blocks = nbt.getByteArray("Blocks").orElse(new byte[0]);

        IntegrationTestSupport.assertEquals((byte) 1, blocks[0], "first tracked block should remain stone");
        IntegrationTestSupport.assertEquals((byte) 0, blocks[1], "middle untracked block should be masked to air");
        IntegrationTestSupport.assertEquals((byte) 1, blocks[2], "third tracked block should remain stone");
    }

    private static void checkoutUpdatesWorkingTreeWhilePreservingVisibleHistory() throws Exception
    {
        Path repoDir = Files.createTempDirectory("rvc-checkout-");
        RvcPlayerIdentity player = new RvcPlayerIdentity("BuilderSix", UUID.fromString("123e4567-e89b-12d3-a456-426614174007"));
        RevCommit first = RvcRepository.commit(repoDir, "Checkout Project", "first schematic".getBytes(StandardCharsets.UTF_8), player, null, "first");
        RevCommit second = RvcRepository.commit(repoDir, "Checkout Project", "second schematic".getBytes(StandardCharsets.UTF_8), player, first.getId(), "second");

        RvcProjectService.checkoutCommitToWorkingTree(repoDir, first.getName());

        IntegrationTestSupport.assertFileContains(repoDir.resolve(RvcRepository.INDEX_SCHEMATIC), "first schematic");
        IntegrationTestSupport.assertEquals(first.getId(), RvcRepository.resolveHead(repoDir), "checkout should move HEAD to the selected commit");

        List<RvcProjectService.CommitInfo> history = RvcProjectService.listCommits(repoDir);
        IntegrationTestSupport.assertTrue(history.stream().anyMatch(commit -> commit.id().equals(first.getName())), "history should still show checked-out commit");
        IntegrationTestSupport.assertTrue(history.stream().anyMatch(commit -> commit.id().equals(second.getName())), "history should still show branch commits after detached checkout");
    }

    private static void commitAfterCheckoutUsesCheckedOutCommitAsParent() throws Exception
    {
        Path repoDir = Files.createTempDirectory("rvc-checkout-commit-");
        RvcPlayerIdentity player = new RvcPlayerIdentity("BuilderSeven", UUID.fromString("123e4567-e89b-12d3-a456-426614174008"));
        RevCommit first = RvcRepository.commit(repoDir, "Checkout Commit Project", createTinySchematicaSchematic(), player, null, "first");
        RvcRepository.commit(repoDir, "Checkout Commit Project", createTinySchematicaSchematic(), player, first.getId(), "second");

        RvcProjectService.checkoutCommitToWorkingTree(repoDir, first.getName());
        RevCommit afterCheckout = RvcRepository.commit(repoDir, "Checkout Commit Project", createTinySchematicaSchematic(), player, RvcRepository.resolveHead(repoDir), "after checkout");

        try (Git git = Git.open(repoDir.toFile()))
        {
            Repository repository = git.getRepository();

            try (RevWalk revWalk = new RevWalk(repository))
            {
                RevCommit parsed = revWalk.parseCommit(repository.resolve(Constants.HEAD));
                IntegrationTestSupport.assertEquals(afterCheckout.getId(), parsed.getId(), "detached HEAD should move to the new commit");
                IntegrationTestSupport.assertEquals(first.getId(), parsed.getParent(0).getId(), "new detached commit should parent the checked-out commit");
            }
        }
    }

    private static Box createBox(String name, BlockPos pos1, BlockPos pos2)
    {
        Box box = new Box();
        box.setName(name);
        box.setPos1(pos1);
        box.setPos2(pos2);
        return box;
    }

    private static Set<String> listTreeFiles(Repository repository, RevTree tree) throws Exception
    {
        try (TreeWalk treeWalk = new TreeWalk(repository))
        {
            treeWalk.addTree(tree);
            treeWalk.setRecursive(true);

            Set<String> paths = new java.util.HashSet<>();
            while (treeWalk.next())
            {
                paths.add(treeWalk.getPathString());
            }

            return paths.stream().collect(Collectors.toUnmodifiableSet());
        }
    }

    private static void initCommitsAnIndexSchematicSavedBySchematicaSchematic() throws Exception
    {
        Path repoDir = Files.createTempDirectory("rvc-schematic-init-");
        SchematicaSchematic schematic = createTinySchematicaSchematic();

        RvcRepository.init(
                repoDir,
                "Schematica Backed",
                schematic,
                new RvcPlayerIdentity("BuilderTwo", UUID.fromString("123e4567-e89b-12d3-a456-426614174003"))
        );

        Path schematicFile = repoDir.resolve("index.schematic");
        IntegrationTestSupport.assertTrue(Files.size(schematicFile) > 0, "index.schematic should be a real saved schematic file");
        net.minecraft.nbt.CompoundTag nbt = NbtUtils.readNbtFromFile(schematicFile);
        IntegrationTestSupport.assertEquals((short) 1, nbt.getShortOr("Width", (short) 0), "saved schematic width");
        IntegrationTestSupport.assertEquals((short) 1, nbt.getShortOr("Height", (short) 0), "saved schematic height");
        IntegrationTestSupport.assertEquals((short) 1, nbt.getShortOr("Length", (short) 0), "saved schematic length");
        IntegrationTestSupport.assertTrue(nbt.contains("Blocks"), "saved schematic should contain block ids");
        IntegrationTestSupport.assertTrue(nbt.contains("Data"), "saved schematic should contain block metadata");

        try (Git git = Git.open(repoDir.toFile()))
        {
            Repository repository = git.getRepository();
            ObjectId headId = repository.resolve(Constants.HEAD);
            IntegrationTestSupport.assertNotNull(headId, "schematica-backed repo should have HEAD");
        }
    }

    private static SchematicaSchematic createTinySchematicaSchematic() throws Exception
    {
        return createLineSchematicaSchematic(1);
    }

    private static SchematicaSchematic createLineSchematicaSchematic(int width) throws Exception
    {
        SharedConstants.setVersion(DetectedVersion.BUILT_IN);
        Bootstrap.bootStrap();

        Constructor<SchematicaSchematic> constructor = SchematicaSchematic.class.getDeclaredConstructor();
        constructor.setAccessible(true);
        SchematicaSchematic schematic = constructor.newInstance();
        LitematicaBlockStateContainer container = new LitematicaBlockStateContainer(width, 1, 1);

        for (int x = 0; x < width; x++)
        {
            container.set(x, 0, 0, Blocks.STONE.defaultBlockState());
        }

        Field blocksField = SchematicaSchematic.class.getDeclaredField("blocks");
        blocksField.setAccessible(true);
        blocksField.set(schematic, container);

        Field sizeField = SchematicaSchematic.class.getDeclaredField("size");
        sizeField.setAccessible(true);
        sizeField.set(schematic, new BlockPos(width, 1, 1));

        return schematic;
    }
}
