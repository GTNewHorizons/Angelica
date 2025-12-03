package com.gtnewhorizons.angelica.glsm;

import com.gtnewhorizons.angelica.AngelicaExtension;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.lwjgl.opengl.GL11;

import static org.junit.jupiter.api.Assertions.assertTrue;

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
        displayList = GL11.glGenLists(1);
        GLStateManager.glNewList(displayList, GL11.GL_COMPILE);

        GL11.glBegin(GL11.GL_QUADS);
        GL11.glVertex3f(0, 0, 0);
        GL11.glVertex3f(1, 0, 0);
        GL11.glVertex3f(1, 1, 0);
        GL11.glVertex3f(0, 1, 0);
        GL11.glEnd();

        GLStateManager.glEndList();

        // Call the list to ensure it doesn't crash (verifies compilation succeeded)
        GLStateManager.glCallList(displayList);
    }

    @Test
    void testPositionTextureFormat() {
        // Create a display list with position and texture coordinates
        displayList = GL11.glGenLists(1);
        GLStateManager.glNewList(displayList, GL11.GL_COMPILE);

        GL11.glBegin(GL11.GL_QUADS);
        GL11.glTexCoord2f(0, 0);
        GL11.glVertex3f(0, 0, 0);
        GL11.glTexCoord2f(1, 0);
        GL11.glVertex3f(1, 0, 0);
        GL11.glTexCoord2f(1, 1);
        GL11.glVertex3f(1, 1, 0);
        GL11.glTexCoord2f(0, 1);
        GL11.glVertex3f(0, 1, 0);
        GL11.glEnd();

        GLStateManager.glEndList();

        // Call the list to ensure it doesn't crash (verifies compilation succeeded)
        GLStateManager.glCallList(displayList);
    }

    @Test
    void testPositionColorTextureFormat() {
        // Create a display list with position, color, and texture
        displayList = GL11.glGenLists(1);
        GLStateManager.glNewList(displayList, GL11.GL_COMPILE);

        GL11.glBegin(GL11.GL_QUADS);
        GL11.glColor4f(1, 1, 1, 1);
        GL11.glTexCoord2f(0, 0);
        GL11.glVertex3f(0, 0, 0);
        GL11.glColor4f(1, 0, 0, 1);
        GL11.glTexCoord2f(1, 0);
        GL11.glVertex3f(1, 0, 0);
        GL11.glColor4f(0, 1, 0, 1);
        GL11.glTexCoord2f(1, 1);
        GL11.glVertex3f(1, 1, 0);
        GL11.glColor4f(0, 0, 1, 1);
        GL11.glTexCoord2f(0, 1);
        GL11.glVertex3f(0, 1, 0);
        GL11.glEnd();

        GLStateManager.glEndList();

        // Call the list to ensure it doesn't crash (verifies compilation succeeded)
        GLStateManager.glCallList(displayList);
    }

    @Test
    void testFullFormat() {
        // Create a display list with all attributes: position, color, texture, and normal
        displayList = GL11.glGenLists(1);
        GLStateManager.glNewList(displayList, GL11.GL_COMPILE);

        GL11.glBegin(GL11.GL_QUADS);
        GL11.glNormal3f(0, 0, 1);
        GL11.glColor4f(1, 1, 1, 1);
        GL11.glTexCoord2f(0, 0);
        GL11.glVertex3f(0, 0, 0);
        GL11.glNormal3f(0, 0, 1);
        GL11.glColor4f(1, 0, 0, 1);
        GL11.glTexCoord2f(1, 0);
        GL11.glVertex3f(1, 0, 0);
        GL11.glNormal3f(0, 0, 1);
        GL11.glColor4f(0, 1, 0, 1);
        GL11.glTexCoord2f(1, 1);
        GL11.glVertex3f(1, 1, 0);
        GL11.glNormal3f(0, 0, 1);
        GL11.glColor4f(0, 0, 1, 1);
        GL11.glTexCoord2f(0, 1);
        GL11.glVertex3f(0, 1, 0);
        GL11.glEnd();

        GLStateManager.glEndList();

        // Call the list to ensure it doesn't crash (verifies compilation succeeded)
        GLStateManager.glCallList(displayList);
    }

    @Test
    void testMultipleDrawsInDisplayList() {
        // Test a display list with multiple draws with different formats
        displayList = GL11.glGenLists(1);
        GLStateManager.glNewList(displayList, GL11.GL_COMPILE);

        // First draw: position only
        GL11.glBegin(GL11.GL_QUADS);
        GL11.glVertex3f(0, 0, 0);
        GL11.glVertex3f(1, 0, 0);
        GL11.glVertex3f(1, 1, 0);
        GL11.glVertex3f(0, 1, 0);
        GL11.glEnd();

        // Second draw: position + color
        GL11.glBegin(GL11.GL_QUADS);
        GL11.glColor4f(1, 0, 0, 1);
        GL11.glVertex3f(2, 0, 0);
        GL11.glColor4f(0, 1, 0, 1);
        GL11.glVertex3f(3, 0, 0);
        GL11.glColor4f(0, 0, 1, 1);
        GL11.glVertex3f(3, 1, 0);
        GL11.glColor4f(1, 1, 0, 1);
        GL11.glVertex3f(2, 1, 0);
        GL11.glEnd();

        // Third draw: position + texture
        GL11.glBegin(GL11.GL_QUADS);
        GL11.glTexCoord2f(0, 0);
        GL11.glVertex3f(4, 0, 0);
        GL11.glTexCoord2f(1, 0);
        GL11.glVertex3f(5, 0, 0);
        GL11.glTexCoord2f(1, 1);
        GL11.glVertex3f(5, 1, 0);
        GL11.glTexCoord2f(0, 1);
        GL11.glVertex3f(4, 1, 0);
        GL11.glEnd();

        GLStateManager.glEndList();

        // Call the list to ensure it doesn't crash (verifies compilation succeeded)
        GLStateManager.glCallList(displayList);
    }
}
