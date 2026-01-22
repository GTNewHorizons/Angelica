package net.coderbot.iris.gl.sampler;

import com.gtnewhorizons.angelica.glsm.RenderSystem;
import org.embeddedt.embeddium.impl.gl.GlObject;
import org.lwjgl.opengl.GL11;
import org.lwjgl.opengl.GL12;
import org.lwjgl.opengl.GL14;
import org.lwjgl.opengl.GL30;

public class GlSampler extends GlObject {
	public GlSampler(boolean linear, boolean mipmapped, boolean shadow, boolean hardwareShadow) {
		if (!RenderSystem.supportsSamplerObjects()) {
			this.setHandle(0);
			return;
		}

		this.setHandle(RenderSystem.genSampler());

		RenderSystem.samplerParameteri(getId(), GL11.GL_TEXTURE_MIN_FILTER, linear ? GL11.GL_LINEAR : GL11.GL_NEAREST);
		RenderSystem.samplerParameteri(getId(), GL11.GL_TEXTURE_MAG_FILTER, linear ? GL11.GL_LINEAR : GL11.GL_NEAREST);
		RenderSystem.samplerParameteri(getId(), GL11.GL_TEXTURE_WRAP_S, GL12.GL_CLAMP_TO_EDGE);
		RenderSystem.samplerParameteri(getId(), GL11.GL_TEXTURE_WRAP_T, GL12.GL_CLAMP_TO_EDGE);

		if (mipmapped) {
			RenderSystem.samplerParameteri(getId(), GL11.GL_TEXTURE_MIN_FILTER, linear ? GL11.GL_LINEAR_MIPMAP_LINEAR : GL11.GL_NEAREST_MIPMAP_NEAREST);
		}

		if (hardwareShadow) {
			RenderSystem.samplerParameteri(getId(), GL14.GL_TEXTURE_COMPARE_MODE, GL30.GL_COMPARE_REF_TO_TEXTURE);
		}
	}

	@Override
	protected void destroyInternal() {
		RenderSystem.destroySampler(handle());
	}

	public int getId() {
		return handle();
	}
}
