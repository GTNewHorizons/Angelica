package com.gtnewhorizons.angelica.glsm;

import org.lwjgl.BufferUtils;
import org.lwjgl.opengl.GL11;
import org.lwjgl.opengl.GL13;
import org.lwjgl.opengl.GL20;

import java.nio.FloatBuffer;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.fail;

public final class GlTestSupport {

    public static final String TRIVIAL_VS = "#version 330 core\nvoid main() { gl_Position = vec4(0.0); }\n";
    public static final String TRIVIAL_FS = "#version 330 core\nout vec4 color;\nvoid main() { color = vec4(1.0); }\n";

    private GlTestSupport() {}

    public static void drainErrors() {
        while (GL11.glGetError() != GL11.GL_NO_ERROR) {}
    }

    public static int compile(int type, String source) {
        final int shader = GLStateManager.glCreateShader(type);
        GLStateManager.glShaderSource(shader, source);
        GLStateManager.glCompileShader(shader);
        if (GLStateManager.glGetShaderi(shader, GL20.GL_COMPILE_STATUS) == 0) {
            fail("test shader failed to compile:\n" + GLStateManager.glGetShaderInfoLog(shader, 4096) + "\n\n" + source);
        }
        return shader;
    }

    /** Links and deletes the two shaders. */
    public static int link(int vs, int fs) {
        final int p = GLStateManager.glCreateProgram();
        GLStateManager.glAttachShader(p, vs);
        GLStateManager.glAttachShader(p, fs);
        GLStateManager.glLinkProgram(p);
        assertNotEquals(0, GLStateManager.glGetProgrami(p, GL20.GL_LINK_STATUS), () -> "test program must link: " + GLStateManager.glGetProgramInfoLog(p, 4096));
        GLStateManager.glDeleteShader(vs);
        GLStateManager.glDeleteShader(fs);
        return p;
    }

    public static int program(String vertexSource, String fragmentSource) {
        return link(compile(GL20.GL_VERTEX_SHADER, vertexSource), compile(GL20.GL_FRAGMENT_SHADER, fragmentSource));
    }

    /** Raw driver GL_TEXTURE_BINDING_2D of a unit, bypassing the GLSM cache. */
    public static int driverTextureBinding(int unit) {
        final int saved = GL11.glGetInteger(GL13.GL_ACTIVE_TEXTURE);
        GL13.glActiveTexture(GL13.GL_TEXTURE0 + unit);
        final int binding = GL11.glGetInteger(GL11.GL_TEXTURE_BINDING_2D);
        GL13.glActiveTexture(saved);
        return binding;
    }

    public static FloatBuffer floats(float... values) {
        final FloatBuffer buf = BufferUtils.createFloatBuffer(values.length);
        buf.put(values).flip();
        return buf;
    }

    /** GLSM's answer for a 16-float matrix pname. */
    public static FloatBuffer matrix(int pname) {
        final FloatBuffer buf = BufferUtils.createFloatBuffer(16);
        GLStateManager.glGetFloat(pname, buf);
        return buf;
    }

    public static FloatBuffer modelview() {
        return matrix(GL11.GL_MODELVIEW_MATRIX);
    }

    public static void assertMatrixEquals(FloatBuffer expected, FloatBuffer actual, String message) {
        for (int i = 0; i < 16; i++) {
            assertEquals(expected.get(i), actual.get(i), 0.001f, message + " (element " + i + ")");
        }
    }
}
