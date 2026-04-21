package com.gtnewhorizons.angelica.glsm;

import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.lwjgl.LWJGLException;
import org.lwjgl.opengl.ContextAttribs;
import org.lwjgl.opengl.Display;
import org.lwjgl.opengl.DisplayMode;
import org.lwjgl.opengl.GL11;
import org.lwjgl.opengl.GL20;
import org.lwjgl.opengl.GL30;
import org.lwjgl.opengl.PixelFormat;
import org.lwjgl.opengl.SharedDrawable;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

class SharedDrawableVaoTest {

    private static final String VERT_330 = "#version 330 core\nvoid main() { gl_Position = vec4(0.0); }\n";
    private static final String FRAG_330 = "#version 330 core\nout vec4 o;\nvoid main() { o = vec4(0.0); }\n";

    @BeforeAll
    static void createContext() throws LWJGLException {
        assumeTrue(!Display.isCreated(), "inherited Display -- skipping to avoid profile drift");
        Display.setDisplayModeAndFullscreen(new DisplayMode(800, 600));
        Display.setResizable(false);
        Display.setFullscreen(false);
        Display.create(
            new PixelFormat().withDepthBits(24).withStencilBits(8),
            new ContextAttribs(4, 1).withProfileCore(true).withForwardCompatible(true));
        GL30.glBindVertexArray(GL30.glGenVertexArrays());
    }

    @AfterAll
    static void destroyContext() {
        if (Display.isCreated()) Display.destroy();
    }

    @Test
    void validateProgramSucceedsOnSharedDrawableWithVao() throws LWJGLException {
        SharedDrawable shared = new SharedDrawable(Display.getDrawable());
        Display.getDrawable().releaseContext();
        shared.makeCurrent();
        try {
            GL30.glBindVertexArray(GL30.glGenVertexArrays());
            int program = linkTrivialProgram();
            GL20.glValidateProgram(program);
            assertEquals(GL11.GL_TRUE, GL20.glGetProgrami(program, GL20.GL_VALIDATE_STATUS), () -> GL20.glGetProgramInfoLog(program, 4096));
            GL20.glDeleteProgram(program);
        } finally {
            shared.releaseContext();
            Display.getDrawable().makeCurrent();
            shared.destroy();
        }
    }

    private static int linkTrivialProgram() {
        int vs = compile(GL20.GL_VERTEX_SHADER, VERT_330);
        int fs = compile(GL20.GL_FRAGMENT_SHADER, FRAG_330);
        int program = GL20.glCreateProgram();
        GL20.glAttachShader(program, vs);
        GL20.glAttachShader(program, fs);
        GL20.glLinkProgram(program);
        assertEquals(GL11.GL_TRUE, GL20.glGetProgrami(program, GL20.GL_LINK_STATUS), () -> GL20.glGetProgramInfoLog(program, 4096));
        GL20.glDeleteShader(vs);
        GL20.glDeleteShader(fs);
        return program;
    }

    private static int compile(int type, String src) {
        int shader = GL20.glCreateShader(type);
        GL20.glShaderSource(shader, src);
        GL20.glCompileShader(shader);
        assertEquals(GL11.GL_TRUE, GL20.glGetShaderi(shader, GL20.GL_COMPILE_STATUS), () -> GL20.glGetShaderInfoLog(shader, 4096));
        return shader;
    }
}
