package com.gtnewhorizons.angelica.glsm;

import org.lwjgl.opengl.GL11;
import org.lwjgl.opengl.GL13;
import org.lwjgl.opengl.GL14;
import org.lwjgl.opengl.GL20;

import java.util.Arrays;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

public final class LegacyQueryFixture {

    public static final int[] CLIENT_ARRAY_CAPS = {
        GL11.GL_VERTEX_ARRAY, GL11.GL_NORMAL_ARRAY, GL11.GL_COLOR_ARRAY, GL11.GL_TEXTURE_COORD_ARRAY,
        GL11.GL_INDEX_ARRAY, GL11.GL_EDGE_FLAG_ARRAY, GL14.GL_SECONDARY_COLOR_ARRAY, GL14.GL_FOG_COORDINATE_ARRAY
    };

    public static final int[] HINT_TARGETS = {
        GL11.GL_PERSPECTIVE_CORRECTION_HINT, GL11.GL_POINT_SMOOTH_HINT, GL11.GL_LINE_SMOOTH_HINT, GL11.GL_POLYGON_SMOOTH_HINT,
        GL11.GL_FOG_HINT, GL13.GL_TEXTURE_COMPRESSION_HINT, GL14.GL_GENERATE_MIPMAP_HINT, GL20.GL_FRAGMENT_SHADER_DERIVATIVE_HINT
    };

    private LegacyQueryFixture() {}

    /** Every pname LegacyQueries answers from the cache. */
    public static int[] legacyPnames() {
        final int[] out = new int[0x10000];
        int n = 0;
        for (int pname = 0; pname <= 0xFFFF; pname++) {
            if (LegacyQueries.desc(pname) != 0) {
                out[n++] = pname;
            }
        }
        return Arrays.copyOf(out, n);
    }

    public static String hex(int pname) {
        return "0x" + Integer.toHexString(pname);
    }

    public static void setEnabled(int cap, boolean on) {
        if (on) {
            GLSMEnable.glEnable(cap);
        } else {
            GLSMEnable.glDisable(cap);
        }
    }

    public static void popAttribTo(int base) {
        while (GLSMAttribStack.getAttribDepth() > base) {
            GLSMAttribStack.glPopAttrib();
        }
    }

    public static void drainClientAttribStack() {
        while (GLSMQueries.glGetInteger(GL11.GL_CLIENT_ATTRIB_STACK_DEPTH) > 0) {
            GLSMAttribStack.glPopClientAttrib();
        }
    }

    /** Enables then disables {@code cap}, checking glIsEnabled, glGetBoolean and glGetInteger at each step. */
    public static void assertEnableRoundTrip(int cap, boolean clientState) {
        final String name = hex(cap);
        if (clientState) {
            GLSMVertexInput.glEnableClientState(cap);
        } else {
            GLSMEnable.glEnable(cap);
        }
        assertTrue(GLSMEnable.glIsEnabled(cap), "enabled isEnabled " + name);
        assertTrue(GLSMQueries.glGetBoolean(cap), "enabled getBoolean " + name);
        assertEquals(1, GLSMQueries.glGetInteger(cap), "enabled getInteger " + name);
        if (clientState) {
            GLSMVertexInput.glDisableClientState(cap);
        } else {
            GLSMEnable.glDisable(cap);
        }
        assertFalse(GLSMEnable.glIsEnabled(cap), "disabled isEnabled " + name);
        assertFalse(GLSMQueries.glGetBoolean(cap), "disabled getBoolean " + name);
        assertEquals(0, GLSMQueries.glGetInteger(cap), "disabled getInteger " + name);
    }
}
