package me.zly2006.rvc;

import java.nio.charset.StandardCharsets;
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
import net.minecraft.core.HolderGetter;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.DetectedVersion;
import net.minecraft.SharedConstants;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.IntTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.NbtAccounter;
import net.minecraft.nbt.NbtIo;
import net.minecraft.server.Bootstrap;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.levelgen.structure.templatesystem.StructureTemplate;
import org.eclipse.jgit.api.Git;
import org.eclipse.jgit.lib.Constants;
import org.eclipse.jgit.lib.ObjectId;
import org.eclipse.jgit.lib.Repository;
import org.eclipse.jgit.revwalk.RevCommit;
import org.eclipse.jgit.revwalk.RevTree;
import org.eclipse.jgit.revwalk.RevWalk;
import org.eclipse.jgit.storage.file.FileRepositoryBuilder;
import org.eclipse.jgit.treewalk.TreeWalk;
import fi.dy.masa.litematica.selection.AreaSelection;
import fi.dy.masa.malilib.util.data.json.JsonUtils;

public class RvcRepositoryIntegrationTest
{
    public static void main(String[] args) throws Exception
    {
        IntegrationTestSupport.run("init writes RVC files and creates the first Git commit", RvcRepositoryIntegrationTest::initWritesRvcFilesAndCreatesTheFirstGitCommit);
        IntegrationTestSupport.run("init commits an index structure saved as vanilla nbt", RvcRepositoryIntegrationTest::initCommitsAnIndexStructureSavedAsVanillaNbt);
        IntegrationTestSupport.run("commit uses the supplied parent and history lists newest commits first", RvcRepositoryIntegrationTest::commitUsesSuppliedParentAndHistoryListsNewestFirst);
        IntegrationTestSupport.run("project service lists valid repositories and pushes to a remote", RvcRepositoryIntegrationTest::projectServiceListsValidRepositoriesAndPushesToRemote);
        IntegrationTestSupport.run("local selection is stored in local.json and ignored by Git", RvcRepositoryIntegrationTest::localSelectionIsStoredInLocalJsonAndIgnoredByGit);
        IntegrationTestSupport.run("sub-regions are versioned in index json and master origin is local only", RvcRepositoryIntegrationTest::subRegionsAreVersionedInIndexJsonAndMasterOriginIsLocalOnly);
        IntegrationTestSupport.run("checkout updates the working tree while preserving visible history", RvcRepositoryIntegrationTest::checkoutUpdatesWorkingTreeWhilePreservingVisibleHistory);
        IntegrationTestSupport.run("commit history remains scoped to the branch after checkout", RvcRepositoryIntegrationTest::commitHistoryRemainsScopedToBranchAfterCheckout);
        IntegrationTestSupport.run("commit after checkout uses checked out commit as parent", RvcRepositoryIntegrationTest::commitAfterCheckoutUsesCheckedOutCommitAsParent);
    }

    private static void commitUsesSuppliedParentAndHistoryListsNewestFirst() throws Exception
    {
        Path repoDir = Files.createTempDirectory("rvc-commit-parent-");
        RvcPlayerIdentity player = new RvcPlayerIdentity("BuilderThree", UUID.fromString("123e4567-e89b-12d3-a456-426614174004"));

        RevCommit first = RvcRepository.commit(repoDir, "Parent Driven", createTinyStructureTemplate(), player, null, "init");
        RevCommit second = RvcRepository.commit(repoDir, "Parent Driven", createTinyStructureTemplate(), player, first.getId(), "update from world");

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

        RvcRepository.commit(validRepo, "Valid Project", createTinyStructureTemplate(), player, null, "init");
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
                "structure bytes".getBytes(StandardCharsets.UTF_8),
                new RvcPlayerIdentity("BuilderOne", playerUuid)
        );

