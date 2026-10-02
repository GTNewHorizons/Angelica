package com.gtnewhorizons.angelica.mixins.early.rendering;

import com.gtnewhorizons.angelica.client.rendering.ThreadedBlockData;
import net.minecraft.block.Block;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;

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
    @Unique
    private ThreadedBlockData angelica$mainData;
    @Unique
    private ThreadedBlockData angelica$serverData;

    @Override
    public ThreadedBlockData angelica$getThreadData() {
        Thread t = Thread.currentThread();

        if (t == ThreadedBlockData.MAIN_THREAD) {
            ThreadedBlockData data = angelica$mainData;
            return data != null ? data : (angelica$mainData = createThreadedBlockData());
        }

        if (t == ThreadedBlockData.serverThread) {
            ThreadedBlockData data = angelica$serverData;
            return (data == null || data.owner != t) ? (angelica$serverData = createThreadedBlockData()) : data;
        }

        ThreadedBlockData data = angelica$threadData.get();
        if(data != null)
            return data;

        data = createThreadedBlockData();
        angelica$threadData.set(data);
        return data;
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

        return data;
    }
}
