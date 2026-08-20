package com.gtnewhorizons.angelica.rendering;

import com.gtnewhorizon.gtnhlib.client.renderer.vertex.VertexFormatElement;
import com.gtnewhorizons.angelica.glsm.ffp.InstancedAttribs;
import net.coderbot.iris.gl.shader.ProgramCreator;
import org.junit.jupiter.api.Test;

import java.util.HashMap;
import java.util.Map;
import java.util.TreeSet;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Make sure we don't ever have collisions again, that was annoying.
 */
class VertexAttributeLayoutTest {

    private static final int MAX_VERTEX_ATTRIBS = 16;

    private final Map<Integer, String> claimed = new HashMap<>();

    private void claim(int location, String owner) {
        assertTrue(location >= 0 && location < MAX_VERTEX_ATTRIBS,
            owner + " uses location " + location + ", outside 0.." + (MAX_VERTEX_ATTRIBS - 1));
        final String previous = claimed.put(location, owner);
        assertNull(previous, "location " + location + " claimed by both " + previous + " and " + owner);
    }

    @Test
    void reservedAttributeLocationsDoNotOverlap() {
        for (VertexFormatElement.Usage usage : VertexFormatElement.Usage.values()) {
            final int loc = usage.getAttributeLocation();
            if (loc < 0) continue;
            claim(loc, "vertex format " + usage.name());
        }

        claim(InstancedAttribs.LOC_MATRIX_COL0, "InstancedAttribs.LOC_MATRIX_COL0");
        claim(InstancedAttribs.LOC_MATRIX_COL1, "InstancedAttribs.LOC_MATRIX_COL1");
        claim(InstancedAttribs.LOC_MATRIX_COL2, "InstancedAttribs.LOC_MATRIX_COL2");
        claim(InstancedAttribs.LOC_MATRIX_COL3, "InstancedAttribs.LOC_MATRIX_COL3");
        claim(InstancedAttribs.LOC_COLOR, "InstancedAttribs.LOC_COLOR");
        claim(InstancedAttribs.LOC_LIGHTMAP, "InstancedAttribs.LOC_LIGHTMAP");

        claim(ProgramCreator.MC_ENTITY, "ProgramCreator.MC_ENTITY");
        claim(ProgramCreator.MC_MID_TEX_COORD, "ProgramCreator.MC_MID_TEX_COORD");
        claim(ProgramCreator.AT_TANGENT, "ProgramCreator.AT_TANGENT");
        claim(ProgramCreator.AT_MIDBLOCK, "ProgramCreator.AT_MIDBLOCK");
    }

    @Test
    void instancedMatrixColumnsAreContiguous() {
        final int[] columns = {
            InstancedAttribs.LOC_MATRIX_COL0, InstancedAttribs.LOC_MATRIX_COL1,
            InstancedAttribs.LOC_MATRIX_COL2, InstancedAttribs.LOC_MATRIX_COL3
        };
        for (int c = 1; c < columns.length; c++) {
            assertEquals(columns[0] + c, columns[c],
                "instance matrix column " + c + " must sit at LOC_MATRIX_COL0 + " + c);
        }
    }

    @Test
    void reportsRemainingFreeLocations() {
        reservedAttributeLocationsDoNotOverlap();
        final TreeSet<Integer> free = new TreeSet<>();
        for (int i = 0; i < MAX_VERTEX_ATTRIBS; i++) {
            if (!claimed.containsKey(i)) free.add(i);
        }
        System.out.println("free vertex attribute locations: " + free);
    }
}
