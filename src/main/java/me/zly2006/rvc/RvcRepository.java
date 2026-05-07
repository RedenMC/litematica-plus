package me.zly2006.rvc;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Objects;
import javax.annotation.Nullable;
import org.eclipse.jgit.api.Git;
import org.eclipse.jgit.api.errors.GitAPIException;
import org.eclipse.jgit.dircache.DirCache;
import org.eclipse.jgit.lib.Constants;
import org.eclipse.jgit.lib.ObjectId;
import org.eclipse.jgit.lib.ObjectInserter;
import org.eclipse.jgit.lib.PersonIdent;
import org.eclipse.jgit.lib.RefUpdate;
import org.eclipse.jgit.lib.Repository;
import org.eclipse.jgit.revwalk.RevCommit;
import org.eclipse.jgit.revwalk.RevWalk;
import org.eclipse.jgit.storage.file.FileRepositoryBuilder;
import net.minecraft.world.level.levelgen.structure.templatesystem.StructureTemplate;

public final class RvcRepository
{
    public static final int RVC_VERSION = 1;
    public static final String INDEX_JSON = "index.json";
    public static final String INDEX_STRUCTURE = "index.nbt";
    public static final String README = "README.md";
    public static final String GITIGNORE = ".gitignore";

    private RvcRepository()
    {
    }

    public static RevCommit init(Path directory, String name, byte[] structureBytes, RvcPlayerIdentity player) throws IOException, GitAPIException
    {
        return commit(directory, name, structureBytes, player, null, "init");
    }

    public static RevCommit initFromSavedStructure(Path directory, String name, Path structureFile, RvcPlayerIdentity player) throws IOException, GitAPIException
    {
        Objects.requireNonNull(structureFile, "structureFile");
        return init(directory, name, Files.readAllBytes(structureFile), player);
    }

    public static RevCommit init(Path directory, String name, StructureTemplate structure, RvcPlayerIdentity player) throws IOException, GitAPIException
    {
        return commit(directory, name, structure, player, null, "init");
    }

    public static RevCommit commit(Path directory, String name, byte[] structureBytes, RvcPlayerIdentity player, @Nullable ObjectId parent, String message) throws IOException, GitAPIException
    {
        Objects.requireNonNull(directory, "directory");
        Objects.requireNonNull(structureBytes, "structureBytes");
        Objects.requireNonNull(player, "player");
        Objects.requireNonNull(message, "message");
        validateName(name);
        requireCommittableHead(directory);

        Files.createDirectories(directory);
        writeProjectMetadata(directory, name);
        Files.write(directory.resolve(INDEX_STRUCTURE), structureBytes);

        return commitRvcRepository(directory, player, parent, message);
    }

    public static RevCommit commit(Path directory, String name, StructureTemplate structure, RvcPlayerIdentity player, @Nullable ObjectId parent, String message) throws IOException, GitAPIException
    {
        Objects.requireNonNull(directory, "directory");
        Objects.requireNonNull(structure, "structure");
        Objects.requireNonNull(player, "player");
        Objects.requireNonNull(message, "message");
        validateName(name);
        requireCommittableHead(directory);

        Files.createDirectories(directory);
        writeProjectMetadata(directory, name);

        RvcStructure.writeCompressed(structure, directory.resolve(INDEX_STRUCTURE));

        return commitRvcRepository(directory, player, parent, message);
    }

    @Nullable
    public static ObjectId resolveHead(Path directory) throws IOException
    {
        try (Repository repository = org.eclipse.jgit.storage.file.FileRepositoryBuilder.create(directory.resolve(".git").toFile()))
        {
            return repository.resolve(Constants.HEAD);
        }
    }

    private static RevCommit commitRvcRepository(Path directory, RvcPlayerIdentity player, @Nullable ObjectId parent, String message) throws IOException, GitAPIException
    {
        try (Git git = openOrCreateGit(directory))
        {
            Repository repository = git.getRepository();
            requireCommittableHead(repository);

            git.add()
                    .addFilepattern(INDEX_JSON)
                    .addFilepattern(INDEX_STRUCTURE)
                    .addFilepattern(README)
                    .addFilepattern(GITIGNORE)
                    .call();

            ObjectId commitId = createCommit(repository, player, parent, message);

            try (RevWalk revWalk = new RevWalk(repository))
            {
                return revWalk.parseCommit(commitId);
            }
        }
    }

    private static void validateName(String name)
    {
        if (name == null || name.isBlank())
        {
            throw new IllegalArgumentException("RVC project name must not be blank");
        }
    }

    private static void requireCommittableHead(Path directory) throws IOException
    {
        if (Files.isDirectory(directory.resolve(".git")) == false)
        {
            return;
        }

        try (Repository repository = new FileRepositoryBuilder().setGitDir(directory.resolve(".git").toFile()).build())
        {
            requireCommittableHead(repository);
        }
    }

    private static void requireCommittableHead(Repository repository) throws IOException
    {
        String fullBranch = repository.getFullBranch();

        if (fullBranch != null && fullBranch.startsWith(Constants.R_HEADS))
        {
            return;
        }

        if (repository.resolve(Constants.HEAD) == null)
        {
            return;
        }

        throw new IOException("RVC commit is disabled while HEAD is detached. Checkout master before committing.");
    }

