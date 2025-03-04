package com.seibel.distanthorizons.common.wrappers.minecraft;

import com.seibel.distanthorizons.core.enums.EDhDirection;
import com.seibel.distanthorizons.core.pos.DhChunkPos;
import com.seibel.distanthorizons.core.pos.blockPos.DhBlockPos;
import com.seibel.distanthorizons.core.wrapperInterfaces.minecraft.IMinecraftClientWrapper;
import com.seibel.distanthorizons.core.wrapperInterfaces.minecraft.IProfilerWrapper;
import com.seibel.distanthorizons.core.wrapperInterfaces.world.IClientLevelWrapper;
import com.seibel.distanthorizons.core.wrapperInterfaces.world.ILevelWrapper;

import java.util.ArrayList;
import java.util.UUID;

public class MinecraftClientWrapper implements IMinecraftClientWrapper {
    public static MinecraftClientWrapper INSTANCE = new MinecraftClientWrapper();

    @Override
    public void clearFrameObjectCache() {

    }

    @Override
    public float getShade(EDhDirection lodDirection) {
        return 0;
    }

    @Override
    public boolean hasSinglePlayerServer() {
        return false;
    }

    @Override
    public boolean clientConnectedToDedicatedServer() {
        return false;
    }

    @Override
    public boolean connectedToReplay() {
        return false;
    }

    @Override
    public String getCurrentServerName() {
        return "";
    }

    @Override
    public String getCurrentServerIp() {
        return "";
    }

    @Override
    public String getCurrentServerVersion() {
        return "";
    }

    @Override
    public boolean playerExists() {
        return false;
    }

    @Override
    public UUID getPlayerUUID() {
        return null;
    }

    @Override
    public String getUsername() {
        return "";
    }

    @Override
    public DhBlockPos getPlayerBlockPos() {
        return null;
    }

    @Override
    public DhChunkPos getPlayerChunkPos() {
        return null;
    }

    @Override
    public IClientLevelWrapper getWrappedClientLevel() {
        return null;
    }

    @Override
    public IClientLevelWrapper getWrappedClientLevel(boolean bypassLevelKeyManager) {
        return null;
    }

    @Override
    public IProfilerWrapper getProfiler() {
        return null;
    }

    @Override
    public ArrayList<ILevelWrapper> getAllServerWorlds() {
        return null;
    }

    @Override
    public void sendChatMessage(String string) {

    }

    @Override
    public void sendOverlayMessage(String string) {

    }

    @Override
    public void crashMinecraft(String errorMessage, Throwable exception) {

    }

    @Override
    public Object getOptionsObject() {
        return null;
    }

    @Override
    public void executeOnRenderThread(Runnable runnable) {

    }
}
