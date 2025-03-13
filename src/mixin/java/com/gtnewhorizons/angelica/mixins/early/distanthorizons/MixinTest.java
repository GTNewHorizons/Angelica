package com.gtnewhorizons.angelica.mixins.early.distanthorizons;

import cpw.mods.fml.common.FMLLog;
import makamys.coretweaks.ducks.optimization.IPendingBlockUpdatesWorldServer;
import makamys.coretweaks.optimization.ChunkPendingBlockUpdateMap;
import net.minecraft.block.Block;
import net.minecraft.profiler.Profiler;
import net.minecraft.world.*;
import net.minecraft.world.storage.ISaveHandler;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.Redirect;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import java.util.Iterator;
import java.util.Map;
import java.util.Set;
import java.util.TreeSet;

@Mixin(WorldServer.class)
public abstract class MixinTest extends World {
    public MixinTest() {
        super(null, null, null, (WorldSettings)null, null);
    }

    @Redirect(method="tickUpdates", at=@At(value = "INVOKE", target = "Lnet/minecraft/world/WorldServer;scheduleBlockUpdate(IIILnet/minecraft/block/Block;I)V"))
    private void disableTicks(WorldServer instance, int p_147464_1_, int p_147464_2_, int p_147464_3_, Block p_147464_4_, int p_147464_5_)
    {
        // Do nothing
    }

    private int dh$cleanupFrame;

    @Shadow
    private Set<NextTickListEntry> pendingTickListEntriesHashSet;
    @Shadow
    private TreeSet<NextTickListEntry> pendingTickListEntriesTreeSet;

    @Inject(method="tickUpdates", at = @At(value="HEAD"))
    private void cleanupTicks(boolean p_72955_1_, CallbackInfoReturnable<Boolean> cir)
    {
        dh$cleanupFrame++;
        if (dh$cleanupFrame > 20 * 10) // Cleanup every 10 seconds
        {
            IPendingBlockUpdatesWorldServer coretweaksMixin = (IPendingBlockUpdatesWorldServer)(Object)this;
            dh$cleanupFrame = 0;
            Iterator<NextTickListEntry> iterator = pendingTickListEntriesTreeSet.iterator();
            while (iterator.hasNext())
            {
                NextTickListEntry entry = iterator.next();

                if (!this.checkChunksExist(entry.xCoord, entry.yCoord, entry.zCoord, entry.xCoord, entry.yCoord, entry.zCoord)) {
                    iterator.remove();
                    pendingTickListEntriesHashSet.remove(entry);
                    ChunkPendingBlockUpdateMap.remove(coretweaksMixin, entry);
                }
            }
            FMLLog.getLogger().debug("Entries : " + pendingTickListEntriesHashSet.size());
        }
    }
}
