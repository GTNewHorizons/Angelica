package net.coderbot.iris.pipeline.transform.parameter;

import net.coderbot.iris.pipeline.transform.Patch;
import net.coderbot.iris.shaderpack.texture.TextureStage;

public class CeleritasTerrainParameters extends Parameters {
	// WARNING: adding new fields requires updating hashCode and equals methods!

	public CeleritasTerrainParameters(Patch patch) {
		super(patch, null);
	}

	@Override
	public TextureStage getTextureStage() {
		return TextureStage.GBUFFERS_AND_SHADOW;
	}

	// since this class has no additional fields, hashCode() and equals() are inherited from Parameters
}
