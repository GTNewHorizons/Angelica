package com.gtnewhorizons.angelica.client.font;

import com.gtnewhorizons.angelica.glsm.backend.RenderBackend;

import java.util.Arrays;

final class QuadRegionAllocator {

    private int[] freeStart = new int[16];
    private int[] freeLength = new int[16];
    private int freeCount;

    private int[] retiredStart = new int[16];
    private int[] retiredLength = new int[16];
    private long[] retiredFrame = new long[16];
    private int retiredCount;

    QuadRegionAllocator(int capacity) {
        reset(capacity);
    }

    void reset(int capacity) {
        freeCount = 0;
        retiredCount = 0;
        if (capacity > 0) {
            freeStart[0] = 0;
            freeLength[0] = capacity;
            freeCount = 1;
        }
    }

    int allocate(int quads) {
        for (int i = 0; i < freeCount; i++) {
            final int length = freeLength[i];
            if (length < quads) continue;
            final int start = freeStart[i];
            if (length == quads) {
                System.arraycopy(freeStart, i + 1, freeStart, i, freeCount - i - 1);
                System.arraycopy(freeLength, i + 1, freeLength, i, freeCount - i - 1);
                freeCount--;
            } else {
                freeStart[i] = start + quads;
                freeLength[i] = length - quads;
            }
            return start;
        }
        return -1;
    }

    void retire(int start, int quads, long frame) {
        if (retiredCount == retiredStart.length) {
            final int n = retiredCount * 2;
            retiredStart = Arrays.copyOf(retiredStart, n);
            retiredLength = Arrays.copyOf(retiredLength, n);
            retiredFrame = Arrays.copyOf(retiredFrame, n);
        }
        retiredStart[retiredCount] = start;
        retiredLength[retiredCount] = quads;
        retiredFrame[retiredCount] = frame;
        retiredCount++;
    }

    void advance(long frame) {
        final long reusableUpTo = frame - RenderBackend.MAX_FRAMES_IN_FLIGHT;
        int kept = 0;
        for (int i = 0; i < retiredCount; i++) {
            if (retiredFrame[i] <= reusableUpTo) {
                free(retiredStart[i], retiredLength[i]);
            } else {
                retiredStart[kept] = retiredStart[i];
                retiredLength[kept] = retiredLength[i];
                retiredFrame[kept] = retiredFrame[i];
                kept++;
            }
        }
        retiredCount = kept;
    }

    private void free(int start, int quads) {
        int at = 0;
        while (at < freeCount && freeStart[at] < start) at++;
        final boolean joinsPrev = at > 0 && freeStart[at - 1] + freeLength[at - 1] == start;
        final boolean joinsNext = at < freeCount && start + quads == freeStart[at];
        if (joinsPrev && joinsNext) {
            freeLength[at - 1] += quads + freeLength[at];
            System.arraycopy(freeStart, at + 1, freeStart, at, freeCount - at - 1);
            System.arraycopy(freeLength, at + 1, freeLength, at, freeCount - at - 1);
            freeCount--;
        } else if (joinsPrev) {
            freeLength[at - 1] += quads;
        } else if (joinsNext) {
            freeStart[at] = start;
            freeLength[at] += quads;
        } else {
            if (freeCount == freeStart.length) {
                freeStart = Arrays.copyOf(freeStart, freeCount * 2);
                freeLength = Arrays.copyOf(freeLength, freeCount * 2);
            }
            System.arraycopy(freeStart, at, freeStart, at + 1, freeCount - at);
            System.arraycopy(freeLength, at, freeLength, at + 1, freeCount - at);
            freeStart[at] = start;
            freeLength[at] = quads;
            freeCount++;
        }
    }
}
