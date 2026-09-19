package com.gtnewhorizons.angelica.debug.flyby;

import net.minecraft.command.ICommandSender;
import net.minecraft.entity.player.EntityPlayerMP;
import net.minecraft.util.ChatComponentText;
import net.minecraft.util.ChunkCoordinates;
import net.minecraft.util.IChatComponent;
import net.minecraft.world.World;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

final class FlybyCommandSender implements ICommandSender {

    private static final Logger LOGGER = LogManager.getLogger("Angelica/Flyby");
    private static final String NAME = "Flyby";

    private final EntityPlayerMP player;
    private final ChunkCoordinates anchor;

    FlybyCommandSender(EntityPlayerMP player, ChunkCoordinates anchor) {
        this.player = player;
        this.anchor = anchor;
    }

    @Override
    public String getCommandSenderName() {
        return NAME;
    }

    @Override
    public IChatComponent func_145748_c_() {
        return new ChatComponentText(NAME);
    }

    @Override
    public void addChatMessage(IChatComponent message) {
        LOGGER.info(message.getUnformattedText());
    }

    @Override
    public boolean canCommandSenderUseCommand(int permissionLevel, String command) {
        return true;
    }

    @Override
    public ChunkCoordinates getPlayerCoordinates() {
        return this.anchor;
    }

    @Override
    public World getEntityWorld() {
        return this.player.getEntityWorld();
    }
}
