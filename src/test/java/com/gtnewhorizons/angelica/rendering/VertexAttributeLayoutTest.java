package com.gtnewhorizons.angelica.rendering;

import com.gtnewhorizon.gtnhlib.client.renderer.vertex.VertexFormatElement;
import com.gtnewhorizons.angelica.glsm.ffp.CubeInstancedAttribs;
import com.gtnewhorizons.angelica.glsm.ffp.InstancedAttribs;
import com.gtnewhorizons.angelica.glsm.hooks.ImmediateExtendedAttribHandler;
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

        claim(InstancedAttribs.LOC_ROW0, "InstancedAttribs.LOC_ROW0");
        claim(InstancedAttribs.LOC_ROW1, "InstancedAttribs.LOC_ROW1");
        claim(InstancedAttribs.LOC_ROW2, "InstancedAttribs.LOC_ROW2");
        claim(InstancedAttribs.LOC_COLOR, "InstancedAttribs.LOC_COLOR");
        claim(InstancedAttribs.LOC_OVERLAY, "InstancedAttribs.LOC_OVERLAY");
        claim(InstancedAttribs.LOC_LIGHTMAP, "InstancedAttribs.LOC_LIGHTMAP");
        claim(InstancedAttribs.LOC_ENTITY, "InstancedAttribs.LOC_ENTITY");

        claim(ProgramCreator.MC_MID_TEX_COORD, "ProgramCreator.MC_MID_TEX_COORD");
        claim(ProgramCreator.AT_TANGENT, "ProgramCreator.AT_TANGENT");
        claim(ProgramCreator.AT_MIDBLOCK, "ProgramCreator.AT_MIDBLOCK");

        claim(CubeInstancedAttribs.LOC_CUBE_TEX, "CubeInstancedAttribs.LOC_CUBE_TEX");
    }

    @Test
    void instancedEntitySharesLocationWithImmediateMcEntity() {
        assertEquals(InstancedAttribs.LOC_ENTITY, ImmediateExtendedAttribHandler.LOC_MC_ENTITY, "instanced entity info and immediate mc_Entity share a location; they are never bound in the same draw");
        assertEquals(InstancedAttribs.LOC_ENTITY, ProgramCreator.MC_ENTITY);
    }

    @Test
    void instancedRowsAreContiguous() {
        assertEquals(InstancedAttribs.LOC_ROW0 + 1, InstancedAttribs.LOC_ROW1, "instance row 1 must sit at LOC_ROW0 + 1");
        assertEquals(InstancedAttribs.LOC_ROW0 + 2, InstancedAttribs.LOC_ROW2, "instance row 2 must sit at LOC_ROW0 + 2");
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
