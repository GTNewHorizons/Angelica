package com.seibel.distanthorizons.common.wrappers.minecraft;

import com.seibel.distanthorizons.core.util.math.Vec3d;
import com.seibel.distanthorizons.core.util.math.Vec3f;
import com.seibel.distanthorizons.core.wrapperInterfaces.minecraft.IMinecraftRenderWrapper;
import com.seibel.distanthorizons.core.wrapperInterfaces.misc.ILightMapWrapper;
import com.seibel.distanthorizons.core.wrapperInterfaces.world.ILevelWrapper;
import org.jetbrains.annotations.Nullable;

import java.awt.*;

public class MinecraftRenderWrapper implements IMinecraftRenderWrapper {

    public static MinecraftRenderWrapper INSTANCE = new MinecraftRenderWrapper();
    public int finalLevelFrameBufferId;

    @Override
    public Vec3f getLookAtVector() {
        return null;
    }

    @Override
    public boolean playerHasBlindingEffect() {
        return false;
    }

    @Override
    public Vec3d getCameraExactPosition() {
        return null;
    }

    @Override
    public Color getFogColor(float partialTicks) {
        return null;
    }

    @Override
    public boolean isFogStateSpecial() {
        return false;
    }

    @Override
    public Color getSkyColor() {
        return null;
    }

    @Override
    public double getFov(float partialTicks) {
        return 0;
    }

    @Override
    public int getRenderDistance() {
        return 0;
    }

    @Override
    public int getScreenWidth() {
        return 0;
    }

    @Override
    public int getScreenHeight() {
        return 0;
    }

    @Override
    public int getTargetFrameBuffer() {
        return 0;
    }

    @Override
    public int getDepthTextureId() {
        return 0;
    }

    @Override
    public int getColorTextureId() {
        return 0;
    }

    @Override
    public int getTargetFrameBufferViewportWidth() {
        return 0;
    }

    @Override
    public int getTargetFrameBufferViewportHeight() {
        return 0;
    }

    @Override
    public void clearTargetFrameBuffer() {

    }

    @Override
    public @Nullable ILightMapWrapper getLightmapWrapper(ILevelWrapper level) {
        return null;
    }
}
