package com.gtnewhorizons.angelica.glsm;

import com.gtnewhorizons.angelica.glsm.dsa.DSAARB;
import com.gtnewhorizons.angelica.glsm.dsa.DSAAccess;
import com.gtnewhorizons.angelica.glsm.dsa.DSACore;
import com.gtnewhorizons.angelica.glsm.dsa.DSAUnsupported;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.jetbrains.annotations.Nullable;
import org.joml.Matrix4f;
import org.joml.Vector3i;
import org.lwjgl.BufferUtils;
import org.lwjgl.opengl.EXTShaderImageLoadStore;
import org.lwjgl.opengl.GL11;
import org.lwjgl.opengl.GL15;
import org.lwjgl.opengl.GL20;
import org.lwjgl.opengl.GL30;
import org.lwjgl.opengl.GL40;
import org.lwjgl.opengl.GL42;
import org.lwjgl.opengl.GL43;

import java.nio.ByteBuffer;
import java.nio.FloatBuffer;
import java.nio.IntBuffer;


/**
 * This class is responsible for abstracting calls to OpenGL and asserting that calls are run on the render thread.
 */
public class RenderSystem {
    private static final Logger LOGGER = LogManager.getLogger("RenderSystem");
	private static DSAAccess dsaState;
	private static boolean hasMultibind;
	private static boolean supportsCompute;

    private RenderSystem() {}

	public static void initRenderer() {
        try {
            if (GLStateManager.capabilities.OpenGL45) {
                dsaState = new DSACore();
                LOGGER.info("OpenGL 4.5 detected, enabling DSA.");
            }
            hasMultibind = GLStateManager.capabilities.OpenGL45;

        } catch (NoSuchFieldError ignored) {}
        try {
            if (dsaState == null && GLStateManager.capabilities.GL_ARB_direct_state_access) {
                dsaState = new DSAARB();
                LOGGER.info("ARB_direct_state_access detected, enabling DSA.");
            }
        } catch (NoSuchFieldError ignored) {}
        if (dsaState == null) {
            dsaState = new DSAUnsupported();
            LOGGER.info("No DSA support detected, falling back to legacy OpenGL.");
        }

        try {
            hasMultibind |= GLStateManager.capabilities.GL_ARB_multi_bind;
        } catch (NoSuchFieldError ignored) {}

		supportsCompute = supportsCompute();
	}

	public static void generateMipmaps(int texture, int mipmapTarget) {
		dsaState.generateMipmaps(texture, mipmapTarget);
	}

	public static void bindAttributeLocation(int program, int index, CharSequence name) {
        GL20.glBindAttribLocation(program, index, name);
	}

	public static void texImage2D(int texture, int target, int level, int internalformat, int width, int height, int border, int format, int type, @Nullable ByteBuffer pixels) {
		GLStateManager.glBindTexture(GL11.GL_TEXTURE_2D, texture);
        GLStateManager.glTexImage2D(target, level, internalformat, width, height, border, format, type, pixels);
	}

	public static void uniformMatrix4fv(int location, boolean transpose, FloatBuffer matrix) {
        GL20.glUniformMatrix4(location, transpose, matrix);
	}

	public static void copyTexImage2D(int target, int level, int internalFormat, int x, int y, int width, int height, int border) {
		GL11.glCopyTexImage2D(target, level, internalFormat, x, y, width, height, border);
	}

	public static void uniform1f(int location, float v0) {
		GL20.glUniform1f(location, v0);
	}

	public static void uniform1i(int location, int v0) {
		GL20.glUniform1i(location, v0);
	}

	public static void uniform2f(int location, float v0, float v1) {
        GL20.glUniform2f(location, v0, v1);
	}

	public static void uniform2i(int location, int v0, int v1) {
        GL20.glUniform2i(location, v0, v1);
	}

	public static void uniform3f(int location, float v0, float v1, float v2) {
        GL20.glUniform3f(location, v0, v1, v2);
	}

	public static void uniform4f(int location, float v0, float v1, float v2, float v3) {
        GL20.glUniform4f(location, v0, v1, v2, v3);
	}

	public static void uniform4i(int location, int v0, int v1, int v2, int v3) {
        GL20.glUniform4i(location, v0, v1, v2, v3);
	}

	public static int getAttribLocation(int programId, String name) {
		return GL20.glGetAttribLocation(programId, name);
	}

	public static int getUniformLocation(int programId, String name) {
		return GL20.glGetUniformLocation(programId, name);
	}

	public static void texParameteriv(int texture, int target, int pname, IntBuffer params) {
		dsaState.texParameteriv(texture, target, pname, params);
	}

	public static void copyTexSubImage2D(int destTexture, int target, int i, int i1, int i2, int i3, int i4, int width, int height) {
		dsaState.copyTexSubImage2D(destTexture, target, i, i1, i2, i3, i4, width, height);
	}

	public static void texParameteri(int texture, int target, int pname, int param) {
		dsaState.texParameteri(texture, target, pname, param);
	}

	public static void texParameterf(int texture, int target, int pname, float param) {
		dsaState.texParameterf(texture, target, pname, param);
	}

    public static String getProgramInfoLog(int program) {
        return GL20.glGetProgramInfoLog(program, GL20.glGetProgrami(program, GL20.GL_INFO_LOG_LENGTH));
    }

