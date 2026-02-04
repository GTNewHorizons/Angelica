package com.gtnewhorizons.angelica.glsm;

import com.gtnewhorizons.angelica.glsm.dsa.DSAARB;
import com.gtnewhorizons.angelica.glsm.dsa.DSAAccess;
import com.gtnewhorizons.angelica.glsm.dsa.DSACore;
import com.gtnewhorizons.angelica.glsm.dsa.DSAUnsupported;
import com.gtnewhorizons.angelica.glsm.texture.TextureInfoCache;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.jetbrains.annotations.Nullable;
import org.joml.Matrix4f;
import org.joml.Vector3i;
import org.lwjgl.BufferUtils;
import org.lwjgl.opengl.ARBBufferStorage;
import org.lwjgl.opengl.ARBClearBufferObject;
import org.lwjgl.opengl.ARBClearTexture;
import org.lwjgl.opengl.ARBShaderImageLoadStore;
import org.lwjgl.opengl.ARBShaderStorageBufferObject;
import org.lwjgl.opengl.EXTShaderImageLoadStore;
import org.lwjgl.opengl.GL11;
import org.lwjgl.opengl.GL12;
import org.lwjgl.opengl.GL15;
import org.lwjgl.opengl.GL20;
import org.lwjgl.opengl.GL30;
import org.lwjgl.opengl.GL33;
import org.lwjgl.opengl.GL40;
import org.lwjgl.opengl.GL42;
import org.lwjgl.opengl.GL43;
import org.lwjgl.opengl.GL44;
import org.lwjgl.opengl.NVXGpuMemoryInfo;

import net.coderbot.iris.gl.shader.StandardMacros;

import java.nio.ByteBuffer;
import java.nio.FloatBuffer;
import java.nio.IntBuffer;


/**
 * This class is responsible for abstracting calls to OpenGL and asserting that calls are run on the render thread.
 */
public class RenderSystem {
    private static final Logger LOGGER = LogManager.getLogger("RenderSystem");
	private static DSAAccess dsaState;
	private static boolean supportsCompute;
	private static boolean supportsImageLoadStore;
	private static boolean supportsSSBO;
	private static boolean supportsBufferStorage;
	private static boolean supportsClearTexture;
	private static boolean supportsTesselation;
	private static boolean supportsSamplerObjects;
	private static int maxImageUnits;
	private static int maxSSBOBindings;
	private static int maxGlslVersion;
	private static boolean supportsGpuShader4;

	// Sampler object state tracking (null if unsupported)
	private static int[] samplers;

    private RenderSystem() {}

