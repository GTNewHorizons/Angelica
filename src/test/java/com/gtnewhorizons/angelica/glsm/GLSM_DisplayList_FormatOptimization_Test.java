package com.gtnewhorizons.angelica.glsm;

import com.gtnewhorizons.angelica.AngelicaExtension;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.lwjgl.opengl.GL11;

import static com.gtnewhorizons.angelica.util.GLSMUtil.verifyState;

/**
 * Tests for display list format optimization.
 * Verifies that display lists work correctly with different vertex attribute combinations
 * and that optimal formats are selected.
 */
@ExtendWith(AngelicaExtension.class)
class GLSM_DisplayList_FormatOptimization_Test {

    private int displayList = -1;

    @AfterEach
    void cleanup() {
        if (displayList > 0) {
            GLStateManager.glDeleteLists(displayList, 1);
            displayList = -1;
        }
        // Reset GL state to avoid test pollution
        GLStateManager.glColor4f(1.0f, 1.0f, 1.0f, 1.0f);
        GLStateManager.glTexCoord2f(0.0f, 0.0f);
        GLStateManager.glNormal3f(0.0f, 0.0f, 1.0f);
    }

    @Test
    void testPositionOnlyFormat() {
        // Create a display list with only position data (no color, texture, brightness, or normals)
        // Use GLStateManager for all calls to ensure they go through GLSM (test env doesn't redirect GL11)
        displayList = GL11.glGenLists(1);
        GLStateManager.glNewList(displayList, GL11.GL_COMPILE);

        GLStateManager.glBegin(GL11.GL_QUADS);
        GLStateManager.glVertex3f(0, 0, 0);
        GLStateManager.glVertex3f(1, 0, 0);
        GLStateManager.glVertex3f(1, 1, 0);
        GLStateManager.glVertex3f(0, 1, 0);
        GLStateManager.glEnd();

        GLStateManager.glEndList();

        // Call the list to ensure it doesn't crash (verifies compilation succeeded)
        GLStateManager.glCallList(displayList);
    }

    @Test
    void testPositionTextureFormat() {
        // Create a display list with position and texture coordinates
        displayList = GL11.glGenLists(1);
        GLStateManager.glNewList(displayList, GL11.GL_COMPILE);

        GLStateManager.glBegin(GL11.GL_QUADS);
        GLStateManager.glTexCoord2f(0, 0);
        GLStateManager.glVertex3f(0, 0, 0);
        GLStateManager.glTexCoord2f(1, 0);
        GLStateManager.glVertex3f(1, 0, 0);
        GLStateManager.glTexCoord2f(1, 1);
        GLStateManager.glVertex3f(1, 1, 0);
        GLStateManager.glTexCoord2f(0, 1);
        GLStateManager.glVertex3f(0, 1, 0);
        GLStateManager.glEnd();

        GLStateManager.glEndList();

        // Call the list to ensure it doesn't crash (verifies compilation succeeded)
        GLStateManager.glCallList(displayList);
    }

    @Test
    void testPositionColorTextureFormat() {
        // Create a display list with position, color, and texture
        displayList = GL11.glGenLists(1);
        GLStateManager.glNewList(displayList, GL11.GL_COMPILE);

        GLStateManager.glBegin(GL11.GL_QUADS);
        GLStateManager.glColor4f(1, 1, 1, 1);
        GLStateManager.glTexCoord2f(0, 0);
        GLStateManager.glVertex3f(0, 0, 0);
        GLStateManager.glColor4f(1, 0, 0, 1);
        GLStateManager.glTexCoord2f(1, 0);
        GLStateManager.glVertex3f(1, 0, 0);
        GLStateManager.glColor4f(0, 1, 0, 1);
        GLStateManager.glTexCoord2f(1, 1);
        GLStateManager.glVertex3f(1, 1, 0);
        GLStateManager.glColor4f(0, 0, 1, 1);
        GLStateManager.glTexCoord2f(0, 1);
        GLStateManager.glVertex3f(0, 1, 0);
        GLStateManager.glEnd();

        GLStateManager.glEndList();

        // Call the list and verify color is restored to last value (blue)
        GLStateManager.glCallList(displayList);
        verifyState(GL11.GL_CURRENT_COLOR, new float[]{0f, 0f, 1f, 1f},
            "After playback, color should be blue (last color in immediate mode block)");
    }

