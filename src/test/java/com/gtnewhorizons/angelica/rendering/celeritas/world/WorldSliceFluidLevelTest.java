package com.gtnewhorizons.angelica.rendering.celeritas.world;

import net.minecraft.client.multiplayer.WorldClient;
import net.minecraft.world.WorldProvider;
import net.minecraft.world.gen.structure.StructureBoundingBox;
import net.minecraftforge.fluids.Fluid;

import org.junit.jupiter.api.Test;

import com.gtnewhorizons.angelica.compat.ModStatus;
import com.gtnewhorizons.angelica.compat.mojang.ChunkSectionPos;
import com.gtnewhorizons.angelica.glsm.testutil.Reflect;
import com.gtnewhorizons.angelica.proxy.ClientProxy;
import com.gtnewhorizons.angelica.rendering.celeritas.world.cloned.ChunkRenderContext;
import com.gtnewhorizons.angelica.rendering.celeritas.world.cloned.ClonedChunkSection;

import me.jellysquid.mods.sodium.client.gui.SodiumGameOptions;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class WorldSliceFluidLevelTest {

    @Test
    void readsSourceAndFlowLevelsAcrossSectionBoundaries() {
        final SodiumGameOptions previousOptions = Reflect.getStatic(ClientProxy.class, "CONFIG");
        final boolean wasLoaded = ModStatus.isLiquidLoggedLoaded;
        Reflect.setStatic(ClientProxy.class, "CONFIG", new SodiumGameOptions());
        ModStatus.isLiquidLoggedLoaded = true;
        try {
            checkFluidLevels();
        } finally {
            ModStatus.isLiquidLoggedLoaded = wasLoaded;
            Reflect.setStatic(ClientProxy.class, "CONFIG", previousOptions);
        }
    }

    private static void checkFluidLevels() {
        final WorldClient world = mock(WorldClient.class);
        Reflect.set(world, "provider", mock(WorldProvider.class));
        final WorldSlice slice = new WorldSlice(world);
        final ClonedChunkSection[] sections = new ClonedChunkSection[64];
        for (int x = 0; x < 3; x++) {
            for (int y = 0; y < 3; y++) {
                for (int z = 0; z < 3; z++) {
                    final ClonedChunkSection section = mock(ClonedChunkSection.class);
                    when(section.getPosition()).thenReturn(ChunkSectionPos.from(x - 2, y, z - 1));
                    sections[WorldSlice.getLocalSectionIndex(x, y, z)] = section;
                }
            }
        }
        final Fluid water = new Fluid("water");
        final ClonedChunkSection west = sections[WorldSlice.getLocalSectionIndex(0, 1, 1)];
        final ClonedChunkSection center = sections[WorldSlice.getLocalSectionIndex(1, 1, 1)];
        final ClonedChunkSection east = sections[WorldSlice.getLocalSectionIndex(2, 1, 1)];
        when(center.getFluid(0, 4, 7)).thenReturn(water);
        when(west.getFluidLevel(15, 4, 7)).thenReturn(3);
        when(center.getFluidLevel(15, 4, 7)).thenReturn(5);
        when(east.getFluidLevel(0, 4, 7)).thenReturn(8);
        when(east.getFluidLevel(2, 4, 7)).thenReturn(12);

        slice.copyData(new ChunkRenderContext(ChunkSectionPos.from(-1, 1, 0), sections,
            new StructureBoundingBox(-18, 14, -2, 1, 33, 17)));

        assertSame(water, slice.liquidlogged$getFluid(-16, 20, 7));
        assertEquals(0, slice.liquidlogged$getFluidLevel(-16, 20, 7));
        assertEquals(3, slice.liquidlogged$getFluidLevel(-17, 20, 7));
        assertEquals(5, slice.liquidlogged$getFluidLevel(-1, 20, 7));
        assertEquals(8, slice.liquidlogged$getFluidLevel(0, 20, 7));
        assertEquals(0, slice.liquidlogged$getFluidLevel(2, 20, 7));
    }
}
