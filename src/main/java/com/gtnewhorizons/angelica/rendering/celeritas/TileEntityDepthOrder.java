package com.gtnewhorizons.angelica.rendering.celeritas;

import it.unimi.dsi.fastutil.objects.ObjectArrayList;
import net.minecraft.tileentity.TileEntity;

import java.util.Arrays;

final class TileEntityDepthOrder {

    private static final int RADIX_BITS = 8;
    private static final int BUCKETS = 1 << RADIX_BITS;

    private final int[] counts = new int[BUCKETS];
    private int[] keys = new int[0];
    private int[] keysScratch = new int[0];
    private int[] order = new int[0];
    private int[] orderScratch = new int[0];
    private TileEntity[] entities = new TileEntity[0];

    void sortBackToFront(ObjectArrayList<TileEntity> list, double camX, double camY, double camZ) {
        final int n = list.size();
        if (n < 2) return;
        final Object[] source = list.elements();
        if (keys.length < n) {
            final int capacity = source.length;
            keys = new int[capacity];
            keysScratch = new int[capacity];
            order = new int[capacity];
            orderScratch = new int[capacity];
            entities = new TileEntity[capacity];
        }
        for (int i = 0; i < n; i++) {
            final TileEntity te = (TileEntity) source[i];
            final double dx = te.xCoord + 0.5 - camX;
            final double dy = te.yCoord + 0.5 - camY;
            final double dz = te.zCoord + 0.5 - camZ;
            keys[i] = Integer.MAX_VALUE - Float.floatToRawIntBits((float) (dx * dx + dy * dy + dz * dz));
            order[i] = i;
            entities[i] = te;
        }
        final int[] sorted = sortOrder(n);
        for (int i = 0; i < n; i++) {
            source[i] = entities[sorted[i]];
            entities[sorted[i]] = null;
        }
    }

    private int[] sortOrder(int n) {
        int[] keyIn = keys, keyOut = keysScratch;
        int[] idxIn = order, idxOut = orderScratch;
        for (int shift = 0; shift < Integer.SIZE; shift += RADIX_BITS) {
            Arrays.fill(counts, 0);
            for (int i = 0; i < n; i++) counts[(keyIn[i] >>> shift) & (BUCKETS - 1)]++;
            if (counts[(keyIn[0] >>> shift) & (BUCKETS - 1)] == n) continue;
            int sum = 0;
            for (int b = 0; b < BUCKETS; b++) {
                final int c = counts[b];
                counts[b] = sum;
                sum += c;
            }
            for (int i = 0; i < n; i++) {
                final int dst = counts[(keyIn[i] >>> shift) & (BUCKETS - 1)]++;
                keyOut[dst] = keyIn[i];
                idxOut[dst] = idxIn[i];
            }
            final int[] k = keyIn;
            keyIn = keyOut;
            keyOut = k;
            final int[] x = idxIn;
            idxIn = idxOut;
            idxOut = x;
        }
        keys = keyIn;
        keysScratch = keyOut;
        order = idxIn;
        orderScratch = idxOut;
        return idxIn;
    }
}