	public static void initRenderer() {
        try {
            if (GLStateManager.capabilities.OpenGL45) {
                dsaState = new DSACore();
                LOGGER.info("OpenGL 4.5 detected, enabling DSA.");
            }

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

		supportsCompute = supportsCompute();
		supportsTesselation = GLStateManager.capabilities.GL_ARB_tessellation_shader || GLStateManager.capabilities.OpenGL40;

		supportsImageLoadStore = GLStateManager.capabilities.OpenGL42 || GLStateManager.capabilities.GL_ARB_shader_image_load_store || GLStateManager.capabilities.GL_EXT_shader_image_load_store;
		supportsSSBO = GLStateManager.capabilities.OpenGL43 || GLStateManager.capabilities.GL_ARB_shader_storage_buffer_object;
		supportsBufferStorage = GLStateManager.capabilities.OpenGL44 || GLStateManager.capabilities.GL_ARB_buffer_storage;
		supportsClearTexture = GLStateManager.capabilities.OpenGL44 || GLStateManager.capabilities.GL_ARB_clear_texture;

		// Cache maximum image units
		if (supportsImageLoadStore) {
			if (GLStateManager.capabilities.OpenGL42) {
				maxImageUnits = GL11.glGetInteger(GL42.GL_MAX_IMAGE_UNITS);
			} else if (GLStateManager.capabilities.GL_ARB_shader_image_load_store) {
				maxImageUnits = GL11.glGetInteger(ARBShaderImageLoadStore.GL_MAX_IMAGE_UNITS);
			} else {
				maxImageUnits = GL11.glGetInteger(EXTShaderImageLoadStore.GL_MAX_IMAGE_UNITS_EXT);
			}
		} else {
			maxImageUnits = 0;
		}

		// Cache maximum SSBO bindings
		if (supportsSSBO) {
			if (GLStateManager.capabilities.OpenGL43) {
				maxSSBOBindings = GL11.glGetInteger(GL43.GL_MAX_SHADER_STORAGE_BUFFER_BINDINGS);
			} else {
				maxSSBOBindings = GL11.glGetInteger(ARBShaderStorageBufferObject.GL_MAX_SHADER_STORAGE_BUFFER_BINDINGS);
			}
		} else {
			maxSSBOBindings = 0;
		}

		// Check for sampler objects support (GL 3.3+ or ARB extension)
		supportsSamplerObjects = GLStateManager.capabilities.OpenGL33
			|| GLStateManager.capabilities.GL_ARB_sampler_objects;
		if (supportsSamplerObjects) {
			samplers = new int[GL11.glGetInteger(GL20.GL_MAX_COMBINED_TEXTURE_IMAGE_UNITS)];
		}

		maxGlslVersion = Integer.parseInt(StandardMacros.getGlVersion(GL20.GL_SHADING_LANGUAGE_VERSION));
		supportsGpuShader4 = GLStateManager.capabilities.GL_EXT_gpu_shader4;

		LOGGER.info("Max GLSL version: {}, GPU Shader4: {}", maxGlslVersion, supportsGpuShader4);
		LOGGER.info("Image Load/Store: {}, Max Image Units: {}", supportsImageLoadStore, maxImageUnits);
		LOGGER.info("SSBO: {}, Max SSBO Bindings: {}", supportsSSBO, maxSSBOBindings);
		LOGGER.info("Buffer Storage: {}, Clear Texture: {}, Sampler Objects: {}", supportsBufferStorage, supportsClearTexture, supportsSamplerObjects);
	}

	public static void generateMipmaps(int texture, int mipmapTarget) {
		dsaState.generateMipmaps(texture, mipmapTarget);
	}

	public static void bindAttributeLocation(int program, int index, CharSequence name) {
        GL20.glBindAttribLocation(program, index, name);
	}

	public static void texImage2D(int texture, int target, int level, int internalformat, int width, int height, int border, int format, int type, @Nullable ByteBuffer pixels) {
		GLStateManager.glBindTexture(target, texture);
		GL11.glTexImage2D(target, level, internalformat, width, height, border, format, type, pixels);
		if (target == GL11.GL_TEXTURE_2D && level == 0) {
			TextureInfoCache.INSTANCE.onTexImage2D(target, level, internalformat, width, height, border, format, type, pixels);
		}
	}

	public static void uniformMatrix4fv(int location, boolean transpose, FloatBuffer matrix) {
        GL20.glUniformMatrix4(location, transpose, matrix);
	}

	public static void copyTexImage2D(int target, int level, int internalFormat, int x, int y, int width, int height, int border) {
		GLStateManager.glCopyTexImage2D(target, level, internalFormat, x, y, width, height, border);
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

    public static void uniform3i(int location, int v0, int v1, int v2) { GL20.glUniform3i(location, v0, v1, v2); }

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

	public static void textureImage2D(int texture, int target, int level, int internalformat, int width, int height, int border, int format, int type, ByteBuffer pixels) {
		dsaState.textureImage2D(texture, target, level, internalformat, width, height, border, format, type, pixels);
	}

	public static void textureImage2D(int texture, int target, int level, int internalformat, int width, int height, int border, int format, int type, IntBuffer pixels) {
		dsaState.textureImage2D(texture, target, level, internalformat, width, height, border, format, type, pixels);
	}

	public static void textureSubImage2D(int texture, int target, int level, int xoffset, int yoffset, int width, int height, int format, int type, ByteBuffer pixels) {
		dsaState.textureSubImage2D(texture, target, level, xoffset, yoffset, width, height, format, type, pixels);
	}

	public static void textureSubImage2D(int texture, int target, int level, int xoffset, int yoffset, int width, int height, int format, int type, IntBuffer pixels) {
		dsaState.textureSubImage2D(texture, target, level, xoffset, yoffset, width, height, format, type, pixels);
	}


	public static void texImage1D(int texture, int target, int level, int internalformat, int width, int border, int format, int type, ByteBuffer pixels) {
		GL11.glBindTexture(target, texture);
		GL11.glTexImage1D(target, level, internalformat, width, border, format, type, pixels);
	}

	public static void texImage3D(int texture, int target, int level, int internalformat, int width, int height, int depth, int border, int format, int type, ByteBuffer pixels) {
		GL11.glBindTexture(target, texture);
		GL12.glTexImage3D(target, level, internalformat, width, height, depth, border, format, type, pixels);
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

	public static int getTexLevelParameteri(int texture, int level, int pname) {
		return dsaState.getTexLevelParameteri(texture, level, pname);
	}

	public static void bindImageTexture(int unit, int texture, int level, boolean layered, int layer, int access, int format) {
		if (GLStateManager.capabilities.OpenGL42) {
			GL42.glBindImageTexture(unit, texture, level, layered, layer, access, format);
		} else {
			EXTShaderImageLoadStore.glBindImageTextureEXT(unit, texture, level, layered, layer, access, format);
		}
	}

	public static int getMaxImageUnits() {
		return maxImageUnits;
	}

	public static boolean supportsImageLoadStore() {
		return supportsImageLoadStore;
	}

	public static boolean supportsSSBO() {
		return supportsSSBO;
	}

	public static boolean supportsBufferStorage() {
		return supportsBufferStorage;
	}

	public static boolean supportsClearTexture() {
		return supportsClearTexture;
	}

	public static int getMaxSSBOBindings() {
		return maxSSBOBindings;
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

	public static void dispatchComputeIndirect(long offset) {
		GL43.glDispatchComputeIndirect(offset);
	}

	public static void bindBuffer(int target, int buffer) {
		GLStateManager.glBindBuffer(target, buffer);
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

	public static void bindTextureToUnit(int target, int unit, int texture) {
		dsaState.bindTextureToUnit(target, unit, texture);
	}

    public static final FloatBuffer PROJECTION_MATRIX_BUFFER = BufferUtils.createFloatBuffer(16);
    public static void setupProjectionMatrix(Matrix4f matrix) {
        GLStateManager.glMatrixMode(GL11.GL_PROJECTION);
        GLStateManager.glPushMatrix();
        matrix.get(0, PROJECTION_MATRIX_BUFFER);
        GLStateManager.glLoadMatrix(PROJECTION_MATRIX_BUFFER);
        GLStateManager.glMatrixMode(GL11.GL_MODELVIEW);
    }

    public static void restoreProjectionMatrix() {
        GLStateManager.glMatrixMode(GL11.GL_PROJECTION);
        GLStateManager.glPopMatrix();
        GLStateManager.glMatrixMode(GL11.GL_MODELVIEW);
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

	public static int createBuffers() {
		return GL15.glGenBuffers();
	}

	public static void bindBufferBase(int target, int index, int buffer) {
		GL30.glBindBufferBase(target, index, buffer);
	}

	public static void bufferStorage(int target, long size, int flags) {
		if (GLStateManager.capabilities.OpenGL44) {
			GL44.glBufferStorage(target, size, flags);
		} else if (GLStateManager.capabilities.GL_ARB_buffer_storage) {
			ARBBufferStorage.glBufferStorage(target, size, flags);
		} else {
			// Fallback: use mutable storage
			GL15.glBufferData(target, size, GL15.GL_DYNAMIC_DRAW);
		}
	}

	public static void clearBufferSubData(int target, int internalFormat, long offset, long size, int format, int type, int[] data) {
		final ByteBuffer buf = BufferUtils.createByteBuffer(data.length * 4);
		buf.asIntBuffer().put(data);
		if (GLStateManager.capabilities.OpenGL43) {
			GL43.glClearBufferSubData(target, internalFormat, offset, size, format, type, buf);
		} else if (GLStateManager.capabilities.GL_ARB_clear_buffer_object) {
			ARBClearBufferObject.glClearBufferSubData(target, internalFormat, offset, size, format, type, buf);
		}
	}

	public static void deleteBuffers(int buffer) {
		GL15.glDeleteBuffers(buffer);
	}

	public static long getVRAM() {
		if (GLStateManager.capabilities.GL_NVX_gpu_memory_info) {
			return GL11.glGetInteger(NVXGpuMemoryInfo.GL_GPU_MEMORY_INFO_CURRENT_AVAILABLE_VIDMEM_NVX) * 1024L;
		} else {
			// Default to 4GB if we can't query VRAM
			return 4294967296L;
		}
	}

	public static void clearTexImage(int texture, int target, int level, int format, int type) {
		if (GLStateManager.capabilities.OpenGL44) {
			GL44.glClearTexImage(texture, level, format, type, (ByteBuffer) null);
		} else {
			ARBClearTexture.glClearTexImage(texture, level, format, type, (ByteBuffer) null);
		}
	}

	public static void textureStorage1D(int texture, int target, int levels, int internalFormat, int width) {
		dsaState.textureStorage1D(texture, target, levels, internalFormat, width);
	}

	public static void textureStorage2D(int texture, int target, int levels, int internalFormat, int width, int height) {
		dsaState.textureStorage2D(texture, target, levels, internalFormat, width, height);
	}

	public static void textureStorage3D(int texture, int target, int levels, int internalFormat, int width, int height, int depth) {
		dsaState.textureStorage3D(texture, target, levels, internalFormat, width, height, depth);
	}

	public static int getMaxGlslVersion() {
		return maxGlslVersion;
	}

	public static boolean supportsGpuShader4() {
		return supportsGpuShader4;
	}

	public static boolean supportsSnormFormats() {
		return GLStateManager.capabilities.OpenGL31;
	}

	public static boolean supportsPackedFloatRenderable() {
		return GLStateManager.capabilities.OpenGL30 && maxGlslVersion >= 130;
	}

	public static boolean supportsTesselation() {
		return supportsTesselation;
	}

	public static boolean supportsSamplerObjects() {
		return supportsSamplerObjects;
	}

	public static int genSampler() {
		if (!supportsSamplerObjects) return 0;
		return GL33.glGenSamplers();
	}

	public static void destroySampler(int sampler) {
		if (!supportsSamplerObjects || sampler == 0) return;
		GL33.glDeleteSamplers(sampler);
	}

	public static void samplerParameteri(int sampler, int pname, int param) {
		if (!supportsSamplerObjects || sampler == 0) return;
		GL33.glSamplerParameteri(sampler, pname, param);
	}

	public static void bindSamplerToUnit(int unit, int sampler) {
		if (!supportsSamplerObjects) return;
		if (samplers[unit] == sampler) return;
		GL33.glBindSampler(unit, sampler);
		samplers[unit] = sampler;
	}

	public static void unbindAllSamplers() {
		if (!supportsSamplerObjects) return;
		for (int i = 0; i < samplers.length; i++) {
			if (samplers[i] != 0) {
				GL33.glBindSampler(i, 0);
				samplers[i] = 0;
			}
		}
	}
}
