package com.gtnewhorizons.angelica.utils;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import net.minecraft.tileentity.TileEntity;
import net.minecraft.world.ChunkPosition;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import com.gtnewhorizons.angelica.rendering.RenderThreadContext;
import com.gtnewhorizons.angelica.rendering.celeritas.world.WorldSlice;

class ConcurrentTileEntityMapTest {

    private static final ChunkPosition POS = new ChunkPosition(1, 2, 3);

    private final ConcurrentTileEntityMap map = new ConcurrentTileEntityMap();

    @AfterEach
    void leaveWorkerContext() {
        RenderThreadContext.clear();
    }

    private static void enterWorkerContext() {
        RenderThreadContext.set(mock(WorldSlice.class));
    }

    private static TileEntity tileEntity(boolean invalid) {
        final TileEntity te = mock(TileEntity.class);
        when(te.isInvalid()).thenReturn(invalid);
        return te;
    }

    @Test
    void containsKeyAgreesWithGetForAnInvalidTileEntityOnAWorker() {
        map.put(POS, tileEntity(true));
        enterWorkerContext();

        assertNull(map.get(POS), "get must hide an invalid tile entity from a worker");
        assertFalse(map.containsKey(POS), "containsKey must agree with get");
    }

    @Test
    void containsKeyAgreesWithGetForAValidTileEntityOnAWorker() {
        final TileEntity te = tileEntity(false);
        map.put(POS, te);
        enterWorkerContext();

        assertSame(te, map.get(POS));
        assertTrue(map.containsKey(POS));
    }

    @Test
    void containsKeyReportsInvalidTileEntitiesOnTheClientThread() {
        map.put(POS, tileEntity(true));

        assertTrue(map.containsKey(POS), "the client thread owns invalidation and must still see the entry");
    }

    @Test
    void removeFromAWorkerDefersInsteadOfMutating() {
        final TileEntity te = tileEntity(false);
        map.put(POS, te);
        enterWorkerContext();

        assertNull(map.remove(POS));
        assertEquals(1, map.size(), "a worker must not mutate the map");

        RenderThreadContext.clear();
        assertSame(te, map.get(POS), "a valid tile entity survives a deferred invalidation");
    }

    @Test
    void deferredInvalidationEvictsOnTheNextClientThreadWrite() {
        map.put(POS, tileEntity(true));
        enterWorkerContext();
        map.get(POS);
        RenderThreadContext.clear();

        map.put(new ChunkPosition(4, 5, 6), tileEntity(false));

        assertFalse(map.containsKey(POS));
    }
}
