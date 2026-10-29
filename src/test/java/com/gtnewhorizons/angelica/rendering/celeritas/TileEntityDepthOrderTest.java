package com.gtnewhorizons.angelica.rendering.celeritas;

import it.unimi.dsi.fastutil.objects.ObjectArrayList;
import net.minecraft.tileentity.TileEntity;
import org.junit.jupiter.api.Test;

import java.util.Arrays;

import static org.junit.jupiter.api.Assertions.assertEquals;

class TileEntityDepthOrderTest {

    private static TileEntity at(int x, int y, int z) {
        final TileEntity te = new TileEntity();
        te.xCoord = x;
        te.yCoord = y;
        te.zCoord = z;
        return te;
    }

    @Test
    void sortsFarthestFirstAndKeepsTiesInListOrder() {
        final TileEntity near = at(0, 0, 2);
        final TileEntity far = at(0, 0, 181);
        final TileEntity mid = at(0, 0, 5);
        final TileEntity midOpposite = at(0, 0, -5);
        final ObjectArrayList<TileEntity> list = new ObjectArrayList<>(Arrays.asList(near, far, mid, midOpposite));
        new TileEntityDepthOrder().sortBackToFront(list, 0.5, 0.5, 0.5);
        assertEquals(Arrays.asList(far, mid, midOpposite, near), list);
    }
}
