package com.gtnewhorizons.angelica.glsm.managers;

import com.gtnewhorizons.angelica.config.AngelicaConfig;
import com.gtnewhorizons.angelica.glsm.GLStateManager;
import com.gtnewhorizons.angelica.glsm.stacks.BooleanStateStack;
import com.gtnewhorizons.angelica.glsm.stacks.FogStateStack;
import lombok.Getter;
import net.coderbot.iris.gl.state.StateUpdateNotifiers;
import org.joml.Vector3d;
import org.lwjgl.opengl.GL11;

import java.nio.FloatBuffer;

public class GLFogManager {

    @Getter public static final FogStateStack fogState = new FogStateStack();
    @Getter public static final BooleanStateStack fogMode = new BooleanStateStack(GL11.GL_FOG);
    protected static Runnable fogToggleListener = null;
    protected static Runnable fogModeListener = null;
    protected static Runnable fogStartListener = null;
    protected static Runnable fogEndListener = null;
    protected static Runnable fogDensityListener = null;

    public static void minecraftInit() {
        if (AngelicaConfig.enableIris) {
            StateUpdateNotifiers.fogToggleNotifier = listener -> fogToggleListener = listener;
            StateUpdateNotifiers.fogModeNotifier = listener -> fogModeListener = listener;
            StateUpdateNotifiers.fogStartNotifier = listener -> fogStartListener = listener;
            StateUpdateNotifiers.fogEndNotifier = listener -> fogEndListener = listener;
            StateUpdateNotifiers.fogDensityNotifier = listener -> fogDensityListener = listener;
        }

    }

    public static void enableFog() {
        GLFogManager.fogMode.enable();
        if (GLFogManager.fogToggleListener != null) {
            GLFogManager.fogToggleListener.run();
        }
    }

    public static void disableFog() {
        GLFogManager.fogMode.disable();
        if (GLFogManager.fogToggleListener != null) {
            GLFogManager.fogToggleListener.run();
        }
    }

    public static void glFog(int pname, FloatBuffer param) {
        glFogfv(pname, param);
    }

    public static void glFogfv(int pname, FloatBuffer param) {
        // TODO: Iris Notifier
        if (GLStateManager.HAS_MULTIPLE_SET.contains(pname)) {
            GL11.glFogfv(pname, param);
            if (pname == GL11.GL_FOG_COLOR) {
                final float red = param.get(0);
                final float green = param.get(1);
                final float blue = param.get(2);

                fogState.getFogColor().set(red, green, blue);
                fogState.setFogAlpha(param.get(3));
                fogState.getFogColorBuffer().clear();
                fogState.getFogColorBuffer().put((FloatBuffer) param.position(0)).flip();
            }
        } else {
            glFogf(pname, param.get(0));
        }
    }

    public static Vector3d getFogColor() {
        return fogState.getFogColor();
    }

    public static void fogColor(float red, float green, float blue, float alpha) {
        if (GLStateManager.shouldBypassCache() || red != fogState.getFogColor().x || green != fogState.getFogColor().y || blue != fogState.getFogColor().z || alpha != fogState.getFogAlpha()) {
            fogState.getFogColor().set(red, green, blue);
            fogState.setFogAlpha(alpha);
            fogState.getFogColorBuffer().clear();
            fogState.getFogColorBuffer().put(red).put(green).put(blue).put(alpha).flip();
            GL11.glFogfv(GL11.GL_FOG_COLOR, fogState.getFogColorBuffer());
        }
    }

    public static void glFogf(int pname, float param) {
        GL11.glFogf(pname, param);
        // Note: Does not handle GL_FOG_INDEX
        switch (pname) {
            case GL11.GL_FOG_DENSITY -> {
                fogState.setDensity(param);
                if (fogDensityListener != null) {
                    fogDensityListener.run();
                }
            }
            case GL11.GL_FOG_START -> {
                fogState.setStart(param);
                if (fogStartListener != null) {
                    fogStartListener.run();
                }
            }
            case GL11.GL_FOG_END -> {
                fogState.setEnd(param);
                if (fogEndListener != null) {
                    fogEndListener.run();
                }
            }
        }
    }

    public static void glFogi(int pname, int param) {
        GL11.glFogi(pname, param);
        if (pname == GL11.GL_FOG_MODE) {
            fogState.setFogMode(param);
            if (fogModeListener != null) {
                fogModeListener.run();
            }
        }
    }

    public static void setFogBlack() {
        glFogf(GL11.GL_FOG_COLOR, 0.0F);
    }
}
