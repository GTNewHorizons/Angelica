package com.gtnewhorizons.angelica.debug.screen.entries;

import com.gtnewhorizons.angelica.api.debug.DebugContext;
import com.gtnewhorizons.angelica.api.debug.DebugFact;
import com.gtnewhorizons.angelica.api.debug.DebugScreenDisplayer;
import com.gtnewhorizons.angelica.api.debug.DebugScreenEntry;
import com.gtnewhorizons.angelica.compat.mojang.CompatMathHelper;
import com.gtnewhorizons.angelica.debug.screen.DebugGroup;
import com.gtnewhorizons.angelica.helpers.LoadControllerHelper;
import cpw.mods.fml.common.ModContainer;
import net.minecraft.client.entity.EntityClientPlayerMP;
import net.minecraft.util.Direction;
import net.minecraft.util.MathHelper;
import net.minecraft.world.EnumSkyBlock;
import net.minecraft.world.WorldProvider;
import net.minecraft.world.biome.BiomeGenBase;
import net.minecraft.world.chunk.Chunk;

import java.util.Locale;

public final class PositionEntries {

    private PositionEntries() {}

    public static final class PlayerPosition implements DebugScreenEntry {

        private static final String SEPARATOR = " / ";
        private static final String[] DIRECTION_NAMES = new String[4];
        private static final String[] TOWARDS = { "Towards positive Z", "Towards negative X", "Towards negative Z", "Towards positive X" };

        static {
            for (int i = 0; i < DIRECTION_NAMES.length; i++) {
                DIRECTION_NAMES[i] = Direction.directions[i].toLowerCase(Locale.ROOT);
            }
        }

        private WorldProvider cachedProvider;
        private String dimensionName = "";

        @Override
        public void display(DebugScreenDisplayer displayer, DebugContext context) {
            final EntityClientPlayerMP player = context.player();
            final double feetY = player.boundingBox.minY;
            final int bx = context.blockX();
            final int by = context.blockY();
            final int bz = context.blockZ();
            displayer.fact(DebugGroup.POSITION, "XYZ").value(player.posX, 3).text(SEPARATOR).value(feetY, 5).text(SEPARATOR).value(player.posZ, 3);
            displayer.fact(DebugGroup.POSITION, "Block").value(bx).text(' ').value(by).text(' ').value(bz);
            displayer.fact(DebugGroup.POSITION, "Chunk").value(bx >> 4).text(' ').value(by >> 4).text(' ').value(bz >> 4);
            final int heading = MathHelper.floor_double((double) (player.rotationYaw * 4.0F / 360.0F) + 0.5D) & 3;
            final DebugFact facing = displayer.fact(DebugGroup.POSITION, "Facing");
            facing.value(DIRECTION_NAMES[heading]).text(" (").text(TOWARDS[heading]).text(") (");
            facing.value(MathHelper.wrapAngleTo180_float(player.rotationYaw), 1).text(SEPARATOR).value(MathHelper.wrapAngleTo180_float(player.rotationPitch), 1).text(')');
            final WorldProvider provider = context.world().provider;
            if (provider != cachedProvider) {
                cachedProvider = provider;
                dimensionName = provider.getDimensionName();
            }
            displayer.fact(DebugGroup.POSITION, "Dimension").value(dimensionName).text(" (").value(provider.dimensionId).text(')');
        }
    }

    public static final class Biome implements DebugScreenEntry {

        private BiomeGenBase biome;
        private String label;

        @Override
        public void display(DebugScreenDisplayer displayer, DebugContext context) {
            final Chunk chunk = context.chunk();
            if (chunk == null) return;
            final BiomeGenBase current = context.world().getBiomeGenForCoords(context.blockX(), context.blockZ());
            if (current != biome) {
                final ModContainer mod = LoadControllerHelper.getOwningMod(current.getClass());
                label = mod == null ? current.biomeName : current.biomeName + " [" + mod.getName() + "]";
                biome = current;
            }
            displayer.fact(DebugGroup.POSITION, "Biome").value(label);
        }
    }

    public static void sectionPosition(DebugScreenDisplayer displayer, DebugContext context) {
        displayer.fact(DebugGroup.POSITION, "Section-Relative").padded(context.blockX() & 15, 2).text(' ').padded(context.blockY() & 15, 2).text(' ').padded(context.blockZ() & 15, 2);
    }

    public static void speed(DebugScreenDisplayer displayer, DebugContext context) {
        final EntityClientPlayerMP player = context.player();
        final double dx = player.posX - player.prevPosX;
        final double dy = player.posY - player.prevPosY;
        final double dz = player.posZ - player.prevPosZ;
        displayer.fact(DebugGroup.POSITION, "Speed").value(Math.sqrt(dx * dx + dy * dy + dz * dz), 3).text(" blocks/tick");
        displayer.fact(DebugGroup.POSITION, "Walk/Fly").value(player.capabilities.getWalkSpeed(), 3).text(" / ").value(player.capabilities.getFlySpeed(), 3);
        displayer.fact(DebugGroup.POSITION, "On Ground").value(player.onGround ? "true" : "false");
    }

    public static void lightLevels(DebugScreenDisplayer displayer, DebugContext context) {
        final Chunk chunk = context.chunk();
        if (chunk == null) return;
        final int x = context.blockX() & 15;
        final int z = context.blockZ() & 15;
        final int cy = CompatMathHelper.clampHeight(context.blockY());
        displayer.fact(DebugGroup.POSITION, "Light")
            .value(chunk.getBlockLightValue(x, cy, z, 0))
            .text(" (")
            .value(chunk.getSavedLightValue(EnumSkyBlock.Sky, x, cy, z))
            .text(" sky, ")
            .value(chunk.getSavedLightValue(EnumSkyBlock.Block, x, cy, z))
            .text(" block)");
    }

    public static void heightmap(DebugScreenDisplayer displayer, DebugContext context) {
        final Chunk chunk = context.chunk();
        if (chunk == null) return;
        displayer.fact(DebugGroup.POSITION, "Top Section").value(chunk.getTopFilledSegment() + 15);
        displayer.fact(DebugGroup.POSITION, "Height").value(context.world().getHeightValue(context.blockX(), context.blockZ()));
    }
}
