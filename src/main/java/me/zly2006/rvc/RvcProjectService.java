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
import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.google.gson.JsonPrimitive;
import org.apache.commons.lang3.tuple.Pair;
import org.eclipse.jgit.api.Git;
import org.eclipse.jgit.api.PullResult;
import org.eclipse.jgit.api.ResetCommand;
import org.eclipse.jgit.api.Status;
import org.eclipse.jgit.api.errors.GitAPIException;
import org.eclipse.jgit.lib.Constants;
import org.eclipse.jgit.lib.ObjectId;
import org.eclipse.jgit.lib.Ref;
import org.eclipse.jgit.lib.Repository;
import org.eclipse.jgit.lib.StoredConfig;
import org.eclipse.jgit.revwalk.RevCommit;
import org.eclipse.jgit.transport.PushResult;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.levelgen.structure.templatesystem.StructureTemplate;

import fi.dy.masa.litematica.data.DataManager;
import fi.dy.masa.litematica.data.SchematicHolder;
import fi.dy.masa.litematica.schematic.LitematicaSchematic;
import fi.dy.masa.litematica.schematic.placement.SchematicPlacement;
import fi.dy.masa.litematica.schematic.verifier.SchematicVerifier;
import fi.dy.masa.litematica.selection.AreaSelection;
import fi.dy.masa.litematica.selection.Box;
import fi.dy.masa.litematica.util.PositionUtils;
import fi.dy.masa.litematica.world.SchematicWorldHandler;
import fi.dy.masa.litematica.world.WorldSchematic;
import fi.dy.masa.malilib.interfaces.ICompletionListener;

public final class RvcProjectService
{
    public static final String REPOS_DIRECTORY = "repos";
    public static final String LOCAL_JSON = "local.json";
    public static final String LOCAL_SELECTION_KEY = "local_selection";
    public static final String MASTER_ORIGIN_KEY = "master_origin";
    public static final String DEFAULT_BRANCH = Constants.MASTER;
    public static final String DEFAULT_REMOTE_URL = "git@github.com:zly2006/rvc-v2-test.git";

    private static final String GIT_CONFIG_SECTION = "rvc";
    private static final String GIT_CONFIG_HISTORY_BRANCH_KEY = "historyBranch";
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
        writeProjectMetadataWithSubRegions(repositoryDirectory, displayName, selection);

        StructureTemplate structure = createStructureFromSelectionBoxes(world, getValidBoxes(selection), ignoreEntities);
        RevCommit commit = RvcRepository.commit(repositoryDirectory, displayName, structure, player, null, "init");

