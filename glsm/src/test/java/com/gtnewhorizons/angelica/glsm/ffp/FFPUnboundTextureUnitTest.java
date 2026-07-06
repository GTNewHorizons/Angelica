package com.gtnewhorizons.angelica.glsm.ffp;

import com.gtnewhorizons.angelica.glsm.GLSMCoreExtension;
import com.gtnewhorizons.angelica.glsm.GLStateManager;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.lwjgl.opengl.GL11;
import org.lwjgl.opengl.GL13;

import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

@ExtendWith(GLSMCoreExtension.class)
class FFPUnboundTextureUnitTest {

    private final List<Integer> texturesToDelete = new ArrayList<>();

    @AfterEach
    void cleanup() {
        for (int i = 3; i >= 0; i--) {
            GLStateManager.glActiveTexture(GL13.GL_TEXTURE0 + i);
            GLStateManager.glBindTexture(GL11.GL_TEXTURE_2D, 0);
            if (i > 0) GLStateManager.getTextures().getTextureUnitStates(i).disable();
        }
        GLStateManager.glActiveTexture(GL13.GL_TEXTURE0);
        for (int t : texturesToDelete) GL11.glDeleteTextures(t);
        texturesToDelete.clear();
    }

    private void bindUnitTexture() {
        final int tex = GL11.glGenTextures();
        texturesToDelete.add(tex);
        GLStateManager.glBindTexture(GL11.GL_TEXTURE_2D, tex);
    }

    @Test
    void enabledUnitWithoutTextureIsExcluded() {
        GLStateManager.glActiveTexture(GL13.GL_TEXTURE0);
        GLStateManager.enableTexture();
        bindUnitTexture();
        GLStateManager.glActiveTexture(GL13.GL_TEXTURE2);
        GLStateManager.enableTexture(); // enabled, nothing bound
        GLStateManager.glActiveTexture(GL13.GL_TEXTURE3);
        GLStateManager.enableTexture(); // enabled, nothing bound
        GLStateManager.glActiveTexture(GL13.GL_TEXTURE0);

        final FragmentKey fk = FragmentKey.fromState();
        assertEquals(1, fk.nrEnabledUnits(), "units 2/3 have no texture bound -> excluded -> only unit 0");
        assertEquals(0b0001, fk.enabledUnitMask(), "only unit 0 in the key; enabled-but-unbound units 2/3 excluded");
        assertTrue(fk.unitEnabled(0), "unit 0 (texture bound) stays enabled");
    }

    @Test
    void enabledUnitsWithTextureAreCounted() {
        GLStateManager.glActiveTexture(GL13.GL_TEXTURE0);
        GLStateManager.enableTexture();
        bindUnitTexture();
        GLStateManager.glActiveTexture(GL13.GL_TEXTURE2);
        GLStateManager.enableTexture();
        bindUnitTexture();
        GLStateManager.glActiveTexture(GL13.GL_TEXTURE0);

        final FragmentKey fk = FragmentKey.fromState();
        assertTrue(fk.unitEnabled(0));
        assertTrue(fk.unitEnabled(2), "unit 2 with a bound texture must be counted");
        assertEquals(3, fk.nrEnabledUnits(), "highest enabled is unit 2 -> nrEnabledUnits=3");
    }
}
