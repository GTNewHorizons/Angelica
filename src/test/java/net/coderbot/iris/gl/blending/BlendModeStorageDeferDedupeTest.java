package net.coderbot.iris.gl.blending;

import com.gtnewhorizons.angelica.glsm.states.BlendState;
import com.gtnewhorizons.angelica.glsm.testutil.Reflect;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.lwjgl.opengl.GL11;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class BlendModeStorageDeferDedupeTest {

    @BeforeEach
    @AfterEach
    void resetStorage() {
        BlendModeStorage.restoreBlend();
        Reflect.setStatic(BlendModeStorage.class, "hasDeferredChanges", false);
        Reflect.setStatic(BlendModeStorage.class, "vanillaBlendEnable", false);
        vanillaBlend().setAll(GL11.GL_ONE, GL11.GL_ZERO, GL11.GL_ONE, GL11.GL_ZERO);
    }

    private static BlendState vanillaBlend() {
        return Reflect.getStatic(BlendModeStorage.class, "vanillaBlend");
    }

    private static boolean hasDeferredChanges() {
        return Reflect.<Boolean>getStatic(BlendModeStorage.class, "hasDeferredChanges");
    }

    @Test
    void blendFuncDefersOnlyOnChange() {
        BlendModeStorage.deferBlendFunc(GL11.GL_ONE, GL11.GL_ZERO, GL11.GL_ONE, GL11.GL_ZERO);
        assertFalse(hasDeferredChanges());

        BlendModeStorage.deferBlendFunc(GL11.GL_SRC_ALPHA, GL11.GL_ONE_MINUS_SRC_ALPHA, GL11.GL_ONE, GL11.GL_ZERO);
        assertTrue(hasDeferredChanges());
        assertEquals(GL11.GL_SRC_ALPHA, vanillaBlend().getSrcRgb());
        assertEquals(GL11.GL_ONE_MINUS_SRC_ALPHA, vanillaBlend().getDstRgb());

        BlendModeStorage.deferBlendFunc(GL11.GL_ONE, GL11.GL_ZERO, GL11.GL_ONE, GL11.GL_ZERO);
        assertTrue(hasDeferredChanges());
        assertEquals(GL11.GL_ONE, vanillaBlend().getSrcRgb());
    }

    @Test
    void toggleDefersOnlyOnChange() {
        BlendModeStorage.deferBlendModeToggle(false);
        assertFalse(hasDeferredChanges());

        BlendModeStorage.deferBlendModeToggle(true);
        assertTrue(hasDeferredChanges());
        assertTrue(Reflect.<Boolean>getStatic(BlendModeStorage.class, "vanillaBlendEnable"));
    }
}
