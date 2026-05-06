package me.zly2006.rvc;

import java.nio.file.FileAlreadyExistsException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import org.apache.commons.lang3.tuple.Pair;
import java.util.Objects;
import org.eclipse.jgit.revwalk.RevCommit;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.Level;

import fi.dy.masa.litematica.schematic.SchematicaSchematic;
import fi.dy.masa.litematica.selection.AreaSelection;
import fi.dy.masa.litematica.selection.Box;
import fi.dy.masa.litematica.util.PositionUtils;

public final class RvcGameTestCommit
{
    private RvcGameTestCommit()
    {
    }

    public static Result create(Path gameRunDirectory, String repositoryName, RvcPlayerIdentity player, Level world, AreaSelection selection, boolean ignoreEntities) throws Exception
    {
        Objects.requireNonNull(gameRunDirectory, "gameRunDirectory");
        Objects.requireNonNull(player, "player");
        Objects.requireNonNull(world, "world");
        Objects.requireNonNull(selection, "selection");

        String displayName = normalizeDisplayName(repositoryName);
        Path repositoryDirectory = gameRunDirectory.resolve("repos").resolve(toDirectoryName(displayName)).normalize();

        if (Files.exists(repositoryDirectory))
        {
            throw new FileAlreadyExistsException(repositoryDirectory.toString());
        }

        SchematicaSchematic schematic = createSchematicFromSelection(world, selection, ignoreEntities);
        RevCommit commit = RvcRepository.init(repositoryDirectory, displayName, schematic, player);

        return new Result(repositoryDirectory, commit.getName());
    }

    private static SchematicaSchematic createSchematicFromSelection(Level world, AreaSelection selection, boolean ignoreEntities)
    {
        java.util.List<Box> boxes = PositionUtils.getValidBoxes(selection);
        Pair<BlockPos, BlockPos> corners = PositionUtils.getEnclosingAreaCorners(boxes);

        if (corners == null)
        {
            throw new IllegalArgumentException("RVC test commit requires a non-empty area selection");
        }

        BlockPos min = corners.getLeft();
        BlockPos size = corners.getRight().subtract(min).offset(1, 1, 1);
        return SchematicaSchematic.createFromWorld(world, min, size, ignoreEntities);
    }

    private static String normalizeDisplayName(String repositoryName)
    {
        if (repositoryName == null || repositoryName.isBlank())
        {
            return "rvc-test-" + Instant.now().toEpochMilli();
        }

        return repositoryName.trim();
    }

    private static String toDirectoryName(String displayName)
    {
        if (displayName.indexOf('/') >= 0 || displayName.indexOf('\\') >= 0)
        {
            throw new IllegalArgumentException("RVC repository name must not contain path separators: " + displayName);
        }

        return displayName;
    }

    public record Result(Path repositoryDirectory, String commitId)
    {
    }
}
