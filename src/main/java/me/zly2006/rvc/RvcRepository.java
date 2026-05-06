package me.zly2006.rvc;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Objects;
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
import fi.dy.masa.litematica.schematic.SchematicaSchematic;

public final class RvcRepository
{
    public static final int RVC_VERSION = 1;
    public static final String INDEX_JSON = "index.json";
    public static final String INDEX_SCHEMATIC = "index.schematic";
    public static final String README = "README.md";

    private RvcRepository()
    {
    }

    public static RevCommit init(Path directory, String name, byte[] schematicBytes, RvcPlayerIdentity player) throws IOException, GitAPIException
    {
        Objects.requireNonNull(directory, "directory");
        Objects.requireNonNull(schematicBytes, "schematicBytes");
        Objects.requireNonNull(player, "player");
        validateName(name);

        Files.createDirectories(directory);
        writeProjectMetadata(directory, name);
        Files.write(directory.resolve(INDEX_SCHEMATIC), schematicBytes);

        return commitInitialRvcRepository(directory, player);
    }

    public static RevCommit initFromSavedSchematic(Path directory, String name, Path schematicFile, RvcPlayerIdentity player) throws IOException, GitAPIException
    {
        Objects.requireNonNull(schematicFile, "schematicFile");
        return init(directory, name, Files.readAllBytes(schematicFile), player);
    }

    public static RevCommit init(Path directory, String name, SchematicaSchematic schematic, RvcPlayerIdentity player) throws IOException, GitAPIException
    {
        Objects.requireNonNull(directory, "directory");
        Objects.requireNonNull(schematic, "schematic");
        Objects.requireNonNull(player, "player");
        validateName(name);

        Files.createDirectories(directory);
        writeProjectMetadata(directory, name);

        if (schematic.writeToFile(directory, INDEX_SCHEMATIC, true) == false)
        {
            throw new IOException("Failed to write RVC schematic file: " + directory.resolve(INDEX_SCHEMATIC));
        }

        return commitInitialRvcRepository(directory, player);
    }

    private static RevCommit commitInitialRvcRepository(Path directory, RvcPlayerIdentity player) throws IOException, GitAPIException
    {
        try (Git git = openOrCreateGit(directory))
        {
            Repository repository = git.getRepository();

            if (repository.resolve(Constants.HEAD) != null)
            {
                throw new IOException("RVC repository already has commits: " + directory);
            }

            git.add()
                    .addFilepattern(INDEX_JSON)
                    .addFilepattern(INDEX_SCHEMATIC)
                    .addFilepattern(README)
                    .call();

            ObjectId commitId = createInitialCommit(repository, player, "init");

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
            throw new IllegalArgumentException("RVC schematic name must not be blank");
        }
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
        Files.writeString(directory.resolve(INDEX_JSON), createIndexJson(name), StandardCharsets.UTF_8);
        Files.writeString(directory.resolve(README), "# " + name + "\n\nCreated by RVC.\n", StandardCharsets.UTF_8);
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

    private static ObjectId createInitialCommit(Repository repository, RvcPlayerIdentity player, String message) throws IOException
    {
        PersonIdent identity = player.toPersonIdent();

        try (ObjectInserter inserter = repository.newObjectInserter())
        {
            DirCache dirCache = repository.readDirCache();
            ObjectId treeId = dirCache.writeTree(inserter);
            ObjectId commitId = inserter.insert(Constants.OBJ_COMMIT, createCommitBytes(treeId, identity, message));
            inserter.flush();
            updateHead(repository, commitId, identity, message);
            return commitId;
        }
    }

    private static byte[] createCommitBytes(ObjectId treeId, PersonIdent identity, String message)
    {
        String commit = "tree " + treeId.name() + "\n" +
                "author " + identity.toExternalString() + "\n" +
                "committer " + identity.toExternalString() + "\n" +
                "rvc-version " + RVC_VERSION + "\n" +
                "x-created-by rvc\n" +
                "\n" +
                message + "\n";

        return commit.getBytes(StandardCharsets.UTF_8);
    }

    private static void updateHead(Repository repository, ObjectId commitId, PersonIdent identity, String message) throws IOException
    {
        RefUpdate refUpdate = repository.updateRef(repository.getFullBranch());
        refUpdate.setNewObjectId(commitId);
        refUpdate.setRefLogIdent(identity);
        refUpdate.setRefLogMessage("commit (initial): " + message, false);

        RefUpdate.Result result = refUpdate.update();

        if (result != RefUpdate.Result.NEW && result != RefUpdate.Result.FAST_FORWARD && result != RefUpdate.Result.FORCED)
        {
            throw new IOException("Failed to update HEAD for RVC init commit: " + result);
        }
    }
}
