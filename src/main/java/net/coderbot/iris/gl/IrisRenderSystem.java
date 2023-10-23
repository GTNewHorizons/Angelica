package net.coderbot.iris.gl;

import net.coderbot.iris.Iris;
import net.minecraft.client.renderer.OpenGlHelper;
import org.jetbrains.annotations.Nullable;
import org.joml.Vector3i;
import org.lwjgl.opengl.ARBDirectStateAccess;
import org.lwjgl.opengl.EXTShaderImageLoadStore;
import org.lwjgl.opengl.GL11;
import org.lwjgl.opengl.GL13;
import org.lwjgl.opengl.GL15;
import org.lwjgl.opengl.GL20;
import org.lwjgl.opengl.GL30;
import org.lwjgl.opengl.GL40;
import org.lwjgl.opengl.GL42;
import org.lwjgl.opengl.GL43;
import org.lwjgl.opengl.GL45;

import java.nio.ByteBuffer;
import java.nio.FloatBuffer;
import java.nio.IntBuffer;

/**
 * This class is responsible for abstracting calls to OpenGL and asserting that calls are run on the render thread.
 */
public class IrisRenderSystem {
	private static DSAAccess dsaState;
	private static boolean hasMultibind;
	private static boolean supportsCompute;

	public static void initRenderer() {
		if (Iris.capabilities.OpenGL45) {
			dsaState = new DSACore();
			Iris.logger.info("OpenGL 4.5 detected, enabling DSA.");
		} else if (Iris.capabilities.GL_ARB_direct_state_access) {
			dsaState = new DSAARB();
			Iris.logger.info("ARB_direct_state_access detected, enabling DSA.");
		} else {
			dsaState = new DSAUnsupported();
			Iris.logger.info("DSA support not detected.");
		}

        hasMultibind = Iris.capabilities.OpenGL45 || Iris.capabilities.GL_ARB_multi_bind;

		supportsCompute = supportsCompute();
	}

	public static void getIntegerv(int pname, int[] params) {
        // TODO: Iris Shaders - IntBuffer
		GL11.glGetInteger(pname, IntBuffer.wrap(params));
	}

	public static void getFloatv(int pname, float[] params) {
        // TODO: Iris Shaders - FloatBuffer
		GL11.glGetFloat(pname, FloatBuffer.wrap(params));
	}

	public static void generateMipmaps(int texture, int mipmapTarget) {
		dsaState.generateMipmaps(texture, mipmapTarget);
	}

	public static void bindAttributeLocation(int program, int index, CharSequence name) {
        GL20.glBindAttribLocation(program, index, name);
	}

