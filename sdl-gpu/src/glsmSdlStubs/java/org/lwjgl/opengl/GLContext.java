package org.lwjgl.opengl;

public final class GLContext {

    private static final ContextCapabilities CAPABILITIES = new ContextCapabilities();

    private GLContext() {}

    public static ContextCapabilities getCapabilities() {
        return CAPABILITIES;
    }
}
