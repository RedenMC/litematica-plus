package me.zly2006.rvc;

import java.io.IOException;
import javax.annotation.Nullable;

public interface RvcWorldReader
{
    String blockStateAt(RvcIntPosition worldPos) throws IOException;

    @Nullable
    default byte[] blockEntityNbtAt(RvcIntPosition worldPos) throws IOException
    {
        return null;
    }
}
