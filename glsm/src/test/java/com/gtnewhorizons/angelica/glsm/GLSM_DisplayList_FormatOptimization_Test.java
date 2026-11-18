package com.gtnewhorizons.angelica.glsm;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.lwjgl.opengl.GL11;

import static com.gtnewhorizons.angelica.util.GLSMUtil.verifyState;

/**
 * Tests for display list format optimization.
 * Verifies that display lists work correctly with different vertex attribute combinations
 * and that optimal formats are selected.
 */
@GLCompatTest
class GLSM_DisplayList_FormatOptimization_Test {

    private int displayList = -1;

    @AfterEach
    void cleanup() {
        if (displayList > 0) {
            GLSMDraws.glDeleteLists(displayList, 1);
            displayList = -1;
        }
        // Reset GL state to avoid test pollution
        GLSMCurrentAttribs.glColor4f(1.0f, 1.0f, 1.0f, 1.0f);
        GLSMCurrentAttribs.glTexCoord2f(0.0f, 0.0f);
        GLSMCurrentAttribs.glNormal3f(0.0f, 0.0f, 1.0f);
    }

    private static final float[][] CORNERS = {{0, 0}, {1, 0}, {1, 1}, {0, 1}};
    private static final float[][] COLORS = {{1, 1, 1, 1}, {1, 0, 0, 1}, {0, 1, 0, 1}, {0, 0, 1, 1}};

    @ParameterizedTest(name = "normals={0}")
    @ValueSource(booleans = {false, true})
    void colorTextureFormatLeavesLastColorCurrent(boolean normals) {
        displayList = GL11.glGenLists(1);
        GLSMDraws.glNewList(displayList, GL11.GL_COMPILE);

        GLSMDraws.glBegin(GL11.GL_QUADS);
        for (int i = 0; i < 4; i++) {
            if (normals) GLSMCurrentAttribs.glNormal3f(0, 0, 1);
            GLSMCurrentAttribs.glColor4f(COLORS[i][0], COLORS[i][1], COLORS[i][2], COLORS[i][3]);
            GLSMCurrentAttribs.glTexCoord2f(CORNERS[i][0], CORNERS[i][1]);
            GLSMDraws.glVertex3f(CORNERS[i][0], CORNERS[i][1], 0);
        }
        GLSMDraws.glEnd();

        GLSMDraws.glEndList();

        GLSMDraws.glCallList(displayList);
        verifyState(GL11.GL_CURRENT_COLOR, new float[]{0f, 0f, 1f, 1f},
            "After playback, color should be blue (last color in immediate mode block)");
    }

    @Test
    void testMultipleDrawsInDisplayList() {
        // Test a display list with multiple draws with different formats
        displayList = GL11.glGenLists(1);
        GLSMDraws.glNewList(displayList, GL11.GL_COMPILE);

        // First draw: position only
        DisplayListTestHelper.immediateQuad(1.0f);

        // Second draw: position + color
        GLSMDraws.glBegin(GL11.GL_QUADS);
        GLSMCurrentAttribs.glColor4f(1, 0, 0, 1);
        GLSMDraws.glVertex3f(2, 0, 0);
        GLSMCurrentAttribs.glColor4f(0, 1, 0, 1);
        GLSMDraws.glVertex3f(3, 0, 0);
        GLSMCurrentAttribs.glColor4f(0, 0, 1, 1);
        GLSMDraws.glVertex3f(3, 1, 0);
        GLSMCurrentAttribs.glColor4f(1, 1, 0, 1);
        GLSMDraws.glVertex3f(2, 1, 0);
        GLSMDraws.glEnd();

        // Third draw: position + texture
        GLSMDraws.glBegin(GL11.GL_QUADS);
        GLSMCurrentAttribs.glTexCoord2f(0, 0);
        GLSMDraws.glVertex3f(4, 0, 0);
        GLSMCurrentAttribs.glTexCoord2f(1, 0);
        GLSMDraws.glVertex3f(5, 0, 0);
        GLSMCurrentAttribs.glTexCoord2f(1, 1);
        GLSMDraws.glVertex3f(5, 1, 0);
        GLSMCurrentAttribs.glTexCoord2f(0, 1);
        GLSMDraws.glVertex3f(4, 1, 0);
        GLSMDraws.glEnd();

        GLSMDraws.glEndList();

        // Call the list and verify color is restored to last value from second draw (yellow)
        // Third draw has no color, so doesn't affect GL_CURRENT_COLOR
        GLSMDraws.glCallList(displayList);
        verifyState(GL11.GL_CURRENT_COLOR, new float[]{1f, 1f, 0f, 1f},
            "After playback, color should be yellow (last color from second draw)");
    }

    /**
     * Verifies that color is properly restored after immediate mode VBO playback.
     * After glCallList, GL_CURRENT_COLOR should be the last color set in the display list.
     */
    @Test
    void testColorRestoredAfterImmediateModeVBOPlayback() {
        // Set initial color to white
        GLSMCurrentAttribs.glColor4f(1.0f, 1.0f, 1.0f, 1.0f);
        verifyState(GL11.GL_CURRENT_COLOR, new float[]{1f, 1f, 1f, 1f}, "Initial color should be white");

        // Create display list with immediate mode that sets color to BLUE (0, 0, 1, 1)

        displayList = GL11.glGenLists(1);
        GLSMDraws.glNewList(displayList, GL11.GL_COMPILE);

        GLSMDraws.glBegin(GL11.GL_QUADS);
        GLSMCurrentAttribs.glColor4f(0.0f, 0.0f, 1.0f, 1.0f);  // Blue
        GLSMDraws.glVertex3f(0, 0, 0);
        GLSMDraws.glVertex3f(1, 0, 0);
        GLSMDraws.glVertex3f(1, 1, 0);
        GLSMDraws.glVertex3f(0, 1, 0);
        GLSMDraws.glEnd();

        GLSMDraws.glEndList();

        // Call the display list
        GLSMDraws.glCallList(displayList);

        // After playback, GL_CURRENT_COLOR should be blue (the last color in the display list)
        // AND the GLSM cache should also be blue (synced with GL)
        verifyState(GL11.GL_CURRENT_COLOR, new float[]{0f, 0f, 1f, 1f},
            "After display list playback, color should be blue (last color in the list)");

        // Now set color to red - this should ACTUALLY change the color
        GLSMCurrentAttribs.glColor4f(1.0f, 0.0f, 0.0f, 1.0f);
        verifyState(GL11.GL_CURRENT_COLOR, new float[]{1f, 0f, 0f, 1f},
            "After setting color to red, color should be red");
    }
}
