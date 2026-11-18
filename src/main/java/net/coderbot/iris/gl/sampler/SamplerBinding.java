package net.coderbot.iris.gl.sampler;

import com.gtnewhorizons.angelica.glsm.RenderSystem;
import net.coderbot.iris.gl.state.ValueUpdateNotifier;
import com.gtnewhorizons.angelica.glsm.texture.TextureType;

import java.util.function.IntSupplier;
import java.util.function.Supplier;

public class SamplerBinding {
	private final Runnable updateListener = this::updateSampler;
	private final TextureType textureType;
	private final int textureUnit;
	private final IntSupplier texture;
	private final Supplier<GlSampler> sampler;
	private final ValueUpdateNotifier notifier;

	public SamplerBinding(TextureType type, int textureUnit, IntSupplier texture, Supplier<GlSampler> sampler, ValueUpdateNotifier notifier) {
		this.textureType = type;
		this.textureUnit = textureUnit;
		this.texture = texture;
		this.sampler = sampler;
		this.notifier = notifier;
	}

	public void update() {
		updateSampler();

		if (notifier != null) {
			notifier.setListener(updateListener);
		}
	}

	private void updateSampler() {
		RenderSystem.bindSamplerToUnit(textureUnit, GlSampler.idOf(sampler));
		RenderSystem.bindTextureToUnit(textureType.getGlType(), textureUnit, texture.getAsInt());
	}
}
