package com.gtnewhorizons.angelica.stereo;

import com.gtnewhorizons.angelica.glsm.hooks.GLSMHooks;
import com.gtnewhorizons.angelica.glsm.hooks.StereoHook;
import net.minecraft.client.Minecraft;
import org.lwjgl.input.Mouse;

public final class StereoGLSMBridge {

    private StereoGLSMBridge() {}

    public static void register() {
        GLSMHooks.stereoHook = new StereoHook() {
            @Override
            public boolean remapScissor(int x, int y, int width, int height, int[] out) {
                final StereoState state = StereoState.INSTANCE;
                if (!state.isInGuiPass()) return false;
                final Minecraft mc = Minecraft.getMinecraft();
                final int displayW = mc.displayWidth;
                final int displayH = mc.displayHeight;
                if (displayW <= 0 || displayH <= 0) return false;
                final int vpX = state.getEyeVpX();
                final int vpY = state.getEyeVpY();
                final int vpW = state.getEyeVpW();
                final int vpH = state.getEyeVpH();
                out[0] = vpX + (int)((long) x * vpW / displayW);
                out[1] = vpY + (int)((long) y * vpH / displayH);
                out[2] = (int)((long) width  * vpW / displayW);
                out[3] = (int)((long) height * vpH / displayH);
                return true;
            }

            @Override
            public boolean remapWorldPassViewport(int x, int y, int width, int height, int[] out) {
                final StereoState state = StereoState.INSTANCE;
                if (!state.isInWorldPass()) return false;
                final Minecraft mc = Minecraft.getMinecraft();
                if (mc == null) return false;
                if (x != 0 || y != 0 || width != mc.displayWidth || height != mc.displayHeight) return false;
                out[0] = 0;
                out[1] = 0;
                out[2] = state.irisFbWidth(mc.displayWidth);
                out[3] = state.irisFbHeight(mc.displayHeight);
                return true;
            }

            @Override
            public boolean remapGuiPassViewport(int x, int y, int width, int height, int[] out) {
                final StereoState state = StereoState.INSTANCE;
                if (!state.isInGuiPass()) return false;
                final Minecraft mc = Minecraft.getMinecraft();
                if (mc == null) return false;
                if (x != 0 || y != 0 || width != mc.displayWidth || height != mc.displayHeight) return false;
                out[0] = state.getEyeVpX();
                out[1] = state.getEyeVpY();
                out[2] = state.getEyeVpW();
                out[3] = state.getEyeVpH();
                return true;
            }

            @Override public int stereoMouseGetX()      { return StereoState.INSTANCE.isActive() ? StereoCursor.getX()      : Mouse.getX(); }
            @Override public int stereoMouseGetY()      { return StereoState.INSTANCE.isActive() ? StereoCursor.getY()      : Mouse.getY(); }
            @Override public int stereoMouseGetEventX() { return StereoState.INSTANCE.isActive() ? StereoCursor.getEventX() : Mouse.getEventX(); }
            @Override public int stereoMouseGetEventY() { return StereoState.INSTANCE.isActive() ? StereoCursor.getEventY() : Mouse.getEventY(); }
        };
    }
}
