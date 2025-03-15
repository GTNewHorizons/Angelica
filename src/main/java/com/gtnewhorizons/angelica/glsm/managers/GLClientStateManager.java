package com.gtnewhorizons.angelica.glsm.managers;

import com.gtnewhorizons.angelica.glsm.GLStateManager;
import com.gtnewhorizons.angelica.glsm.stacks.BooleanStateStack;
import lombok.Getter;
import org.lwjgl.opengl.GL11;

@SuppressWarnings("unused") // Called via ASM
public class GLClientStateManager {

    @Getter
    protected static final BooleanStateStack vertexArrayState = new BooleanStateStack(GL11.GL_VERTEX_ARRAY);
    @Getter protected static final BooleanStateStack normalArrayState = new BooleanStateStack(GL11.GL_NORMAL_ARRAY);

    public static void glEnableClientState(int cap) {
        final boolean changed;
        switch (cap) {
            case GL11.GL_VERTEX_ARRAY:
                changed = !vertexArrayState.isEnabled();
                vertexArrayState.enable();
                break;
            case GL11.GL_NORMAL_ARRAY:
                changed = !normalArrayState.isEnabled();
                normalArrayState.enable();
                break;
            case GL11.GL_COLOR_ARRAY:
                changed = !GLLightingManager.colorArrayState.isEnabled();
                GLLightingManager.colorArrayState.enable();
                break;
            case GL11.GL_TEXTURE_COORD_ARRAY:
                changed = !GLLightingManager.texCoordArrayState.isEnabled();
                GLLightingManager.texCoordArrayState.enable();
                break;
            default:
                // For any other capabilities, always call the GL method
                changed = true;
                break;
        }

        // Only make the GL call if the state actually changed or we want to bypass the cache
        if (changed || GLStateManager.shouldBypassCache()) {
            GL11.glEnableClientState(cap);
        }
    }

    public static void glDisableClientState(int cap) {
        final boolean changed;
        switch (cap) {
            case GL11.GL_VERTEX_ARRAY:
                changed = vertexArrayState.isEnabled();
                vertexArrayState.disable();
                break;
            case GL11.GL_NORMAL_ARRAY:
                changed = normalArrayState.isEnabled();
                normalArrayState.disable();
                break;
            case GL11.GL_COLOR_ARRAY:
                changed = GLLightingManager.colorArrayState.isEnabled();
                GLLightingManager.colorArrayState.disable();
                break;
            case GL11.GL_TEXTURE_COORD_ARRAY:
                changed = GLLightingManager.texCoordArrayState.isEnabled();
                GLLightingManager.texCoordArrayState.disable();
                break;
            default:
                // For any other capabilities, always call the GL method
                changed = true;
                break;
        }

        // Only make the GL call if the state actually changed or we want to bypass the cache
        if (changed || GLStateManager.shouldBypassCache()) {
            GL11.glDisableClientState(cap);
        }
    }
}
