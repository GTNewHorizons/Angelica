package net.coderbot.iris.postprocess;

import com.google.common.collect.ImmutableSet;
import com.gtnewhorizons.angelica.glsm.GLStateManager;
import com.gtnewhorizons.angelica.glsm.RenderSystem;
import com.gtnewhorizons.angelica.glsm.managers.GLTextureManager;
import net.coderbot.iris.gl.framebuffer.GlFramebuffer;
import net.coderbot.iris.gl.program.Program;
import net.coderbot.iris.gl.program.ProgramBuilder;
import net.coderbot.iris.gl.program.ProgramSamplers;
import net.coderbot.iris.gl.program.ProgramUniforms;
import net.coderbot.iris.gl.texture.DepthCopyStrategy;
import net.coderbot.iris.gl.texture.InternalTextureFormat;
import net.coderbot.iris.gl.texture.PixelType;
import net.coderbot.iris.gl.uniform.UniformUpdateFrequency;
import net.coderbot.iris.uniforms.SystemTimeUniforms;
import net.minecraft.client.Minecraft;
import org.apache.commons.io.IOUtils;
import org.lwjgl.opengl.GL11;
import org.lwjgl.opengl.GL12;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.Objects;
import java.util.function.IntSupplier;

public class CenterDepthSampler {
	private static final double LN2 = Math.log(2);
	private boolean hasFirstSample;
	private boolean everRetrieved;
	private final Program program;
	private final GlFramebuffer framebuffer;
	private final int texture;
	private final int altTexture;
	private boolean destroyed;

	public CenterDepthSampler(IntSupplier depthSupplier, float halfLife) {
		this.texture = GL11.glGenTextures();
		this.altTexture = GL11.glGenTextures();
		this.framebuffer = new GlFramebuffer();

		// Fall back to a less precise format if the system doesn't support OpenGL 3
		InternalTextureFormat format = GLStateManager.capabilities.OpenGL32 ? InternalTextureFormat.R32F : InternalTextureFormat.RGB16;
		setupColorTexture(texture, format);
		setupColorTexture(altTexture, format);
		GLTextureManager.glBindTexture(GL11.GL_TEXTURE_2D, 0);

		this.framebuffer.addColorAttachment(0, texture);
		ProgramBuilder builder;

		try {
			String fsh = new String(IOUtils.toByteArray(Objects.requireNonNull(getClass().getResourceAsStream("/centerDepth.fsh"))), StandardCharsets.UTF_8);

			if (GLStateManager.capabilities.OpenGL32) {
				fsh = fsh.replace("VERSIONPLACEHOLDER", "150 compatibility");
			} else {
				fsh = fsh.replace("#define IS_GL3", "");
				fsh = fsh.replace("VERSIONPLACEHOLDER", "120");
			}
			String vsh = new String(IOUtils.toByteArray(Objects.requireNonNull(getClass().getResourceAsStream("/centerDepth.vsh"))), StandardCharsets.UTF_8);

			builder = ProgramBuilder.begin("centerDepthSmooth", vsh, null, fsh, ImmutableSet.of(0, 1, 2));
		} catch (IOException e) {
			throw new RuntimeException(e);
		}

		builder.addDynamicSampler(depthSupplier, "depth");
		builder.addDynamicSampler(() -> altTexture, "altDepth");
		builder.uniform1f(UniformUpdateFrequency.PER_FRAME, "lastFrameTime", SystemTimeUniforms.TIMER::getLastFrameTime);
		builder.uniform1f(UniformUpdateFrequency.ONCE, "decay", () -> (1.0f / ((halfLife * 0.1) / LN2)));
		this.program = builder.build();
	}

	public void sampleCenterDepth() {
		if ((hasFirstSample && (!everRetrieved)) || destroyed) {
			// If the shaderpack isn't reading center depth values, don't bother sampling it
			// This improves performance with most shaderpacks
			return;
		}

		hasFirstSample = true;

		this.framebuffer.bind();
		this.program.use();

		GL11.glViewport(0, 0, 1, 1);

		FullScreenQuadRenderer.INSTANCE.render();

		ProgramUniforms.clearActiveUniforms();
		ProgramSamplers.clearActiveSamplers();

		// The API contract of DepthCopyStrategy claims it can only copy depth, however the 2 non-stencil methods used are entirely capable of copying color as of now.
		DepthCopyStrategy.fastest(false).copy(this.framebuffer, texture, null, altTexture, 1, 1);

		//Reset viewport
        Minecraft.getMinecraft().getFramebuffer().bindFramebuffer(true);
	}

	public void setupColorTexture(int texture, InternalTextureFormat format) {
		RenderSystem.texImage2D(texture, GL11.GL_TEXTURE_2D, 0, format.getGlFormat(), 1, 1, 0, format.getPixelFormat().getGlFormat(), PixelType.FLOAT.getGlFormat(), null);

		RenderSystem.texParameteri(texture, GL11.GL_TEXTURE_2D, GL11.GL_TEXTURE_MIN_FILTER, GL11.GL_LINEAR);
		RenderSystem.texParameteri(texture, GL11.GL_TEXTURE_2D, GL11.GL_TEXTURE_MAG_FILTER, GL11.GL_LINEAR);
		RenderSystem.texParameteri(texture, GL11.GL_TEXTURE_2D, GL11.GL_TEXTURE_WRAP_S, GL12.GL_CLAMP_TO_EDGE);
		RenderSystem.texParameteri(texture, GL11.GL_TEXTURE_2D, GL11.GL_TEXTURE_WRAP_T, GL12.GL_CLAMP_TO_EDGE);
	}

	public int getCenterDepthTexture() {
		return altTexture;
	}

	public void setUsage(boolean usage) {
		everRetrieved |= usage;
	}

	public void destroy() {
		GLTextureManager.glDeleteTextures(texture);
		GLTextureManager.glDeleteTextures(altTexture);
		framebuffer.destroy();
		program.destroy();
		destroyed = true;
	}
}
