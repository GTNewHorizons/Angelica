package net.coderbot.iris.uniforms;

import com.gtnewhorizons.angelica.stereo.StereoState;
import net.coderbot.iris.gl.uniform.UniformHolder;
import net.minecraft.client.Minecraft;

import static net.coderbot.iris.gl.uniform.UniformUpdateFrequency.PER_FRAME;

/**
 * Implements uniforms relating the current viewport
 *
 * @see <a href="https://github.com/IrisShaders/ShaderDoc/blob/master/uniforms.md#viewport">Uniforms: Viewport</a>
 */
public final class ViewportUniforms {
	// cannot be constructed
	private ViewportUniforms() {
	}

	/**
	 * Makes the viewport uniforms available to the given program
	 *
	 * @param uniforms the program to make the uniforms available to
	 */
	public static void addViewportUniforms(UniformHolder uniforms) {
		// TODO: What about the custom scale.composite3 property?
		// NB: It is not safe to cache the render target due to mods like Resolution Control modifying the render target field.
		// During a stereo eye pass, Iris's intermediate framebuffers are sized to the eye's
		// effective dimensions (half-width for SBS_HALF). Shaders compute their own texcoords
		// as gl_FragCoord.xy / vec2(viewWidth, viewHeight), so these uniforms MUST match the
		// FBO dimensions or every shader-internal sampling lookup ends up scaled wrong.
		uniforms
			.uniform1f(PER_FRAME, "viewHeight", ViewportUniforms::getViewHeight)
			.uniform1f(PER_FRAME, "viewWidth", ViewportUniforms::getViewWidth)
			.uniform1f(PER_FRAME, "aspectRatio", ViewportUniforms::getAspectRatio);
	}

	private static float getViewWidth() {
		final int w = Minecraft.getMinecraft().getFramebuffer().framebufferWidth;
		return StereoState.INSTANCE.irisFbWidth(w);
	}

	private static float getViewHeight() {
		final int h = Minecraft.getMinecraft().getFramebuffer().framebufferHeight;
		return StereoState.INSTANCE.irisFbHeight(h);
	}

	/**
	 * @return the current viewport aspect ratio, calculated from the current Minecraft window size
	 */
	private static float getAspectRatio() {
		return getViewWidth() / getViewHeight();
	}
}
