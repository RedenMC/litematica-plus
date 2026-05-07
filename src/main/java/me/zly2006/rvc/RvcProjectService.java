package me.zly2006.rvc;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.FileAlreadyExistsException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Objects;
import javax.annotation.Nullable;
import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import org.apache.commons.lang3.tuple.Pair;
import org.eclipse.jgit.api.Git;
import org.eclipse.jgit.api.PullResult;
import org.eclipse.jgit.api.errors.GitAPIException;
import org.eclipse.jgit.lib.Constants;
import org.eclipse.jgit.lib.ObjectId;
import org.eclipse.jgit.lib.Repository;
import org.eclipse.jgit.lib.StoredConfig;
import org.eclipse.jgit.revwalk.RevCommit;
import org.eclipse.jgit.transport.PushResult;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.Level;

import fi.dy.masa.litematica.schematic.SchematicaSchematic;
import fi.dy.masa.litematica.selection.AreaSelection;
import fi.dy.masa.litematica.selection.Box;
import fi.dy.masa.litematica.util.PositionUtils;

public final class RvcProjectService
{
    public static final String REPOS_DIRECTORY = "repos";
    public static final String LOCAL_JSON = "local.json";
    public static final String LOCAL_SELECTION_KEY = "local_selection";
    public static final String DEFAULT_REMOTE_URL = "git@github.com:zly2006/rvc-v2-test.git";

    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();
    private static final DateTimeFormatter COMMIT_TIME_FORMAT = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss").withZone(ZoneId.systemDefault());

    private RvcProjectService()
    {
    }

    public static Result createProject(Path gameRunDirectory, String repositoryName, RvcPlayerIdentity player, Level world, AreaSelection selection, boolean ignoreEntities) throws Exception
    {
        Objects.requireNonNull(gameRunDirectory, "gameRunDirectory");
        Objects.requireNonNull(player, "player");
        Objects.requireNonNull(world, "world");
        Objects.requireNonNull(selection, "selection");

        String displayName = normalizeDisplayName(repositoryName);
        Path repositoryDirectory = repositoryDirectory(gameRunDirectory, displayName);

        if (Files.exists(repositoryDirectory))
        {
            throw new FileAlreadyExistsException(repositoryDirectory.toString());
        }

        Files.createDirectories(repositoryDirectory);
        writeLocalSelection(repositoryDirectory, selection);

        SchematicaSchematic schematic = createSchematicFromSelectionBoxes(world, getValidBoxes(selection), ignoreEntities);
        RevCommit commit = RvcRepository.commit(repositoryDirectory, displayName, schematic, player, null, "init");

        return new Result(repositoryDirectory, commit.getName());
    }

    public static RevCommit commitStoredSelectionWithCurrentSelectionFallback(Path repositoryDirectory, String projectName, RvcPlayerIdentity player, Level world, @Nullable AreaSelection currentSelectionFallback, boolean ignoreEntities, String message) throws Exception
    {
        Objects.requireNonNull(repositoryDirectory, "repositoryDirectory");
        Objects.requireNonNull(player, "player");
        Objects.requireNonNull(world, "world");
        Objects.requireNonNull(message, "message");

        ObjectId parent = RvcRepository.resolveHead(repositoryDirectory);
        SchematicaSchematic schematic = createSchematicFromLocalSelectionOrFallbackToCurrentPositionUtilsGetValidBoxes(repositoryDirectory, world, currentSelectionFallback, ignoreEntities);
        return RvcRepository.commit(repositoryDirectory, projectName, schematic, player, parent, normalizeCommitMessage(message));
    }

    public static void writeLocalSelection(Path repositoryDirectory, AreaSelection selection) throws IOException
    {
        Objects.requireNonNull(repositoryDirectory, "repositoryDirectory");
        Objects.requireNonNull(selection, "selection");

        JsonObject root = new JsonObject();
        root.add(LOCAL_SELECTION_KEY, selection.toJson());
        Files.createDirectories(repositoryDirectory);
        Files.writeString(repositoryDirectory.resolve(LOCAL_JSON), GSON.toJson(root), StandardCharsets.UTF_8);
    }

