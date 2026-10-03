package com.gtnewhorizons.angelica.sdlgpu.resource;

import org.junit.jupiter.api.Test;
import org.lwjgl.opengl.GL11;
import org.lwjgl.opengl.GL12;
import org.lwjgl.opengl.GL14;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class TextureSamplerStateTest {

    @Test
    void changedIntValueReportsChange() {
        final TextureSamplerState ss = new TextureSamplerState();
        assertTrue(ss.seti(GL11.GL_TEXTURE_MIN_FILTER, GL11.GL_LINEAR));
        assertEquals(GL11.GL_LINEAR, ss.minFilter);
        assertFalse(ss.seti(GL11.GL_TEXTURE_MIN_FILTER, GL11.GL_LINEAR));
    }

    @Test
    void changedFloatValueReportsChange() {
        final TextureSamplerState ss = new TextureSamplerState();
        assertTrue(ss.setf(GL14.GL_TEXTURE_LOD_BIAS, 0.5f));
        assertEquals(0.5f, ss.lodBias);
        assertFalse(ss.setf(GL14.GL_TEXTURE_LOD_BIAS, 0.5f));
    }

    @Test
    void setfFallsThroughToIntPname() {
        final TextureSamplerState ss = new TextureSamplerState();
        assertTrue(ss.setf(GL11.GL_TEXTURE_MIN_FILTER, (float) GL11.GL_LINEAR));
        assertEquals(GL11.GL_LINEAR, ss.minFilter);
        assertFalse(ss.setf(GL11.GL_TEXTURE_MIN_FILTER, (float) GL11.GL_LINEAR));
    }

    @Test
    void setiRoutesLodToTheFloatField() {
        final TextureSamplerState ss = new TextureSamplerState();
        assertTrue(ss.seti(GL12.GL_TEXTURE_MIN_LOD, 2));
        assertEquals(2.0f, ss.minLod);
        assertFalse(ss.seti(GL12.GL_TEXTURE_MIN_LOD, 2));
    }

    @Test
    void maxLevelIsStoredButNeverAffectsTheSampler() {
        final TextureSamplerState ss = new TextureSamplerState();
        assertFalse(ss.seti(GL12.GL_TEXTURE_MAX_LEVEL, 3));
        assertEquals(3, ss.maxLevel);
    }
}
