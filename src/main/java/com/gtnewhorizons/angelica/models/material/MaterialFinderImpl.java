package com.gtnewhorizons.angelica.models.material;

import com.gtnewhorizons.angelica.models.fapi.TriState;

import java.util.Objects;

public class MaterialFinderImpl extends MaterialViewImpl implements MaterialFinder {
	private static int defaultBits = 0;

	static {
		MaterialFinderImpl finder = new MaterialFinderImpl();
		finder.ambientOcclusion(TriState.DEFAULT);
		finder.glint(TriState.DEFAULT);
		defaultBits = finder.bits;

		if (!areBitsValid(defaultBits)) {
			throw new AssertionError("Default MaterialFinder bits are not valid!");
		}
	}

	public MaterialFinderImpl() {
		super(defaultBits);
	}

	@Override
	public MaterialFinder blendMode(BlendMode blendMode) {
		Objects.requireNonNull(blendMode, "BlendMode may not be null");

		bits = (bits & ~BLEND_MODE_MASK) | (blendMode.ordinal() << BLEND_MODE_BIT_OFFSET);
		return this;
	}

	@Override
	public MaterialFinder disableColorIndex(boolean disable) {
		bits = disable ? (bits | COLOR_DISABLE_FLAG) : (bits & ~COLOR_DISABLE_FLAG);
		return this;
	}

	@Override
	public MaterialFinder emissive(boolean isEmissive) {
		bits = isEmissive ? (bits | EMISSIVE_FLAG) : (bits & ~EMISSIVE_FLAG);
		return this;
	}

	@Override
	public MaterialFinder disableDiffuse(boolean disable) {
		bits = disable ? (bits | DIFFUSE_FLAG) : (bits & ~DIFFUSE_FLAG);
		return this;
	}

	@Override
	public MaterialFinder ambientOcclusion(TriState mode) {
		Objects.requireNonNull(mode, "ambient occlusion TriState may not be null");

		bits = (bits & ~AO_MASK) | (mode.ordinal() << AO_BIT_OFFSET);
		return this;
	}

	@Override
	public MaterialFinder glint(TriState mode) {
		Objects.requireNonNull(mode, "glint TriState may not be null");

		bits = (bits & ~GLINT_MASK) | (mode.ordinal() << GLINT_BIT_OFFSET);
		return this;
	}

	@Override
	public MaterialFinder copyFrom(MaterialView material) {
		bits = ((MaterialViewImpl) material).bits;
		return this;
	}

	@Override
	public MaterialFinder clear() {
		bits = defaultBits;
		return this;
	}

	@Override
	public RenderMaterial find() {
		return RenderMaterialImpl.byIndex(bits);
	}
}
