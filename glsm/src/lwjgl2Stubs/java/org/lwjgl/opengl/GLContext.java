package org.lwjgl.opengl;

public final class GLContext {

    private static ContextCapabilities capabilities;

    private GLContext() {}

    public static ContextCapabilities getCapabilities() {
        if (capabilities == null) capabilities = new ContextCapabilities();
        return capabilities;
    }
}
