package com.gtnewhorizons.angelica.glsm;

import org.lwjgl.opengl.OpenGLException;

final class GLErrorReporter {

    private GLErrorReporter() {}

    static void reportError(int error) {
        throw new OpenGLException(error);
    }
}