    public static String getShaderInfoLog(int shader) {
        return GL20.glGetShaderInfoLog(shader, GL20.glGetShaderi(shader, GL20.GL_INFO_LOG_LENGTH));
    }

	public static void drawBuffers(int framebuffer, IntBuffer buffers) {
		dsaState.drawBuffers(framebuffer, buffers);
	}

	public static void readBuffer(int framebuffer, int buffer) {
		dsaState.readBuffer(framebuffer, buffer);
	}

	public static String getActiveUniform(int program, int index, int maxLength, IntBuffer sizeType) {
        return GL20.glGetActiveUniform(program, index, maxLength, sizeType);
	}

	public static void readPixels(int x, int y, int width, int height, int format, int type, FloatBuffer pixels) {
		GL11.glReadPixels(x, y, width, height, format, type, pixels);
	}

	public static void bufferData(int target, FloatBuffer data, int usage) {
		GL15.glBufferData(target, data, usage);
	}

	public static int bufferStorage(int target, FloatBuffer data, int usage) {
		return dsaState.bufferStorage(target, data, usage);
	}

	public static void vertexAttrib4f(int index, float v0, float v1, float v2, float v3) {
		GL20.glVertexAttrib4f(index, v0, v1, v2, v3);
	}

	public static void detachShader(int program, int shader) {
		GL20.glDetachShader(program, shader);
	}

	public static void framebufferTexture2D(int fb, int fbtarget, int attachment, int target, int texture, int levels) {
		dsaState.framebufferTexture2D(fb, fbtarget, attachment, target, texture, levels);
	}

	public static int getTexParameteri(int texture, int target, int pname) {
		return dsaState.getTexParameteri(texture, target, pname);
	}

	public static void bindImageTexture(int unit, int texture, int level, boolean layered, int layer, int access, int format) {
		if (GLStateManager.capabilities.OpenGL42) {
			GL42.glBindImageTexture(unit, texture, level, layered, layer, access, format);
		} else {
			EXTShaderImageLoadStore.glBindImageTextureEXT(unit, texture, level, layered, layer, access, format);
		}
	}

	public static int getMaxImageUnits() {
		if (GLStateManager.capabilities.OpenGL42) {
			return GL11.glGetInteger(GL42.GL_MAX_IMAGE_UNITS);
		} else if (GLStateManager.capabilities.GL_EXT_shader_image_load_store) {
			return GL11.glGetInteger(EXTShaderImageLoadStore.GL_MAX_IMAGE_UNITS_EXT);
		} else {
			return 0;
		}
	}

	public static void getProgramiv(int program, int value, IntBuffer storage) {
        GL20.glGetProgram(program, value, storage);
	}

	public static void dispatchCompute(int workX, int workY, int workZ) {
		GL43.glDispatchCompute(workX, workY, workZ);
	}

	public static void dispatchCompute(Vector3i workGroups) {
		GL43.glDispatchCompute(workGroups.x, workGroups.y, workGroups.z);
	}

	public static void memoryBarrier(int barriers) {
		if (supportsCompute) {
            GL42.glMemoryBarrier(barriers);
		}
	}

	public static boolean supportsBufferBlending() {
		return GLStateManager.capabilities.GL_ARB_draw_buffers_blend || GLStateManager.capabilities.OpenGL40;
	}

	public static void disableBufferBlend(int buffer) {
		GL30.glDisablei(GL11.GL_BLEND, buffer);
	}

	public static void enableBufferBlend(int buffer) {
		GL30.glEnablei(GL11.GL_BLEND, buffer);
	}

	public static void blendFuncSeparatei(int buffer, int srcRGB, int dstRGB, int srcAlpha, int dstAlpha) {
		GL40.glBlendFuncSeparatei(buffer, srcRGB, dstRGB, srcAlpha, dstAlpha);
  }

	public static void bindTextureToUnit(int unit, int texture) {
		dsaState.bindTextureToUnit(unit, texture);
	}

    public static final FloatBuffer PROJECTION_MATRIX_BUFFER = BufferUtils.createFloatBuffer(16);
    public static void setupProjectionMatrix(Matrix4f matrix) {
        GL11.glMatrixMode(GL11.GL_PROJECTION);
        GL11.glPushMatrix();
        matrix.get(0, PROJECTION_MATRIX_BUFFER);
        GL11.glLoadMatrix(PROJECTION_MATRIX_BUFFER);
        GL11.glMatrixMode(GL11.GL_MODELVIEW);
    }

    public static void restoreProjectionMatrix() {
        GL11.glMatrixMode(GL11.GL_PROJECTION);
        GL11.glPopMatrix();
        GL11.glMatrixMode(GL11.GL_MODELVIEW);
    }

	public static void blitFramebuffer(int source, int dest, int offsetX, int offsetY, int width, int height, int offsetX2, int offsetY2, int width2, int height2, int bufferChoice, int filter) {
		dsaState.blitFramebuffer(source, dest, offsetX, offsetY, width, height, offsetX2, offsetY2, width2, height2, bufferChoice, filter);
	}

	public static int createFramebuffer() {
		return dsaState.createFramebuffer();
	}

	public static int createTexture(int target) {
		return dsaState.createTexture(target);
	}

	// TODO: Proper notification of compute support
	public static boolean supportsCompute() {
        try {
            return GLStateManager.capabilities.GL_ARB_compute_shader;
        } catch(Exception ignored) {
            return false;
        }
	}
}
