package com.gtnewhorizons.angelica.sdlgpu.frame;

public final class StateSyncVerifier {

    private StateSyncVerifier() {}

    public static void verify(int glsmVao, int backendVao, int glsmDrawFbo, int backendDrawFbo, int glsmReadFbo, int backendReadFbo, int glsmTextureUnit, int backendTextureUnit) {
        check("bound VAO", glsmVao, backendVao);
        check("draw framebuffer", glsmDrawFbo, backendDrawFbo);
        check("read framebuffer", glsmReadFbo, backendReadFbo);
        check("active texture unit", glsmTextureUnit, backendTextureUnit);
    }

    private static void check(String what, int glsm, int backend) {
        if (glsm == backend) return;
        throw new IllegalStateException("GLSM/ContextState drift on " + what + ": GLSM=" + glsm + " backend=" + backend + ". GLSM elides redundant binds against its cache, so a bypass of GLStateManager leaves the backend on stale state and the next elided rebind silently drops the call.");
    }
}
