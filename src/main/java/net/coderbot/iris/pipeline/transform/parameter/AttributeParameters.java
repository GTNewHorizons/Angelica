package net.coderbot.iris.pipeline.transform.parameter;

import com.gtnewhorizons.angelica.glsm.ffp.Instancing;
import com.gtnewhorizons.angelica.glsm.shader.ShaderDiskCache;
import net.coderbot.iris.gbuffer_overrides.matching.InputAvailability;
import net.coderbot.iris.pipeline.transform.Patch;
import net.coderbot.iris.shaderpack.texture.TextureStage;

public class AttributeParameters extends Parameters {
	public final boolean hasGeometry;
	public final boolean hasTesselation;
	public final InputAvailability inputs;
	public final boolean scrollGlint;
	public final Instancing instancing;
	public final boolean wideLines;
	// WARNING: adding new fields requires updating hashCode and equals methods!

	public AttributeParameters(Patch patch, boolean hasGeometry, boolean hasTesselation, InputAvailability inputs, boolean scrollGlint, Instancing instancing) {
		this(patch, hasGeometry, hasTesselation, inputs, scrollGlint, instancing, false);
	}

	public AttributeParameters(Patch patch, boolean hasGeometry, boolean hasTesselation, InputAvailability inputs, boolean scrollGlint, Instancing instancing, boolean wideLines) {
		super(patch, null);
		this.hasGeometry = hasGeometry;
		this.hasTesselation = hasTesselation;
		this.inputs = inputs;
		this.scrollGlint = scrollGlint;
		this.instancing = instancing;
		this.wideLines = wideLines;
	}

	@Override
	public TextureStage getTextureStage() {
		return TextureStage.GBUFFERS_AND_SHADOW;
	}

	@Override
	public void appendDiskKey(ShaderDiskCache.Key k) {
		super.appendDiskKey(k);
		k.b(hasGeometry).b(hasTesselation).str(inputs == null ? null : inputs.name()).b(scrollGlint).str(instancing.name()).b(wideLines);
	}

	@Override
	public int hashCode() {
		final int prime = 31;
		int result = super.hashCode();
		result = prime * result + (hasGeometry ? 1231 : 1237);
		result = prime * result + (hasTesselation ? 1231 : 1237);
		result = prime * result + (scrollGlint ? 1231 : 1237);
		result = prime * result + instancing.ordinal();
		result = prime * result + (wideLines ? 1231 : 1237);
		result = prime * result + ((inputs == null) ? 0 : inputs.hashCode());
		return result;
	}

	@Override
	public boolean equals(Object obj) {
		if (this == obj)
			return true;
		if (!super.equals(obj))
			return false;
		if (getClass() != obj.getClass())
			return false;
		AttributeParameters other = (AttributeParameters) obj;
		if (hasGeometry != other.hasGeometry)
			return false;
		if (hasTesselation != other.hasTesselation)
			return false;
		if (scrollGlint != other.scrollGlint)
			return false;
		if (instancing != other.instancing)
			return false;
		if (wideLines != other.wideLines)
			return false;
		if (inputs == null) {
            return other.inputs == null;
		} else return inputs.equals(other.inputs);
    }
}
