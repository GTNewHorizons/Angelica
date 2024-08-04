package com.gtnewhorizons.angelica.mixins.early.sodium;

import com.gtnewhorizons.angelica.glsm.ThreadedBlockData;
import net.minecraft.block.Block;
import org.spongepowered.asm.mixin.Mixin;

/**
 * Store thread-safe block data here.
 *
 * We need to be careful - blocks will initialize some stuff in the constructor, so the first ThreadedBlockData
 * instance should be used as the clone for all others. This will ensure the block bounds are set correctly
 * for blocks that don't change their bounds at runtime.
 */
@Mixin(Block.class)
public class MixinBlock implements ThreadedBlockData.Getter {
    private final ThreadLocal<ThreadedBlockData> angelica$threadData = ThreadLocal.withInitial(() -> null);
    private volatile ThreadedBlockData angelica$initialData;

    @Override
    public ThreadedBlockData angelica$getThreadData() {
        ThreadedBlockData data = angelica$threadData.get();
        if(data != null)
            return data;

        return createThreadedBlockData();
    }

    private ThreadedBlockData createThreadedBlockData() {
        ThreadedBlockData data;

        synchronized (this) {
            if(angelica$initialData == null) {
                data = angelica$initialData = new ThreadedBlockData();
            } else {
                data = new ThreadedBlockData(angelica$initialData);
            }
        }

        angelica$threadData.set(data);

        return data;
    }
}