    private static Git openOrCreateGit(Path directory) throws GitAPIException, IOException
    {
        if (Files.isDirectory(directory.resolve(".git")))
        {
            return Git.open(directory.toFile());
        }

        return Git.init().setDirectory(directory.toFile()).call();
    }

    private static void writeProjectMetadata(Path directory, String name) throws IOException
    {
        if (Files.isRegularFile(directory.resolve(INDEX_JSON)) == false)
        {
            Files.writeString(directory.resolve(INDEX_JSON), createIndexJson(name), StandardCharsets.UTF_8);
        }

        Files.writeString(directory.resolve(README), createReadme(name), StandardCharsets.UTF_8);
        Files.writeString(directory.resolve(GITIGNORE), "/" + RvcProjectService.LOCAL_JSON + "\n", StandardCharsets.UTF_8);
    }

    private static String createReadme(String name)
    {
        return "# " + name + "\n\n" +
                "Created by RVC.\n\n" +
                "## About this project\n\n" +
                "This repository stores a Minecraft structure project managed by RVC. It is designed to make redstone contraptions, builds, and other structure-based work easier to preserve, review, share, and collaborate on with standard Git tooling.\n\n" +
                "RVC keeps the project files in a normal Git repository, so every saved version can become a commit with an author, message, timestamp, and complete file history. This makes the structure easier to track over time and safer to publish to platforms such as GitHub.\n\n" +
                "## Why use RVC\n\n" +
                "- Version history: keep a clear timeline of meaningful structure changes.\n" +
                "- Collaboration: share the repository with other creators and review changes together.\n" +
                "- Backup safety: push the project to a remote Git host so the work is not tied to one local world or computer.\n" +
                "- Open workflow: use familiar Git commands and GitHub features without a custom storage format.\n" +
                "- Reproducible releases: tag stable versions of a build before making larger experiments.\n\n" +
                "## Project files\n\n" +
                "- `index.nbt` contains the vanilla structure data managed by RVC.\n" +
                "- `index.json` stores RVC metadata such as the project name and repository format version.\n" +
                "- `README.md` explains the purpose of this repository and the basic Git workflow.\n" +
                "- `local.json` stores local, uncommitted selection data and is ignored by Git.\n\n";
    }

    private static String createIndexJson(String name)
    {
        return "{\n" +
                "  \"rvc_version\": " + RVC_VERSION + ",\n" +
                "  \"name\": \"" + escapeJson(name) + "\"\n" +
                "}\n";
    }

    private static String escapeJson(String value)
    {
        StringBuilder builder = new StringBuilder(value.length() + 16);

        for (int i = 0; i < value.length(); ++i)
        {
            char c = value.charAt(i);

            switch (c)
            {
                case '"' -> builder.append("\\\"");
                case '\\' -> builder.append("\\\\");
                case '\b' -> builder.append("\\b");
                case '\f' -> builder.append("\\f");
                case '\n' -> builder.append("\\n");
                case '\r' -> builder.append("\\r");
                case '\t' -> builder.append("\\t");
                default ->
                {
                    if (c < 0x20)
                    {
                        builder.append(String.format("\\u%04x", (int) c));
                    }
                    else
                    {
                        builder.append(c);
                    }
                }
            }
        }

        return builder.toString();
    }

    private static ObjectId createCommit(Repository repository, RvcPlayerIdentity player, @Nullable ObjectId parent, String message) throws IOException
    {
        PersonIdent identity = player.toPersonIdent();

        try (ObjectInserter inserter = repository.newObjectInserter())
        {
            DirCache dirCache = repository.readDirCache();
            ObjectId treeId = dirCache.writeTree(inserter);
            ObjectId commitId = inserter.insert(Constants.OBJ_COMMIT, createCommitBytes(treeId, identity, parent, message));
            inserter.flush();
            updateHead(repository, commitId, identity, parent, message);
            return commitId;
        }
    }

    private static byte[] createCommitBytes(ObjectId treeId, PersonIdent identity, @Nullable ObjectId parent, String message)
    {
        StringBuilder commit = new StringBuilder(256);
        commit.append("tree ").append(treeId.name()).append('\n');

        if (parent != null)
        {
            commit.append("parent ").append(parent.name()).append('\n');
        }

        commit.append("author ").append(identity.toExternalString()).append('\n');
        commit.append("committer ").append(identity.toExternalString()).append('\n');
        commit.append("rvc-version ").append(RVC_VERSION).append('\n');
        commit.append("x-created-by rvc\n");
        commit.append('\n');
        commit.append(message).append('\n');

        return commit.toString().getBytes(StandardCharsets.UTF_8);
    }

    private static void updateHead(Repository repository, ObjectId commitId, PersonIdent identity, @Nullable ObjectId parent, String message) throws IOException
    {
        String fullBranch = repository.getFullBranch();
        String targetRef = fullBranch != null && fullBranch.startsWith(Constants.R_HEADS) ? fullBranch : Constants.HEAD;
        RefUpdate refUpdate = repository.updateRef(targetRef);
        refUpdate.setNewObjectId(commitId);
        refUpdate.setExpectedOldObjectId(parent != null ? parent : ObjectId.zeroId());
        refUpdate.setRefLogIdent(identity);
        refUpdate.setRefLogMessage("commit: " + message, false);

        RefUpdate.Result result = refUpdate.update();

        if (result != RefUpdate.Result.NEW && result != RefUpdate.Result.FAST_FORWARD)
        {
            throw new IOException("Failed to update HEAD for RVC commit: " + result);
        }
    }
}