        IntegrationTestSupport.assertFileContains(repoDir.resolve("index.json"), "\"rvc_version\": 1");
        IntegrationTestSupport.assertFileContains(repoDir.resolve("index.json"), "\"name\": \"Starter Build\"");
        IntegrationTestSupport.assertFileContains(repoDir.resolve("index.nbt"), "structure bytes");
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
                IntegrationTestSupport.assertEquals(Set.of(".gitignore", "README.md", "index.json", "index.nbt"), files, "committed files");
                IntegrationTestSupport.assertTrue(git.status().call().isClean(), "working tree should be clean after init commit");
            }
        }
    }

    private static void localSelectionIsStoredInLocalJsonAndIgnoredByGit() throws Exception
    {
        Path repoDir = Files.createTempDirectory("rvc-local-selection-");
        AreaSelection selection = createAreaSelectionFromJson("Stored Selection");

        RvcProjectService.writeLocalSelection(repoDir, selection);
        RvcRepository.commit(repoDir, "Local Selection", "structure bytes".getBytes(StandardCharsets.UTF_8), new RvcPlayerIdentity("BuilderFive", UUID.fromString("123e4567-e89b-12d3-a456-426614174006")), null, "init");

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

    private static void checkoutUpdatesWorkingTreeWhilePreservingVisibleHistory() throws Exception
    {
        Path repoDir = Files.createTempDirectory("rvc-checkout-");
        RvcPlayerIdentity player = new RvcPlayerIdentity("BuilderSix", UUID.fromString("123e4567-e89b-12d3-a456-426614174007"));
        RevCommit first = RvcRepository.commit(repoDir, "Checkout Project", "first structure".getBytes(StandardCharsets.UTF_8), player, null, "first");
        RevCommit second = RvcRepository.commit(repoDir, "Checkout Project", "second structure".getBytes(StandardCharsets.UTF_8), player, first.getId(), "second");

        List<RvcProjectService.CommitInfo> branchHistoryBeforeCheckout = RvcProjectService.listCommits(repoDir);
        RvcProjectService.checkoutCommitToWorkingTree(repoDir, first.getName());

        IntegrationTestSupport.assertFileContains(repoDir.resolve(RvcRepository.INDEX_STRUCTURE), "first structure");
        IntegrationTestSupport.assertEquals(first.getId(), RvcRepository.resolveHead(repoDir), "checkout should move HEAD to the selected commit");

        List<RvcProjectService.CommitInfo> history = RvcProjectService.listCommits(repoDir);
        IntegrationTestSupport.assertEquals(branchHistoryBeforeCheckout.stream().map(RvcProjectService.CommitInfo::id).toList(), history.stream().map(RvcProjectService.CommitInfo::id).toList(), "checkout should not reorder or replace branch commit history");
        IntegrationTestSupport.assertTrue(history.stream().anyMatch(commit -> commit.id().equals(first.getName())), "history should still show checked-out commit");
        IntegrationTestSupport.assertTrue(history.stream().anyMatch(commit -> commit.id().equals(second.getName())), "history should still show branch commits after detached checkout");
    }

    private static void commitHistoryRemainsScopedToBranchAfterCheckout() throws Exception
    {
        Path repoDir = Files.createTempDirectory("rvc-checkout-history-");
        RvcPlayerIdentity player = new RvcPlayerIdentity("BuilderEight", UUID.fromString("123e4567-e89b-12d3-a456-426614174009"));
        RevCommit first = RvcRepository.commit(repoDir, "Branch Scoped History", "first structure".getBytes(StandardCharsets.UTF_8), player, null, "first");
        RevCommit second = RvcRepository.commit(repoDir, "Branch Scoped History", "second structure".getBytes(StandardCharsets.UTF_8), player, first.getId(), "second");

        List<String> branchHistory = RvcProjectService.listCommits(repoDir).stream().map(RvcProjectService.CommitInfo::id).toList();

        RvcProjectService.checkoutCommitToWorkingTree(repoDir, first.getName());
        RevCommit detached = RvcRepository.commit(repoDir, "Branch Scoped History", "detached structure".getBytes(StandardCharsets.UTF_8), player, RvcRepository.resolveHead(repoDir), "detached experiment");

        List<String> historyAfterDetachedCommit = RvcProjectService.listCommits(repoDir).stream().map(RvcProjectService.CommitInfo::id).toList();
        IntegrationTestSupport.assertEquals(branchHistory, historyAfterDetachedCommit, "history should stay scoped to the branch selected before checkout");
        IntegrationTestSupport.assertTrue(historyAfterDetachedCommit.contains(second.getName()), "branch tip should remain visible");
        IntegrationTestSupport.assertTrue(historyAfterDetachedCommit.contains(detached.getName()) == false, "detached checkout commits must not appear in branch history");
    }

    private static void commitAfterCheckoutUsesCheckedOutCommitAsParent() throws Exception
    {
        Path repoDir = Files.createTempDirectory("rvc-checkout-commit-");
        RvcPlayerIdentity player = new RvcPlayerIdentity("BuilderSeven", UUID.fromString("123e4567-e89b-12d3-a456-426614174008"));
        RevCommit first = RvcRepository.commit(repoDir, "Checkout Commit Project", createTinyStructureTemplate(), player, null, "first");
        RvcRepository.commit(repoDir, "Checkout Commit Project", createTinyStructureTemplate(), player, first.getId(), "second");

        RvcProjectService.checkoutCommitToWorkingTree(repoDir, first.getName());
        RevCommit afterCheckout = RvcRepository.commit(repoDir, "Checkout Commit Project", createTinyStructureTemplate(), player, RvcRepository.resolveHead(repoDir), "after checkout");

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

    private static void initCommitsAnIndexStructureSavedAsVanillaNbt() throws Exception
    {
        Path repoDir = Files.createTempDirectory("rvc-structure-init-");
        StructureTemplate structure = createTinyStructureTemplate();

        RvcRepository.init(
                repoDir,
                "Vanilla Structure Backed",
                structure,
                new RvcPlayerIdentity("BuilderTwo", UUID.fromString("123e4567-e89b-12d3-a456-426614174003"))
        );

        Path structureFile = repoDir.resolve("index.nbt");
        IntegrationTestSupport.assertTrue(Files.size(structureFile) > 0, "index.nbt should be a real saved vanilla structure file");
        CompoundTag nbt = NbtIo.readCompressed(structureFile, NbtAccounter.unlimitedHeap());
        ListTag size = nbt.getListOrEmpty("size");
        IntegrationTestSupport.assertEquals(1, size.getIntOr(0, 0), "saved structure width");
        IntegrationTestSupport.assertEquals(1, size.getIntOr(1, 0), "saved structure height");
        IntegrationTestSupport.assertEquals(1, size.getIntOr(2, 0), "saved structure length");
        IntegrationTestSupport.assertTrue(nbt.contains("palette"), "saved structure should contain a palette");
        IntegrationTestSupport.assertTrue(nbt.contains("blocks"), "saved structure should contain block entries");

        try (Git git = Git.open(repoDir.toFile()))
        {
            Repository repository = git.getRepository();
            ObjectId headId = repository.resolve(Constants.HEAD);
            IntegrationTestSupport.assertNotNull(headId, "vanilla-structure-backed repo should have HEAD");
        }
    }

    private static StructureTemplate createTinyStructureTemplate()
    {
        SharedConstants.setVersion(DetectedVersion.BUILT_IN);
        Bootstrap.bootStrap();

        CompoundTag root = new CompoundTag();
        ListTag size = new ListTag();
        size.add(IntTag.valueOf(1));
        size.add(IntTag.valueOf(1));
        size.add(IntTag.valueOf(1));
        root.put("size", size);

        ListTag palette = new ListTag();
        CompoundTag stone = new CompoundTag();
        stone.putString("Name", "minecraft:stone");
        palette.add(stone);
        root.put("palette", palette);

        ListTag blocks = new ListTag();
        CompoundTag block = new CompoundTag();
        ListTag pos = new ListTag();
        pos.add(IntTag.valueOf(0));
        pos.add(IntTag.valueOf(0));
        pos.add(IntTag.valueOf(0));
        block.put("pos", pos);
        block.putInt("state", 0);
        blocks.add(block);
        root.put("blocks", blocks);
        root.put("entities", new ListTag());

        StructureTemplate template = new StructureTemplate();
        HolderGetter<Block> lookup = BuiltInRegistries.BLOCK;
        template.load(lookup, root);
        return template;
    }
}
