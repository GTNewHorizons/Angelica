package com.gtnewhorizons.angelica.glsm.managers;

import com.gtnewhorizons.angelica.AngelicaMod;
import com.gtnewhorizons.angelica.glsm.GLDebug;
import com.gtnewhorizons.angelica.glsm.GLStateManager;
import lombok.Getter;
import org.lwjgl.opengl.GL33C;
import org.lwjgl.opengl.KHRDebug;

@SuppressWarnings("unused") // Entrypoint via ASM
public class GLShaderManager {

    @Getter protected static int activeProgram = 0;

    public static int glCreateShader(int type) {
        return GL33C.glCreateShader(type);
    }

    public static void glCompileShader(int shader) {
        GL33C.glCompileShader(shader);
    }

    public static void glShaderSource(int shader, CharSequence string) {
        GL33C.glShaderSource(shader, string);
    }

    public static void glShaderSource(int shader, CharSequence[] strings) {
        GL33C.glShaderSource(shader, strings);
    }

    public static int glCreateProgram() {
        return GL33C.glCreateProgram();
    }

    public static void glAttachShader(int program, int shader) {
        GL33C.glAttachShader(program, shader);
    }

    public static void glDetachShader(int program, int shader) {
        GL33C.glDetachShader(program, shader);
    }

    public static void glLinkProgram(int program) {
        GL33C.glLinkProgram(program);
    }

    public static void glValidateProgram(int program) {
        GL33C.glValidateProgram(program);
    }

    public static void glDeleteShader(int shader) {
        GL33C.glDeleteShader(shader);
    }

    public static void glDeleteProgram(int program) {
        if (program == activeProgram) {
            activeProgram = 0;
        }
        GL33C.glDeleteProgram(program);
    }

    public static int glGetProgrami(int program, int pname) {
        return GL33C.glGetProgrami(program, pname);
    }

    public static int glGetShaderi(int shader, int pname) {
        return GL33C.glGetShaderi(shader, pname);
    }

    public static String glGetProgramInfoLog(int program) {
        return GL33C.glGetProgramInfoLog(program, 1024);
    }

    public static String glGetShaderInfoLog(int shader) {
        return GL33C.glGetShaderInfoLog(shader, 1024);
    }

    public static int glGetUniformLocation(int program, CharSequence name) {
        return GL33C.glGetUniformLocation(program, name);
    }

    public static int glGetAttribLocation(int program, CharSequence name) {
        return GL33C.glGetAttribLocation(program, name);
    }

    public static void glBindAttribLocation(int program, int index, CharSequence name) {
        GL33C.glBindAttribLocation(program, index, name);
    }

    public static void glUniform1i(int location, int value) {
        GL33C.glUniform1i(location, value);
    }

    public static void glUniform1f(int location, float value) {
        GL33C.glUniform1f(location, value);
    }

    public static void glUniform2i(int location, int v0, int v1) {
        GL33C.glUniform2i(location, v0, v1);
    }

    public static void glUniform2f(int location, float v0, float v1) {
        GL33C.glUniform2f(location, v0, v1);
    }

    public static void glUniform3i(int location, int v0, int v1, int v2) {
        GL33C.glUniform3i(location, v0, v1, v2);
    }

    public static void glUniform3f(int location, float v0, float v1, float v2) {
        GL33C.glUniform3f(location, v0, v1, v2);
    }

    public static void glUniform4i(int location, int v0, int v1, int v2, int v3) {
        GL33C.glUniform4i(location, v0, v1, v2, v3);
    }

    public static void glUniform4f(int location, float v0, float v1, float v2, float v3) {
        GL33C.glUniform4f(location, v0, v1, v2, v3);
    }

    public static void glUseProgram(int program) {
        if (program != activeProgram || GLStateManager.shouldBypassCache()) {
            activeProgram = program;
            if(AngelicaMod.lwjglDebug) {
                final String programName = GLDebug.getObjectLabel(KHRDebug.GL_PROGRAM, program);
                GLDebug.debugMessage(0, "Activating Program - " + program + ":" + programName);
            }
            GL33C.glUseProgram(program);
        }
    }
}