        return new Result(repositoryDirectory, commit.getName());
    }

    public static RevCommit commitStoredSelectionWithCurrentSelectionFallback(Path repositoryDirectory, String projectName, RvcPlayerIdentity player, Level world, @Nullable AreaSelection currentSelectionFallback, boolean ignoreEntities, String message) throws Exception
    {
        Objects.requireNonNull(repositoryDirectory, "repositoryDirectory");
        Objects.requireNonNull(player, "player");
        Objects.requireNonNull(world, "world");
        Objects.requireNonNull(message, "message");

        StructureTemplate structure = createStructureFromIndexSubRegionsOrFallbackToCurrentPositionUtilsGetValidBoxes(repositoryDirectory, world, currentSelectionFallback, ignoreEntities);
        return RvcRepository.commit(repositoryDirectory, projectName, structure, player, null, normalizeCommitMessage(message));
    }

    public static void writeProjectMetadataWithSubRegions(Path repositoryDirectory, String projectName, AreaSelection selection) throws IOException
    {
        Objects.requireNonNull(repositoryDirectory, "repositoryDirectory");
        Objects.requireNonNull(selection, "selection");
        validateProjectName(projectName);

        Files.createDirectories(repositoryDirectory);

        BlockPos masterOrigin = selection.getEffectiveOrigin();
        JsonObject index = new JsonObject();
        JsonArray subRegions = new JsonArray();

        index.add("rvc_version", new JsonPrimitive(RvcRepository.RVC_VERSION));
        index.add("name", new JsonPrimitive(projectName.trim()));

        for (Box box : getValidBoxes(selection))
        {
            BlockPos pos1 = box.getPos1();
            BlockPos pos2 = box.getPos2();

            if (pos1 == null || pos2 == null)
            {
                continue;
            }

            BlockPos min = fi.dy.masa.litematica.util.PositionUtils.getMinCorner(pos1, pos2);
            BlockPos max = fi.dy.masa.litematica.util.PositionUtils.getMaxCorner(pos1, pos2);
            JsonObject subRegion = new JsonObject();
            subRegion.add("name", new JsonPrimitive(box.getName()));
            subRegion.add("pos1", blockPosToArray(min.subtract(masterOrigin)));
            subRegion.add("pos2", blockPosToArray(max.subtract(masterOrigin)));
            subRegion.add("size", blockPosToArray(max.subtract(min).offset(1, 1, 1)));
            subRegions.add(subRegion);
        }

        index.add("sub_regions", subRegions);
        Files.writeString(repositoryDirectory.resolve(RvcRepository.INDEX_JSON), GSON.toJson(index), StandardCharsets.UTF_8);
        writeLocalMasterOrigin(repositoryDirectory, masterOrigin);
    }

    @Nullable
    public static AreaSelection readProjectAreaSelection(Path repositoryDirectory)
    {
        Objects.requireNonNull(repositoryDirectory, "repositoryDirectory");

        JsonObject index = readJsonObject(repositoryDirectory.resolve(RvcRepository.INDEX_JSON));
        BlockPos masterOrigin = readLocalMasterOrigin(repositoryDirectory);

        if (index == null || masterOrigin == null || index.has("sub_regions") == false || index.get("sub_regions").isJsonArray() == false)
        {
            return readLocalSelection(repositoryDirectory);
        }

        JsonObject selection = new JsonObject();
        JsonArray boxes = new JsonArray();
        String projectName = index.has("name") ? index.get("name").getAsString() : repositoryDirectory.getFileName().toString();

        for (JsonElement element : index.get("sub_regions").getAsJsonArray())
        {
            if (element.isJsonObject() == false)
            {
                continue;
            }

            JsonObject subRegion = element.getAsJsonObject();
            BlockPos pos1 = readBlockPosArray(subRegion, "pos1");
            BlockPos pos2 = readBlockPosArray(subRegion, "pos2");

            if (pos1 == null || pos2 == null || subRegion.has("name") == false)
            {
                continue;
            }

            JsonObject box = new JsonObject();
            box.add("name", new JsonPrimitive(subRegion.get("name").getAsString()));
            box.add("pos1", blockPosToArray(pos1.offset(masterOrigin)));
            box.add("pos2", blockPosToArray(pos2.offset(masterOrigin)));
            boxes.add(box);
        }

        selection.add("name", new JsonPrimitive(projectName));
        selection.add("boxes", boxes);

        if (boxes.size() > 0)
        {
            selection.add("current", new JsonPrimitive(boxes.get(0).getAsJsonObject().get("name").getAsString()));
        }

        return AreaSelection.fromJson(selection);
    }

    public static void writeLocalMasterOrigin(Path repositoryDirectory, BlockPos masterOrigin) throws IOException
    {
        Objects.requireNonNull(repositoryDirectory, "repositoryDirectory");
        Objects.requireNonNull(masterOrigin, "masterOrigin");

        Files.createDirectories(repositoryDirectory);
        JsonObject root = readJsonObject(repositoryDirectory.resolve(LOCAL_JSON));

        if (root == null)
        {
            root = new JsonObject();
        }

        root.add(MASTER_ORIGIN_KEY, blockPosToArray(masterOrigin));
        Files.writeString(repositoryDirectory.resolve(LOCAL_JSON), GSON.toJson(root), StandardCharsets.UTF_8);
    }

    @Nullable
    public static BlockPos readLocalMasterOrigin(Path repositoryDirectory)
    {
        JsonObject root = readJsonObject(repositoryDirectory.resolve(LOCAL_JSON));

        if (root == null)
        {
            return null;
        }

        return readBlockPosArray(root, MASTER_ORIGIN_KEY);
    }

    public static void writeLocalSelection(Path repositoryDirectory, AreaSelection selection) throws IOException
    {
        Objects.requireNonNull(repositoryDirectory, "repositoryDirectory");
        Objects.requireNonNull(selection, "selection");

        JsonObject root = readJsonObject(repositoryDirectory.resolve(LOCAL_JSON));

        if (root == null)
        {
            root = new JsonObject();
        }

        root.add(LOCAL_SELECTION_KEY, selection.toJson());
        root.add(MASTER_ORIGIN_KEY, blockPosToArray(selection.getEffectiveOrigin()));
        Files.createDirectories(repositoryDirectory);
        Files.writeString(repositoryDirectory.resolve(LOCAL_JSON), GSON.toJson(root), StandardCharsets.UTF_8);
    }

    @Nullable
    public static AreaSelection readLocalSelection(Path repositoryDirectory)
    {
        Objects.requireNonNull(repositoryDirectory, "repositoryDirectory");

        JsonObject root = readJsonObject(repositoryDirectory.resolve(LOCAL_JSON));

        if (root != null)
        {
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
            Repository repository = git.getRepository();
            String historyBranch = historyBranchRef(repository);
            ObjectId historyStart = repository.resolve(historyBranch);

            if (historyStart == null)
            {
                return List.of();
            }

            for (RevCommit commit : git.log().add(historyStart).call())
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

    public static void checkoutCommitToWorkingTree(Path repositoryDirectory, String commitId) throws GitAPIException, IOException
    {
        Objects.requireNonNull(repositoryDirectory, "repositoryDirectory");
        Objects.requireNonNull(commitId, "commitId");

        if (commitId.isBlank())
        {
            throw new IllegalArgumentException("Commit id must not be blank");
        }

        try (Git git = Git.open(repositoryDirectory.toFile()))
        {
            rememberCurrentBranchForHistory(git.getRepository());
            git.checkout().setName(commitId.trim()).call();
        }
    }

    public static void checkoutBranchToWorkingTree(Path repositoryDirectory, String branchName) throws GitAPIException, IOException
    {
        Objects.requireNonNull(repositoryDirectory, "repositoryDirectory");
        Objects.requireNonNull(branchName, "branchName");

        if (branchName.isBlank())
        {
            throw new IllegalArgumentException("Branch name must not be blank");
        }

        try (Git git = Git.open(repositoryDirectory.toFile()))
        {
            git.checkout().setName(branchName.trim()).call();
            rememberCurrentBranchForHistory(git.getRepository());
        }
    }

    public static boolean isDetachedHead(Path repositoryDirectory) throws IOException
    {
        Objects.requireNonNull(repositoryDirectory, "repositoryDirectory");

        try (Git git = Git.open(repositoryDirectory.toFile()))
        {
            String fullBranch = git.getRepository().getFullBranch();
            return fullBranch == null || fullBranch.startsWith(Constants.R_HEADS) == false;
        }
    }

    public static String preferredCheckoutBranchName(Path repositoryDirectory) throws IOException
    {
        Objects.requireNonNull(repositoryDirectory, "repositoryDirectory");

        try (Git git = Git.open(repositoryDirectory.toFile()))
        {
            String branch = historyBranchRef(git.getRepository());

            if (branch.startsWith(Constants.R_HEADS))
            {
                return branch.substring(Constants.R_HEADS.length());
            }

            throw new IOException("RVC repository has no local branch to checkout");
        }
    }

    public static boolean hasUncommittedChanges(Path repositoryDirectory) throws GitAPIException, IOException
    {
        Objects.requireNonNull(repositoryDirectory, "repositoryDirectory");

        try (Git git = Git.open(repositoryDirectory.toFile()))
        {
            Status status = git.status().call();
            // reset --hard only returns tracked paths and the index to HEAD.
            return status.getAdded().isEmpty() == false ||
                    status.getChanged().isEmpty() == false ||
                    status.getConflicting().isEmpty() == false ||
                    status.getMissing().isEmpty() == false ||
                    status.getModified().isEmpty() == false ||
                    status.getRemoved().isEmpty() == false;
        }
    }

    public static void resetWorkingTreeToHead(Path repositoryDirectory) throws GitAPIException, IOException
    {
        Objects.requireNonNull(repositoryDirectory, "repositoryDirectory");

        try (Git git = Git.open(repositoryDirectory.toFile()))
        {
            git.reset().setMode(ResetCommand.ResetType.HARD).call();
        }
    }

    public static SchematicWorldRestore restoreWorkingTreeToSchematicWorld(Path repositoryDirectory, String projectName, @Nullable ClientLevel clientLevel, @Nullable ICompletionListener completionListener) throws IOException
    {
        Objects.requireNonNull(repositoryDirectory, "repositoryDirectory");
        Objects.requireNonNull(projectName, "projectName");

        int trackedBoxCount = readTrackedBoxes(repositoryDirectory).size();
        BlockPos schematicWorldOrigin = resolveSchematicWorldOrigin(repositoryDirectory);
        Path structureFile = repositoryDirectory.resolve(RvcRepository.INDEX_STRUCTURE);
        LitematicaSchematic schematic = reloadLitematicaSchematic(structureFile);

        if (schematic == null)
        {
            throw new IOException("Failed to load RVC structure: " + structureFile);
        }

        SchematicPlacement placement = SchematicPlacement.createFor(schematic, schematicWorldOrigin, "RVC: " + projectName, true, true);
        TrackingOverlay overlay = addTrackingOverlay(placement, clientLevel, completionListener);
        return new SchematicWorldRestore(schematicWorldOrigin, trackedBoxCount, overlay);
    }

    public static SchematicWorldRestore checkoutCommitToSchematicWorld(Path repositoryDirectory, String projectName, String commitId, @Nullable ClientLevel clientLevel, @Nullable ICompletionListener completionListener) throws GitAPIException, IOException
    {
        checkoutCommitToWorkingTree(repositoryDirectory, commitId);
        return restoreWorkingTreeToSchematicWorld(repositoryDirectory, projectName, clientLevel, completionListener);
    }

    public static SchematicWorldRestore checkoutBranchToSchematicWorld(Path repositoryDirectory, String projectName, String branchName, @Nullable ClientLevel clientLevel, @Nullable ICompletionListener completionListener) throws GitAPIException, IOException
    {
        checkoutBranchToWorkingTree(repositoryDirectory, branchName);
        return restoreWorkingTreeToSchematicWorld(repositoryDirectory, projectName, clientLevel, completionListener);
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
            String branch = pushBranchRef(git.getRepository());

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
            currentBranch(git.getRepository());
            PullResult result = git.pull().setRemote("origin").call();
            return result.isSuccessful() ? "OK" : "FAILED";
        }
    }

    public static TrackingOverlay loadTrackingOverlay(Path repositoryDirectory, String projectName, @Nullable ClientLevel clientLevel, @Nullable ICompletionListener completionListener) throws IOException
    {
        Objects.requireNonNull(repositoryDirectory, "repositoryDirectory");
        Objects.requireNonNull(projectName, "projectName");

        Path structureFile = repositoryDirectory.resolve(RvcRepository.INDEX_STRUCTURE);
        LitematicaSchematic schematic = reloadLitematicaSchematic(structureFile);

        if (schematic == null)
        {
            throw new IOException("Failed to load RVC structure: " + structureFile);
        }

        BlockPos origin = resolveSchematicWorldOrigin(repositoryDirectory);
        SchematicPlacement placement = SchematicPlacement.createFor(schematic, origin, "RVC: " + projectName, true, true);
        return addTrackingOverlay(placement, clientLevel, completionListener);
    }

    private static TrackingOverlay addTrackingOverlay(SchematicPlacement placement, @Nullable ClientLevel clientLevel, @Nullable ICompletionListener completionListener)
    {
        DataManager.getSchematicPlacementManager().addSchematicPlacement(placement, false);

        SchematicVerifier verifier = placement.getSchematicVerifier();
        WorldSchematic schematicWorld = SchematicWorldHandler.getSchematicWorld();
        boolean verifierStarted = false;

        if (clientLevel != null && schematicWorld != null)
        {
            verifier.startVerification(clientLevel, schematicWorld, placement, completionListener);
            verifierStarted = true;
        }

        return new TrackingOverlay(placement, verifier, verifierStarted);
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

    public static BlockPos resolveSchematicWorldOrigin(Path repositoryDirectory) throws IOException
    {
        List<Box> boxes = readTrackedBoxes(repositoryDirectory);
        Pair<BlockPos, BlockPos> corners = PositionUtils.getEnclosingAreaCorners(boxes);

        if (corners == null)
        {
            throw new IOException("RVC project has no tracked sub-regions");
        }

        return corners.getLeft();
    }

    private static List<Box> readTrackedBoxes(Path repositoryDirectory) throws IOException
    {
        AreaSelection selection = readProjectAreaSelection(repositoryDirectory);

        if (selection == null)
        {
            throw new IOException("RVC project has no readable area selection");
        }

        List<Box> boxes = getValidBoxes(selection);

        if (boxes.isEmpty())
        {
            throw new IOException("RVC project has no valid area boxes");
        }

        return boxes;
    }

    private static LitematicaSchematic reloadLitematicaSchematic(Path structureFile)
    {
        SchematicHolder holder = SchematicHolder.getInstance();

        for (LitematicaSchematic schematic : new ArrayList<>(holder.getAllSchematics()))
        {
            if (structureFile.equals(schematic.getFile()))
            {
                holder.removeSchematic(schematic);
            }
        }

        return holder.getOrLoad(structureFile);
    }

    private static StructureTemplate createStructureFromIndexSubRegionsOrFallbackToCurrentPositionUtilsGetValidBoxes(Path repositoryDirectory, Level world, @Nullable AreaSelection currentSelectionFallback, boolean ignoreEntities)
    {
        AreaSelection localSelection = readProjectAreaSelection(repositoryDirectory);
        List<Box> boxes;

        if (localSelection != null)
        {
            boxes = getValidBoxes(localSelection);

            if (boxes.isEmpty() == false)
            {
                return createStructureFromSelectionBoxes(world, boxes, ignoreEntities);
            }
        }

        boxes = fallbackToCurrentPositionUtilsGetValidBoxes(currentSelectionFallback);
        return createStructureFromSelectionBoxes(world, boxes, ignoreEntities);
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

    @Nullable
    private static JsonObject readJsonObject(Path file)
    {
        if (Files.isRegularFile(file) == false)
        {
            return null;
        }

        try
        {
            JsonElement element = JsonParser.parseString(Files.readString(file, StandardCharsets.UTF_8));
            return element != null && element.isJsonObject() ? element.getAsJsonObject() : null;
        }
        catch (Exception e)
        {
            return null;
        }
    }

    private static JsonArray blockPosToArray(BlockPos pos)
    {
        JsonArray arr = new JsonArray();
        arr.add(pos.getX());
        arr.add(pos.getY());
        arr.add(pos.getZ());
        return arr;
    }

    @Nullable
    private static BlockPos readBlockPosArray(JsonObject obj, String key)
    {
        if (obj.has(key) == false || obj.get(key).isJsonArray() == false)
        {
            return null;
        }

        JsonArray arr = obj.get(key).getAsJsonArray();

        if (arr.size() != 3)
        {
            return null;
        }

        return new BlockPos(arr.get(0).getAsInt(), arr.get(1).getAsInt(), arr.get(2).getAsInt());
    }

    private static StructureTemplate createStructureFromSelectionBoxes(Level world, List<Box> boxes, boolean ignoreEntities)
    {
        return RvcStructure.createFromWorld(world, boxes, ignoreEntities);
    }

    private static String normalizeDisplayName(String repositoryName)
    {
        if (repositoryName == null || repositoryName.isBlank())
        {
            return "rvc-project-" + Instant.now().toEpochMilli();
        }

        return repositoryName.trim();
    }

    private static void validateProjectName(String projectName)
    {
        if (projectName == null || projectName.isBlank())
        {
            throw new IllegalArgumentException("RVC project name must not be blank");
        }
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

    private static String historyBranchRef(Repository repository) throws IOException
    {
        String fullBranch = repository.getFullBranch();

        if (fullBranch != null && fullBranch.startsWith(Constants.R_HEADS))
        {
            rememberHistoryBranch(repository, fullBranch);
            return fullBranch;
        }

        String configuredBranch = repository.getConfig().getString(GIT_CONFIG_SECTION, null, GIT_CONFIG_HISTORY_BRANCH_KEY);

        if (configuredBranch != null && configuredBranch.isBlank() == false && repository.resolve(configuredBranch) != null)
        {
            return configuredBranch;
        }

        String defaultBranch = Constants.R_HEADS + DEFAULT_BRANCH;

        if (repository.resolve(defaultBranch) != null)
        {
            rememberHistoryBranch(repository, defaultBranch);
            return defaultBranch;
        }

        List<Ref> localBranches = repository.getRefDatabase().getRefsByPrefix(Constants.R_HEADS);

        if (localBranches.isEmpty() == false)
        {
            String fallbackBranch = localBranches.get(0).getName();
            rememberHistoryBranch(repository, fallbackBranch);
            return fallbackBranch;
        }

        return Constants.HEAD;
    }

    private static String pushBranchRef(Repository repository) throws IOException
    {
        String branch = historyBranchRef(repository);

        if (branch.startsWith(Constants.R_HEADS))
        {
            return branch;
        }

        throw new IOException("RVC repository has no local branch to push");
    }

    private static void rememberCurrentBranchForHistory(Repository repository) throws IOException
    {
        String fullBranch = repository.getFullBranch();

        if (fullBranch != null && fullBranch.startsWith(Constants.R_HEADS))
        {
            rememberHistoryBranch(repository, fullBranch);
        }
    }

    private static void rememberHistoryBranch(Repository repository, String fullBranch) throws IOException
    {
        StoredConfig config = repository.getConfig();

        if (fullBranch.equals(config.getString(GIT_CONFIG_SECTION, null, GIT_CONFIG_HISTORY_BRANCH_KEY)) == false)
        {
            config.setString(GIT_CONFIG_SECTION, null, GIT_CONFIG_HISTORY_BRANCH_KEY, fullBranch);
            config.save();
        }
    }

    public record Project(String name, Path directory)
    {
    }

    public record CommitInfo(String id, String shortId, String message, String author, String time)
    {
    }

    public record TrackingOverlay(SchematicPlacement placement, SchematicVerifier verifier, boolean verifierStarted)
    {
    }

    public record SchematicWorldRestore(BlockPos schematicWorldOrigin, int boxCount, TrackingOverlay overlay)
    {
    }

    public record Result(Path repositoryDirectory, String commitId)
    {
    }
}