	public static void texImage2D(int texture, int target, int level, int internalformat, int width, int height, int border, int format, int type, @Nullable ByteBuffer pixels) {
		GL11.glBindTexture(GL11.GL_TEXTURE_2D, texture);
        GL11.glTexImage2D(target, level, internalformat, width, height, border, format, type, pixels);
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

	public static void texParameteriv(int texture, int target, int pname, int[] params) {
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

	public static void readPixels(int x, int y, int width, int height, int format, int type, float[] pixels) {
		GL11.glReadPixels(x, y, width, height, format, type, FloatBuffer.wrap(pixels));
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
		if (Iris.capabilities.OpenGL42) {
			GL42.glBindImageTexture(unit, texture, level, layered, layer, access, format);
		} else {
			EXTShaderImageLoadStore.glBindImageTextureEXT(unit, texture, level, layered, layer, access, format);
		}
	}

	public static int getMaxImageUnits() {
		if (Iris.capabilities.OpenGL42) {
			return GL11.glGetInteger(GL42.GL_MAX_IMAGE_UNITS);
		} else if (Iris.capabilities.GL_EXT_shader_image_load_store) {
			return GL11.glGetInteger(EXTShaderImageLoadStore.GL_MAX_IMAGE_UNITS_EXT);
		} else {
			return 0;
		}
	}

	public static void getProgramiv(int program, int value, int[] storage) {
        GL20.glGetProgram(program, value, IntBuffer.wrap(storage));
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
		return Iris.capabilities.GL_ARB_draw_buffers_blend || Iris.capabilities.OpenGL40;
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

    public static void setupProjectionMatrix(float[] matrix) {
        GL11.glMatrixMode(GL11.GL_PROJECTION);
        GL11.glPushMatrix();
        GL11.glLoadMatrix(FloatBuffer.wrap(matrix));
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

	public interface DSAAccess {
		void generateMipmaps(int texture, int target);

		void texParameteri(int texture, int target, int pname, int param);
		void texParameterf(int texture, int target, int pname, float param);
		void texParameteriv(int texture, int target, int pname, int[] params);

		void readBuffer(int framebuffer, int buffer);

		void drawBuffers(int framebuffer, IntBuffer buffers);

		int getTexParameteri(int texture, int target, int pname);

		void copyTexSubImage2D(int destTexture, int target, int i, int i1, int i2, int i3, int i4, int width, int height);

		void bindTextureToUnit(int unit, int texture);

		int bufferStorage(int target, FloatBuffer data, int usage);

		void blitFramebuffer(int source, int dest, int offsetX, int offsetY, int width, int height, int offsetX2, int offsetY2, int width2, int height2, int bufferChoice, int filter);

		void framebufferTexture2D(int fb, int fbtarget, int attachment, int target, int texture, int levels);

		int createFramebuffer();
		int createTexture(int target);
	}

	public static class DSACore extends DSAARB {

	}

	public static class DSAARB extends DSAUnsupported {

		@Override
		public void generateMipmaps(int texture, int target) {
			ARBDirectStateAccess.glGenerateTextureMipmap(texture);
		}

		@Override
		public void texParameteri(int texture, int target, int pname, int param) {
			ARBDirectStateAccess.glTextureParameteri(texture, pname, param);
		}

		@Override
		public void texParameterf(int texture, int target, int pname, float param) {
			ARBDirectStateAccess.glTextureParameterf(texture, pname, param);
		}

		@Override
		public void texParameteriv(int texture, int target, int pname, int[] params) {
			ARBDirectStateAccess.glTextureParameter(texture, pname, IntBuffer.wrap(params));
		}

		@Override
		public void readBuffer(int framebuffer, int buffer) {
			ARBDirectStateAccess.glNamedFramebufferReadBuffer(framebuffer, buffer);
		}

		@Override
		public void drawBuffers(int framebuffer, IntBuffer buffers) {
			ARBDirectStateAccess.glNamedFramebufferDrawBuffers(framebuffer, buffers);
		}

		@Override
		public int getTexParameteri(int texture, int target, int pname) {
			return ARBDirectStateAccess.glGetTextureParameteri(texture, pname);
		}

		@Override
		public void copyTexSubImage2D(int destTexture, int target, int i, int i1, int i2, int i3, int i4, int width, int height) {
			ARBDirectStateAccess.glCopyTextureSubImage2D(destTexture, i, i1, i2, i3, i4, width, height);
		}

		@Override
		public void bindTextureToUnit(int unit, int texture) {
			if (texture == 0) {
				super.bindTextureToUnit(unit, texture);
			} else {
				ARBDirectStateAccess.glBindTextureUnit(unit, texture);
			}
		}

		@Override
		public int bufferStorage(int target, FloatBuffer data, int usage) {
            int buffer = GL45.glCreateBuffers();
            GL45.glNamedBufferData(buffer, data, usage);
			return buffer;
		}

		@Override
		public void blitFramebuffer(int source, int dest, int offsetX, int offsetY, int width, int height, int offsetX2, int offsetY2, int width2, int height2, int bufferChoice, int filter) {
			ARBDirectStateAccess.glBlitNamedFramebuffer(source, dest, offsetX, offsetY, width, height, offsetX2, offsetY2, width2, height2, bufferChoice, filter);
		}

		@Override
		public void framebufferTexture2D(int fb, int fbtarget, int attachment, int target, int texture, int levels) {
			ARBDirectStateAccess.glNamedFramebufferTexture(fb, attachment, texture, levels);
		}

		@Override
		public int createFramebuffer() {
			return ARBDirectStateAccess.glCreateFramebuffers();
		}

		@Override
		public int createTexture(int target) {
			return ARBDirectStateAccess.glCreateTextures(target);
		}
	}

	public static class DSAUnsupported implements DSAAccess {
		@Override
		public void generateMipmaps(int texture, int target) {
			GL11.glBindTexture(GL11.GL_TEXTURE_2D, texture);
			GL30.glGenerateMipmap(target);
		}

		@Override
		public void texParameteri(int texture, int target, int pname, int param) {
			GL11.glBindTexture(GL11.GL_TEXTURE_2D, texture);
			GL11.glTexParameteri(target, pname, param);
		}

		@Override
		public void texParameterf(int texture, int target, int pname, float param) {
			GL11.glBindTexture(GL11.GL_TEXTURE_2D, texture);
			GL11.glTexParameterf(target, pname, param);
		}

		@Override
		public void texParameteriv(int texture, int target, int pname, int[] params) {
			GL11.glBindTexture(GL11.GL_TEXTURE_2D, texture);
			GL11.glTexParameter(target, pname, IntBuffer.wrap(params));
		}

		@Override
		public void readBuffer(int framebuffer, int buffer) {
			OpenGlHelper.func_153171_g/*glBindFramebuffer*/(GL30.GL_FRAMEBUFFER, framebuffer);
			GL11.glReadBuffer(buffer);
		}

		@Override
		public void drawBuffers(int framebuffer, IntBuffer buffers) {
			OpenGlHelper.func_153171_g/*glBindFramebuffer*/(GL30.GL_FRAMEBUFFER, framebuffer);
			GL20.glDrawBuffers(buffers);
		}

		@Override
		public int getTexParameteri(int texture, int target, int pname) {
			GL11.glBindTexture(GL11.GL_TEXTURE_2D, texture);
			return GL11.glGetTexParameteri(target, pname);
		}

		@Override
		public void copyTexSubImage2D(int destTexture, int target, int i, int i1, int i2, int i3, int i4, int width, int height) {
			int previous = GL11.glGetInteger(GL11.GL_TEXTURE_BINDING_2D);
			GL11.glBindTexture(GL11.GL_TEXTURE_2D, destTexture);
			GL11.glCopyTexSubImage2D(target, i, i1, i2, i3, i4, width, height);
			GL11.glBindTexture(GL11.GL_TEXTURE_2D, previous);
		}

		@Override
		public void bindTextureToUnit(int unit, int texture) {
			GL13.glActiveTexture(GL13.GL_TEXTURE0 + unit);
			GL11.glBindTexture(GL11.GL_TEXTURE_2D, texture);
		}

		@Override
		public int bufferStorage(int target, FloatBuffer data, int usage) {
			int buffer = GL15.glGenBuffers();
			GL15.glBindBuffer(target, buffer);
			bufferData(target, data, usage);
			GL15.glBindBuffer(target, 0);

			return buffer;
		}

		@Override
		public void blitFramebuffer(int source, int dest, int offsetX, int offsetY, int width, int height, int offsetX2, int offsetY2, int width2, int height2, int bufferChoice, int filter) {
			OpenGlHelper.func_153171_g/*glBindFramebuffer*/(GL30.GL_READ_FRAMEBUFFER, source);
			OpenGlHelper.func_153171_g/*glBindFramebuffer*/(GL30.GL_DRAW_FRAMEBUFFER, dest);
			GL30.glBlitFramebuffer(offsetX, offsetY, width, height, offsetX2, offsetY2, width2, height2, bufferChoice, filter);
		}

		@Override
		public void framebufferTexture2D(int fb, int fbtarget, int attachment, int target, int texture, int levels) {
			OpenGlHelper.func_153171_g/*glBindFramebuffer*/(fbtarget, fb);
			GL30.glFramebufferTexture2D(fbtarget, attachment, target, texture, levels);
		}

		@Override
		public int createFramebuffer() {
			int framebuffer = OpenGlHelper.func_153165_e/*glGenFramebuffers*/();
			OpenGlHelper.func_153171_g/*glBindFramebuffer*/(GL30.GL_FRAMEBUFFER, framebuffer);
			return framebuffer;
		}

		@Override
		public int createTexture(int target) {
			int texture = GL11.glGenTextures();
			GL11.glBindTexture(GL11.GL_TEXTURE_2D, texture);
			return texture;
		}
	}

	/*
	public static void bindTextures(int startingTexture, int[] bindings) {
		if (hasMultibind) {
			ARBMultiBind.glBindTextures(startingTexture, bindings);
		} else if (dsaState != DSAState.NONE) {
			for (int binding : bindings) {
				ARBDirectStateAccess.glBindTextureUnit(startingTexture, binding);
				startingTexture++;
			}
		} else {
			for (int binding : bindings) {
				GL13.glActiveTexture(startingTexture);
				GL11.glBindTexture(GL11.GL_TEXTURE_2D, binding);
				startingTexture++;
			}
		}
	}
	 */

	// TODO: Proper notification of compute support
	public static boolean supportsCompute() {
        return Iris.capabilities.GL_ARB_compute_shader;
	}
}
