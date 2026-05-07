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
import fi.dy.masa.malilib.util.nbt.NbtUtils;

public class RvcRepositoryIntegrationTest
{
    public static void main(String[] args) throws Exception
    {
        IntegrationTestSupport.run("init writes RVC files and creates the first Git commit", RvcRepositoryIntegrationTest::initWritesRvcFilesAndCreatesTheFirstGitCommit);
        IntegrationTestSupport.run("init commits an index schematic saved by SchematicaSchematic", RvcRepositoryIntegrationTest::initCommitsAnIndexSchematicSavedBySchematicaSchematic);
        IntegrationTestSupport.run("commit uses the supplied parent and history lists newest commits first", RvcRepositoryIntegrationTest::commitUsesSuppliedParentAndHistoryListsNewestFirst);
        IntegrationTestSupport.run("project service lists valid repositories and pushes to a remote", RvcRepositoryIntegrationTest::projectServiceListsValidRepositoriesAndPushesToRemote);
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
                IntegrationTestSupport.assertEquals(Set.of("README.md", "index.json", "index.schematic"), files, "committed files");
                IntegrationTestSupport.assertTrue(git.status().call().isClean(), "working tree should be clean after init commit");
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
        SharedConstants.setVersion(DetectedVersion.BUILT_IN);
        Bootstrap.bootStrap();

        Constructor<SchematicaSchematic> constructor = SchematicaSchematic.class.getDeclaredConstructor();
        constructor.setAccessible(true);
        SchematicaSchematic schematic = constructor.newInstance();
        LitematicaBlockStateContainer container = new LitematicaBlockStateContainer(1, 1, 1);
        container.set(0, 0, 0, Blocks.STONE.defaultBlockState());

        Field blocksField = SchematicaSchematic.class.getDeclaredField("blocks");
        blocksField.setAccessible(true);
        blocksField.set(schematic, container);

        Field sizeField = SchematicaSchematic.class.getDeclaredField("size");
        sizeField.setAccessible(true);
        sizeField.set(schematic, new BlockPos(1, 1, 1));

        return schematic;
    }
}
