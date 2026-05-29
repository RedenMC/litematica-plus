package me.zly2006.rvc;

import java.io.IOException;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.BitSet;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;

public final class RvcCaptureEngine
{
    private RvcCaptureEngine()
    {
    }

    public static Result captureSite(Path repositoryDirectory, RvcManifest.Site site, RvcLocalState.SitePlacement placement,
                                     RvcWorldReader worldReader) throws IOException
    {
        RvcIntPosition origin = RvcIntPosition.fromList(placement.origin());
        Map<RvcChunkCoordinate, BitSet> plannedChunks = RvcCapturePlanner.planSite(site);
        Map<String, String> chunkObjects = new TreeMap<>();

        for (Map.Entry<RvcChunkCoordinate, BitSet> entry : plannedChunks.entrySet())
        {
            RvcChunkCoordinate coordinate = entry.getKey();
            BitSet mask = entry.getValue();
            List<String> blockStates = new ArrayList<>(mask.cardinality());
            List<RvcChunk.BlockEntityRecord> blockEntities = new ArrayList<>();

            for (int index = mask.nextSetBit(0); index >= 0; index = mask.nextSetBit(index + 1))
            {
                RvcIntPosition projectPos = RvcCapturePlanner.projectPosition(coordinate, index, RvcChunk.DEFAULT_SIZE, RvcChunk.DEFAULT_SIZE, RvcChunk.DEFAULT_SIZE);
                RvcIntPosition worldPos = origin.offset(projectPos);
                String blockState = worldReader.blockStateAt(worldPos);

                if (blockState == null || blockState.isBlank())
                {
                    throw new IOException("RVC world reader returned a blank block state at " + worldPos);
                }

                blockStates.add(blockState);
                byte[] blockEntityNbt = worldReader.blockEntityNbtAt(worldPos);

                if (blockEntityNbt != null)
                {
                    blockEntities.add(new RvcChunk.BlockEntityRecord(index, blockEntityNbt));
                }
            }

            RvcChunk chunk = RvcChunk.fromTrackedContent(
                    RvcChunk.DEFAULT_SIZE,
                    RvcChunk.DEFAULT_SIZE,
                    RvcChunk.DEFAULT_SIZE,
                    mask,
                    blockStates,
                    blockEntities,
                    List.of(),
                    List.of()
            );
            String objectId = RvcChunkStore.writeObjectIfMissing(repositoryDirectory, RvcChunkCodec.encode(chunk));
            chunkObjects.put(coordinate.key(), objectId);
        }

        return new Result(Map.copyOf(chunkObjects));
    }

    public record Result(Map<String, String> chunkObjects)
    {
        public Result
        {
            chunkObjects = Map.copyOf(chunkObjects);
        }
    }
}
