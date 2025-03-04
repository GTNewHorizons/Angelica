package com.seibel.distanthorizons.common.wrappers.world;

import com.seibel.distanthorizons.api.enums.worldGeneration.EDhApiLevelType;
import com.seibel.distanthorizons.api.interfaces.render.IDhApiCustomRenderRegister;
import com.seibel.distanthorizons.core.level.IDhLevel;
import com.seibel.distanthorizons.core.pos.blockPos.DhBlockPos;
import com.seibel.distanthorizons.core.wrapperInterfaces.block.IBlockStateWrapper;
import com.seibel.distanthorizons.core.wrapperInterfaces.world.IBiomeWrapper;
import com.seibel.distanthorizons.core.wrapperInterfaces.world.IClientLevelWrapper;
import com.seibel.distanthorizons.core.wrapperInterfaces.world.IDimensionTypeWrapper;
import com.seibel.distanthorizons.core.wrapperInterfaces.world.IServerLevelWrapper;
import net.minecraft.client.multiplayer.WorldClient;
import org.jetbrains.annotations.Nullable;

import java.awt.*;
import java.io.File;

public class ClientLevelWrapper implements IClientLevelWrapper {
    public ClientLevelWrapper(WorldClient level) {
    }

    public static IClientLevelWrapper getWrapper(WorldClient clientLevel, boolean b) {
        return null;
    }
    public static IClientLevelWrapper getWrapper(WorldClient clientLevel) {
        return null;
    }

    @Override
    public @Nullable IServerLevelWrapper tryGetServerSideWrapper() {
        return null;
    }

    @Override
    public int getBlockColor(DhBlockPos pos, IBiomeWrapper biome, IBlockStateWrapper blockState) {
        return 0;
    }

    @Override
    public int getDirtBlockColor() {
        return 0;
    }

    @Override
    public void clearBlockColorCache() {

    }

    @Override
    public @Nullable IBiomeWrapper getPlainsBiomeWrapper() {
        return null;
    }

    @Override
    public Color getCloudColor(float tickDelta) {
        return null;
    }

    @Override
    public IDimensionTypeWrapper getDimensionType() {
        return null;
    }

    @Override
    public String getDimensionName() {
        return "";
    }

    @Override
    public long getHashedSeed() {
        return 0;
    }

    @Override
    public String getDhIdentifier() {
        return "";
    }

    @Override
    public EDhApiLevelType getLevelType() {
        return null;
    }

    @Override
    public boolean hasCeiling() {
        return false;
    }

    @Override
    public boolean hasSkyLight() {
        return false;
    }

    @Override
    public int getMaxHeight() {
        return 0;
    }

    @Override
    public IDhApiCustomRenderRegister getRenderRegister() {
        return null;
    }

    @Override
    public File getDhSaveFolder() {
        return null;
    }

    @Override
    public boolean hasChunkLoaded(int chunkX, int chunkZ) {
        return false;
    }

    @Override
    public IBlockStateWrapper getBlockState(DhBlockPos pos) {
        return null;
    }

    @Override
    public IBiomeWrapper getBiome(DhBlockPos pos) {
        return null;
    }

    @Override
    public void onUnload() {

    }

    @Override
    public void setParentLevel(IDhLevel parentLevel) {

    }

    @Override
    public Object getWrappedMcObject() {
        return null;
    }
}