    @Nullable
    public static AreaSelection readLocalSelection(Path repositoryDirectory)
    {
        Objects.requireNonNull(repositoryDirectory, "repositoryDirectory");

        Path localFile = repositoryDirectory.resolve(LOCAL_JSON);

        if (Files.isRegularFile(localFile) == false)
        {
            return null;
        }

        JsonElement element;

        try
        {
            element = JsonParser.parseString(Files.readString(localFile, StandardCharsets.UTF_8));
        }
        catch (Exception e)
        {
            return null;
        }

        if (element != null && element.isJsonObject())
        {
            JsonObject root = element.getAsJsonObject();

            if (root.has(LOCAL_SELECTION_KEY) && root.get(LOCAL_SELECTION_KEY).isJsonObject())
            {
                return AreaSelection.fromJson(root.get(LOCAL_SELECTION_KEY).getAsJsonObject());
            }
        }

        return null;
    }

    public static List<Project> listProjects(Path gameRunDirectory) throws IOException
    {
        Path reposDirectory = reposDirectory(gameRunDirectory);

        if (Files.isDirectory(reposDirectory) == false)
        {
            return List.of();
        }

        List<Project> projects = new ArrayList<>();

        try (var stream = Files.list(reposDirectory))
        {
            for (Path candidate : stream.sorted(Comparator.comparing(path -> path.getFileName().toString())).toList())
            {
                if (isValidProjectRepository(candidate))
                {
                    projects.add(new Project(candidate.getFileName().toString(), candidate));
                }
            }
        }

        return List.copyOf(projects);
    }

    public static List<CommitInfo> listCommits(Path repositoryDirectory) throws IOException, GitAPIException
    {
        Objects.requireNonNull(repositoryDirectory, "repositoryDirectory");
        List<CommitInfo> commits = new ArrayList<>();

        try (Git git = Git.open(repositoryDirectory.toFile()))
        {
            for (RevCommit commit : git.log().call())
            {
                commits.add(new CommitInfo(
                        commit.getName(),
                        commit.getName().substring(0, Math.min(8, commit.getName().length())),
                        commit.getShortMessage(),
                        commit.getAuthorIdent().getName(),
                        COMMIT_TIME_FORMAT.format(commit.getAuthorIdent().getWhenAsInstant())
                ));
            }
        }

        return List.copyOf(commits);
    }

    public static boolean hasRemote(Path repositoryDirectory) throws IOException
    {
        return remoteOriginUrl(repositoryDirectory) != null;
    }

    @Nullable
    public static String remoteOriginUrl(Path repositoryDirectory) throws IOException
    {
        try (Git git = Git.open(repositoryDirectory.toFile()))
        {
            return git.getRepository().getConfig().getString("remote", "origin", "url");
        }
    }

    public static void setRemote(Path repositoryDirectory, String remoteUrl) throws IOException
    {
        Objects.requireNonNull(remoteUrl, "remoteUrl");

        if (remoteUrl.isBlank())
        {
            throw new IllegalArgumentException("Remote Git URL must not be blank");
        }

        try (Git git = Git.open(repositoryDirectory.toFile()))
        {
            StoredConfig config = git.getRepository().getConfig();
            config.setString("remote", "origin", "url", remoteUrl.trim());
            config.setString("remote", "origin", "fetch", "+refs/heads/*:refs/remotes/origin/*");
            config.save();
        }
    }

    public static List<String> push(Path repositoryDirectory) throws GitAPIException, IOException
    {
        List<String> statuses = new ArrayList<>();

        try (Git git = Git.open(repositoryDirectory.toFile()))
        {
            String branch = currentBranch(git.getRepository());

            for (PushResult result : git.push().setRemote("origin").add(branch).call())
            {
                result.getRemoteUpdates().forEach(update -> statuses.add(update.getRemoteName() + ": " + update.getStatus()));
            }
        }

        return List.copyOf(statuses);
    }

