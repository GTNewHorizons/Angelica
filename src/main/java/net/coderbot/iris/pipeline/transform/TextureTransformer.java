package net.coderbot.iris.pipeline.transform;

import it.unimi.dsi.fastutil.objects.Object2ObjectMap;
import net.coderbot.iris.gl.texture.TextureType;
import net.coderbot.iris.helpers.Tri;
import net.coderbot.iris.pipeline.transform.parameter.Parameters;
import net.coderbot.iris.shaderpack.texture.TextureStage;
import org.taumc.glsl.Transformer;
import org.taumc.glsl.grammar.GLSLLexer;

final class TextureTransformer {
	private TextureTransformer() {
	}

	static void transform(Transformer transformer, Parameters parameters) {
		final Object2ObjectMap<Tri<String, TextureType, TextureStage>, String> textureMap = parameters.getTextureMap();
		if (textureMap == null || textureMap.isEmpty()) {
			return;
		}

		final TextureStage stage = parameters.getTextureStage();
		textureMap.forEach((key, replacement) -> {
			if (key.third() != stage) {
				return;
			}

			final String name = key.first();
			final int type = transformer.findType(name);
			if (type != 0 && isTypeValid(key.second(), type)) {
				transformer.rename(name, replacement);
			}
		});
	}

	private static boolean isTypeValid(TextureType expectedType, int extractedType) {
		return switch (expectedType) {
			case TEXTURE_1D -> extractedType == GLSLLexer.SAMPLER1D ||
				extractedType == GLSLLexer.ISAMPLER1D ||
				extractedType == GLSLLexer.USAMPLER1D;
			case TEXTURE_RECTANGLE -> extractedType == GLSLLexer.SAMPLER2DRECT ||
				extractedType == GLSLLexer.ISAMPLER2DRECT ||
				extractedType == GLSLLexer.USAMPLER2DRECT;
			case TEXTURE_2D -> extractedType == GLSLLexer.SAMPLER2D ||
				extractedType == GLSLLexer.ISAMPLER2D ||
				extractedType == GLSLLexer.USAMPLER2D;
			case TEXTURE_3D -> extractedType == GLSLLexer.SAMPLER3D ||
				extractedType == GLSLLexer.ISAMPLER3D ||
				extractedType == GLSLLexer.USAMPLER3D;
		};
	}
}