    @Test
    void testFullFormat() {
        // Create a display list with all attributes: position, color, texture, and normal
        displayList = GL11.glGenLists(1);
        GLStateManager.glNewList(displayList, GL11.GL_COMPILE);

        GLStateManager.glBegin(GL11.GL_QUADS);
        GLStateManager.glNormal3f(0, 0, 1);
        GLStateManager.glColor4f(1, 1, 1, 1);
        GLStateManager.glTexCoord2f(0, 0);
        GLStateManager.glVertex3f(0, 0, 0);
        GLStateManager.glNormal3f(0, 0, 1);
        GLStateManager.glColor4f(1, 0, 0, 1);
        GLStateManager.glTexCoord2f(1, 0);
        GLStateManager.glVertex3f(1, 0, 0);
        GLStateManager.glNormal3f(0, 0, 1);
        GLStateManager.glColor4f(0, 1, 0, 1);
        GLStateManager.glTexCoord2f(1, 1);
        GLStateManager.glVertex3f(1, 1, 0);
        GLStateManager.glNormal3f(0, 0, 1);
        GLStateManager.glColor4f(0, 0, 1, 1);
        GLStateManager.glTexCoord2f(0, 1);
        GLStateManager.glVertex3f(0, 1, 0);
        GLStateManager.glEnd();

        GLStateManager.glEndList();

        // Call the list and verify color is restored to last value (blue)
        GLStateManager.glCallList(displayList);
        verifyState(GL11.GL_CURRENT_COLOR, new float[]{0f, 0f, 1f, 1f},
            "After playback, color should be blue (last color in immediate mode block)");
    }

    @Test
    void testMultipleDrawsInDisplayList() {
        // Test a display list with multiple draws with different formats
        displayList = GL11.glGenLists(1);
        GLStateManager.glNewList(displayList, GL11.GL_COMPILE);

        // First draw: position only
        GLStateManager.glBegin(GL11.GL_QUADS);
        GLStateManager.glVertex3f(0, 0, 0);
        GLStateManager.glVertex3f(1, 0, 0);
        GLStateManager.glVertex3f(1, 1, 0);
        GLStateManager.glVertex3f(0, 1, 0);
        GLStateManager.glEnd();

        // Second draw: position + color
        GLStateManager.glBegin(GL11.GL_QUADS);
        GLStateManager.glColor4f(1, 0, 0, 1);
        GLStateManager.glVertex3f(2, 0, 0);
        GLStateManager.glColor4f(0, 1, 0, 1);
        GLStateManager.glVertex3f(3, 0, 0);
        GLStateManager.glColor4f(0, 0, 1, 1);
        GLStateManager.glVertex3f(3, 1, 0);
        GLStateManager.glColor4f(1, 1, 0, 1);
        GLStateManager.glVertex3f(2, 1, 0);
        GLStateManager.glEnd();

        // Third draw: position + texture
        GLStateManager.glBegin(GL11.GL_QUADS);
        GLStateManager.glTexCoord2f(0, 0);
        GLStateManager.glVertex3f(4, 0, 0);
        GLStateManager.glTexCoord2f(1, 0);
        GLStateManager.glVertex3f(5, 0, 0);
        GLStateManager.glTexCoord2f(1, 1);
        GLStateManager.glVertex3f(5, 1, 0);
        GLStateManager.glTexCoord2f(0, 1);
        GLStateManager.glVertex3f(4, 1, 0);
        GLStateManager.glEnd();

        GLStateManager.glEndList();

        // Call the list and verify color is restored to last value from second draw (yellow)
        // Third draw has no color, so doesn't affect GL_CURRENT_COLOR
        GLStateManager.glCallList(displayList);
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
        GLStateManager.glColor4f(1.0f, 1.0f, 1.0f, 1.0f);
        verifyState(GL11.GL_CURRENT_COLOR, new float[]{1f, 1f, 1f, 1f}, "Initial color should be white");

        // Create display list with immediate mode that sets color to BLUE (0, 0, 1, 1)

        displayList = GL11.glGenLists(1);
        GLStateManager.glNewList(displayList, GL11.GL_COMPILE);

        GLStateManager.glBegin(GL11.GL_QUADS);
        GLStateManager.glColor4f(0.0f, 0.0f, 1.0f, 1.0f);  // Blue
        GLStateManager.glVertex3f(0, 0, 0);
        GLStateManager.glVertex3f(1, 0, 0);
        GLStateManager.glVertex3f(1, 1, 0);
        GLStateManager.glVertex3f(0, 1, 0);
        GLStateManager.glEnd();

        GLStateManager.glEndList();

        // Call the display list
        GLStateManager.glCallList(displayList);

        // After playback, GL_CURRENT_COLOR should be blue (the last color in the display list)
        // AND the GLSM cache should also be blue (synced with GL)
        verifyState(GL11.GL_CURRENT_COLOR, new float[]{0f, 0f, 1f, 1f},
            "After display list playback, color should be blue (last color in the list)");

        // Now set color to red - this should ACTUALLY change the color
        GLStateManager.glColor4f(1.0f, 0.0f, 0.0f, 1.0f);
        verifyState(GL11.GL_CURRENT_COLOR, new float[]{1f, 0f, 0f, 1f},
            "After setting color to red, color should be red");
    }
}
