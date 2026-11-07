package com.gtnewhorizons.angelica.glsm.testutil;

import com.gtnewhorizons.angelica.glsm.GLSMRasterizer;
import com.gtnewhorizons.angelica.glsm.RenderSystem;
import org.lwjgl.BufferUtils;
import org.lwjgl.opengl.GL11;

import java.nio.DoubleBuffer;

public final class ClipTestSupport {

    private ClipTestSupport() {}

    public static void setPlane(int index, double a, double b, double c, double d) {
        final DoubleBuffer eq = BufferUtils.createDoubleBuffer(4);
        eq.put(a).put(b).put(c).put(d).flip();
        GLSMRasterizer.glClipPlane(GL11.GL_CLIP_PLANE0 + index, eq);
    }

    public static Emulation emulation(boolean emulated) {
        return new Emulation(emulated);
    }

    public static final class Emulation implements AutoCloseable {
        private final boolean previous;

        private Emulation(boolean emulated) {
            previous = Reflect.getStatic(RenderSystem.class, "clipDistanceEmulated");
            Reflect.setStatic(RenderSystem.class, "clipDistanceEmulated", emulated);
        }

        @Override
        public void close() {
            Reflect.setStatic(RenderSystem.class, "clipDistanceEmulated", previous);
        }
    }
}
