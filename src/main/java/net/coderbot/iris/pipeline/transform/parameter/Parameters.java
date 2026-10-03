package net.coderbot.iris.pipeline.transform.parameter;

import it.unimi.dsi.fastutil.objects.Object2ObjectMap;
import net.coderbot.iris.gl.blending.AlphaTest;
import com.gtnewhorizons.angelica.glsm.shader.ShaderDiskCache;
import com.gtnewhorizons.angelica.glsm.shader.ShaderType;
import com.gtnewhorizons.angelica.glsm.texture.TextureType;
import net.coderbot.iris.helpers.Tri;
import net.coderbot.iris.pipeline.transform.Patch;
import net.coderbot.iris.shaderpack.texture.TextureStage;

import java.util.ArrayList;
import java.util.List;

public abstract class Parameters {
	public final Patch patch;
	private final Object2ObjectMap<Tri<String, TextureType, TextureStage>, String> textureMap;
	public ShaderType type;
	// WARNING: adding new fields requires updating hashCode and equals methods!

	public Parameters(Patch patch, Object2ObjectMap<Tri<String, TextureType, TextureStage>, String> textureMap) {
		this.patch = patch;
		this.textureMap = textureMap;
	}

	public AlphaTest getAlphaTest() {
		return AlphaTest.ALWAYS;
	}

	public abstract TextureStage getTextureStage();

	public Object2ObjectMap<Tri<String, TextureType, TextureStage>, String> getTextureMap() {
		return textureMap;
	}

	public void appendDiskKey(ShaderDiskCache.Key k) {
		k.str(getClass().getName());
		if (textureMap == null) {
			k.i(-1);
			return;
		}
		final List<String> rendered = new ArrayList<>(textureMap.size());
		for (Object2ObjectMap.Entry<Tri<String, TextureType, TextureStage>, String> e : textureMap.object2ObjectEntrySet()) {
			final Tri<String, TextureType, TextureStage> tri = e.getKey();
			rendered.add(tri.first() + "\0" + tri.second().name() + "\0" + tri.third().name() + "\0" + e.getValue());
		}
		k.sortedStrs(rendered);
	}

	@Override
	public int hashCode() {
		final int prime = 31;
		int result = 1;
		result = prime * result + ((patch == null) ? 0 : patch.hashCode());
		result = prime * result + ((type == null) ? 0 : type.hashCode());
		result = prime * result + ((textureMap == null) ? 0 : textureMap.hashCode());
		return result;
	}

	@Override
	public boolean equals(Object obj) {
		if (this == obj)
			return true;
		if (obj == null)
			return false;
		if (getClass() != obj.getClass())
			return false;
		final Parameters other = (Parameters) obj;
		if (patch != other.patch)
			return false;
		if (type != other.type)
			return false;
		if (textureMap == null) {
			return other.textureMap == null;
		} else return textureMap.equals(other.textureMap);
	}
}