    public static String pull(Path repositoryDirectory) throws GitAPIException, IOException
    {
        try (Git git = Git.open(repositoryDirectory.toFile()))
        {
            PullResult result = git.pull().setRemote("origin").call();
            return result.isSuccessful() ? "OK" : "FAILED";
        }
    }

    public static Path reposDirectory(Path gameRunDirectory)
    {
        return gameRunDirectory.resolve(REPOS_DIRECTORY);
    }

    public static Path repositoryDirectory(Path gameRunDirectory, String projectName)
    {
        String displayName = normalizeDisplayName(projectName);
        return reposDirectory(gameRunDirectory).resolve(toDirectoryName(displayName)).normalize();
    }

    private static boolean isValidProjectRepository(Path candidate)
    {
        if (Files.isDirectory(candidate.resolve(".git")) == false || Files.isRegularFile(candidate.resolve(RvcRepository.INDEX_JSON)) == false)
        {
            return false;
        }

        try (Git ignored = Git.open(candidate.toFile()))
        {
            return true;
        }
        catch (Exception ignored)
        {
            return false;
        }
    }

    private static SchematicaSchematic createSchematicFromLocalSelectionOrFallbackToCurrentPositionUtilsGetValidBoxes(Path repositoryDirectory, Level world, @Nullable AreaSelection currentSelectionFallback, boolean ignoreEntities)
    {
        AreaSelection localSelection = readLocalSelection(repositoryDirectory);
        List<Box> boxes;

        if (localSelection != null)
        {
            boxes = getValidBoxes(localSelection);

            if (boxes.isEmpty() == false)
            {
                return createSchematicFromSelectionBoxes(world, boxes, ignoreEntities);
            }
        }

        boxes = fallbackToCurrentPositionUtilsGetValidBoxes(currentSelectionFallback);
        return createSchematicFromSelectionBoxes(world, boxes, ignoreEntities);
    }

    private static List<Box> getValidBoxes(AreaSelection selection)
    {
        return PositionUtils.getValidBoxes(selection);
    }

    private static List<Box> fallbackToCurrentPositionUtilsGetValidBoxes(@Nullable AreaSelection currentSelectionFallback)
    {
        if (currentSelectionFallback == null)
        {
            return List.of();
        }

        return PositionUtils.getValidBoxes(currentSelectionFallback);
    }

    private static SchematicaSchematic createSchematicFromSelectionBoxes(Level world, List<Box> boxes, boolean ignoreEntities)
    {
        Pair<BlockPos, BlockPos> corners = PositionUtils.getEnclosingAreaCorners(boxes);

        if (corners == null)
        {
            throw new IllegalArgumentException("RVC project requires a non-empty area selection");
        }

        BlockPos min = corners.getLeft();
        BlockPos size = corners.getRight().subtract(min).offset(1, 1, 1);
        return SchematicaSchematic.createFromWorld(world, min, size, ignoreEntities);
    }

    private static String normalizeDisplayName(String repositoryName)
    {
        if (repositoryName == null || repositoryName.isBlank())
        {
            return "rvc-project-" + Instant.now().toEpochMilli();
        }

        return repositoryName.trim();
    }

    private static String normalizeCommitMessage(String message)
    {
        String trimmed = message.trim();

        if (trimmed.isEmpty())
        {
            throw new IllegalArgumentException("Commit message must not be blank");
        }

        return trimmed;
    }

    private static String toDirectoryName(String displayName)
    {
        if (displayName.indexOf('/') >= 0 || displayName.indexOf('\\') >= 0)
        {
            throw new IllegalArgumentException("RVC repository name must not contain path separators: " + displayName);
        }

        return displayName;
    }

    private static String currentBranch(Repository repository) throws IOException
    {
        String fullBranch = repository.getFullBranch();

        if (fullBranch == null || fullBranch.startsWith(Constants.R_HEADS) == false)
        {
            throw new IOException("RVC repository is not on a local branch");
        }

        return fullBranch;
    }

    public record Project(String name, Path directory)
    {
    }

    public record CommitInfo(String id, String shortId, String message, String author, String time)
    {
    }

    public record Result(Path repositoryDirectory, String commitId)
    {
    }
}
