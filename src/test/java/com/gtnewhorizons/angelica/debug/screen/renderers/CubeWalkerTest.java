package com.gtnewhorizons.angelica.debug.screen.renderers;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;

class CubeWalkerTest {

    private static List<Integer> reference(int cx, int cy, int cz, int radius, int minY, int maxY) {
        final List<Integer> out = new ArrayList<>();
        for (int r = 0; r <= radius; r++) {
            for (int dx = -r; dx <= r; dx++) {
                for (int dz = -r; dz <= r; dz++) {
                    final boolean edge = dx == -r || dx == r || dz == -r || dz == r;
                    for (int dy = -r; dy <= r; dy++) {
                        if (!edge && dy != -r && dy != r) continue;
                        if (cy + dy < minY || cy + dy > maxY) continue;
                        out.add(cx + dx);
                        out.add(cy + dy);
                        out.add(cz + dz);
                    }
                }
            }
        }
        return out;
    }

    private static List<Integer> walk(int cx, int cy, int cz, int radius, int stopAfter) {
        final List<Integer> out = new ArrayList<>();
        CubeWalker.walk(cx, cy, cz, radius, (x, y, z) -> {
            out.add(x);
            out.add(y);
            out.add(z);
            return out.size() / 3 != stopAfter;
        });
        return out;
    }

    @ParameterizedTest
    @CsvSource({"10,100,-7,3", "0,1,0,3", "0,254,0,3", "5,6,7,0", "0,300,0,50"})
    void matchesShellOrderAndClampsToWorldHeight(int cx, int cy, int cz, int radius) {
        assertEquals(reference(cx, cy, cz, radius, 0, 255), walk(cx, cy, cz, radius, -1));
    }

    @Test
    void stopsWhenSinkReturnsFalse() {
        assertEquals(10, walk(0, 64, 0, 3, 10).size() / 3);
    }
}
