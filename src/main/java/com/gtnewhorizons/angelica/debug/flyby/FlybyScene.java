package com.gtnewhorizons.angelica.debug.flyby;

import net.minecraft.entity.Entity;
import net.minecraft.tileentity.TileEntity;
import net.minecraft.tileentity.TileEntityDispenser;
import net.minecraft.world.WorldServer;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.List;

final class FlybyScene {

    private static final Logger LOGGER = LogManager.getLogger("Angelica/Flyby");
    private static final String[] NO_COMMANDS = new String[0];

    static final String MARKER = "flyby";

    static String[] load(String path) {
        final List<String> lines;
        try {
            lines = Files.readAllLines(Paths.get(path));
        } catch (IOException e) {
            LOGGER.error("Could not read flyby scene '{}'", path, e);
            return NO_COMMANDS;
        }

        final String[] commands = new String[lines.size()];
        int n = 0;
        for (int i = 0; i < lines.size(); i++) {
            String line = lines.get(i).trim();
            if (line.isEmpty() || line.charAt(0) == '#') continue;
            if (line.charAt(0) == '/') line = line.substring(1).trim();
            if (line.isEmpty()) continue;
            commands[n++] = line;
        }
        if (n == commands.length) return commands;
        final String[] trimmed = new String[n];
        System.arraycopy(commands, 0, trimmed, 0, n);
        return trimmed;
    }

    static int count(WorldServer world) {
        final List<Entity> entities = world.loadedEntityList;
        int total = 0;
        for (int i = 0; i < entities.size(); i++) {
            final Entity entity = entities.get(i);
            if (entity != null && !entity.isDead && entity.getEntityData().getBoolean(MARKER)) total++;
        }

        final List<TileEntity> tileEntities = world.loadedTileEntityList;
        for (int i = 0; i < tileEntities.size(); i++) {
            if (marked(tileEntities.get(i))) total++;
        }
        return total;
    }

    static int clear(WorldServer world) {
        final List<Entity> entities = world.loadedEntityList;
        int cleared = 0;
        for (int i = 0; i < entities.size(); i++) {
            final Entity entity = entities.get(i);
            if (entity != null && entity.getEntityData().getBoolean(MARKER)) {
                entity.setDead();
                cleared++;
            }
        }

        final List<TileEntity> tileEntities = world.loadedTileEntityList;
        final List<TileEntity> markedBlocks = new ArrayList<>();
        for (int i = 0; i < tileEntities.size(); i++) {
            final TileEntity tileEntity = tileEntities.get(i);
            if (marked(tileEntity)) markedBlocks.add(tileEntity);
        }
        for (int i = 0; i < markedBlocks.size(); i++) {
            final TileEntity tileEntity = markedBlocks.get(i);
            world.setBlockToAir(tileEntity.xCoord, tileEntity.yCoord, tileEntity.zCoord);
        }
        return cleared + markedBlocks.size();
    }

    private static boolean marked(TileEntity tileEntity) {
        return tileEntity instanceof TileEntityDispenser dispenser && !dispenser.isInvalid() && dispenser.getClass() == TileEntityDispenser.class && MARKER.equals(dispenser.getInventoryName());
    }

    private FlybyScene() {}
}
