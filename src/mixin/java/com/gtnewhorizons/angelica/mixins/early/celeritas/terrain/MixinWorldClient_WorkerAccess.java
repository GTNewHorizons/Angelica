package com.gtnewhorizons.angelica.mixins.early.celeritas.terrain;

import net.minecraft.block.Block;
import net.minecraft.client.multiplayer.WorldClient;
import net.minecraft.profiler.Profiler;
import net.minecraft.tileentity.TileEntity;
import net.minecraft.world.World;
import net.minecraft.world.WorldProvider;
import net.minecraft.world.WorldSettings;
import net.minecraft.world.biome.BiomeGenBase;
import net.minecraft.world.storage.ISaveHandler;
import net.minecraftforge.common.util.ForgeDirection;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;

import com.gtnewhorizons.angelica.rendering.RenderThreadContext;
import com.gtnewhorizons.angelica.rendering.WorkerWorldAccess;
import com.gtnewhorizons.angelica.rendering.celeritas.world.WorldSlice;

@Mixin(WorldClient.class)
public abstract class MixinWorldClient_WorkerAccess extends World {

    private MixinWorldClient_WorkerAccess(ISaveHandler saveHandler, String name, WorldProvider provider, WorldSettings settings, Profiler profiler) {
        super(saveHandler, name, provider, settings, profiler);
    }

    @Unique
    private static WorldSlice angelica$workerSlice(String method, int x, int y, int z) {
        final WorldSlice slice = RenderThreadContext.workerSlice();
        if (slice != null && !slice.containsBlock(x, y, z)) {
            WorkerWorldAccess.readOutsideSlice(method, x, y, z, slice.getRenderingBlock());
        }
        return slice;
    }

    @Override
    public Block getBlock(int x, int y, int z) {
        final WorldSlice slice = angelica$workerSlice("getBlock", x, y, z);
        return slice == null ? super.getBlock(x, y, z) : slice.getBlock(x, y, z);
    }

    @Override
    public int getBlockMetadata(int x, int y, int z) {
        final WorldSlice slice = angelica$workerSlice("getBlockMetadata", x, y, z);
        return slice == null ? super.getBlockMetadata(x, y, z) : slice.getBlockMetadata(x, y, z);
    }

    @Override
    public TileEntity getTileEntity(int x, int y, int z) {
        final WorldSlice slice = angelica$workerSlice("getTileEntity", x, y, z);
        return slice == null ? super.getTileEntity(x, y, z) : slice.getTileEntity(x, y, z);
    }

    @Override
    public int getLightBrightnessForSkyBlocks(int x, int y, int z, int min) {
        final WorldSlice slice = angelica$workerSlice("getLightBrightnessForSkyBlocks", x, y, z);
        return slice == null ? super.getLightBrightnessForSkyBlocks(x, y, z, min) : slice.getLightBrightnessForSkyBlocks(x, y, z, min);
    }

    @Override
    public boolean isSideSolid(int x, int y, int z, ForgeDirection side, boolean _default) {
        final WorldSlice slice = angelica$workerSlice("isSideSolid", x, y, z);
        return slice == null ? super.isSideSolid(x, y, z, side, _default) : slice.isSideSolid(x, y, z, side, _default);
    }

    @Override
    public BiomeGenBase getBiomeGenForCoords(int x, int z) {
        final WorldSlice slice = RenderThreadContext.workerSlice();
        return slice == null ? super.getBiomeGenForCoords(x, z) : slice.getBiomeGenForCoords(x, z);
    }
}
