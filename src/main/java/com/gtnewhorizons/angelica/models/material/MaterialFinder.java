package com.gtnewhorizons.angelica.models.material;

import com.gtnewhorizons.angelica.compat.toremove.RenderLayer;
import com.gtnewhorizons.angelica.models.renderer.IndigoRenderer;
import com.gtnewhorizons.angelica.models.NdQuadBuilder;
import com.gtnewhorizons.angelica.models.fapi.TriState;
import com.gtnewhorizons.angelica.models.json.JsonModel;
import net.minecraft.block.Block;
import net.minecraft.item.ItemStack;

/**
 * Finds standard {@link RenderMaterial} instances used to communicate
 * quad rendering characteristics to a {@link RenderContext}.
 *
 * <p>Must be obtained via {@link IndigoRenderer#materialFinder()}.
 */
public interface MaterialFinder extends MaterialView {
	/**
	 * Defines how sprite pixels will be blended with the scene.
	 *
	 * <p>See {@link BlendMode} for more information.
	 */
	MaterialFinder blendMode(BlendMode blendMode);

	/**
	 * Vertex color(s) will be modified for quad color index unless disabled.
	 */
	MaterialFinder disableColorIndex(boolean disable);

	/**
	 * When true, sprite texture and color will be rendered at full brightness.
	 * Lightmap values provided via {@link NdQuadBuilder#lightmap(int)} will be ignored.
	 * False by default
	 *
	 * <p>This is the preferred method for emissive lighting effects.  Some renderers
	 * with advanced lighting models may not use block lightmaps and this method will
	 * allow per-sprite emissive lighting in future extensions that support overlay sprites.
	 *
	 * <p>Note that color will still be modified by diffuse shading and ambient occlusion,
	 * unless disabled via {@link #disableDiffuse(boolean)} and {@link #ambientOcclusion(TriState)}.
	 */
	MaterialFinder emissive(boolean isEmissive);

	/**
	 * Vertex color(s) will be modified for diffuse shading unless disabled.
	 *
	 * <p>This property is guaranteed to be respected in block contexts. Some renderers may also respect it in item
	 * contexts, but this is not guaranteed.
	 */
	MaterialFinder disableDiffuse(boolean disable);

	/**
	 * Controls whether vertex color(s) will be modified for ambient occlusion.
	 *
	 * <p>By default, ambient occlusion will be used if {@link JsonModel#isUseAO() the model uses ambient occlusion}
	 * and the block has {@link Block#getLightValue() a luminance} of 0.
	 * Set to {@link TriState#TRUE} or {@link TriState#FALSE} to override this behavior.
	 *
	 * <p>This property is respected only in block contexts. It will not have an effect in other contexts.
	 */
	MaterialFinder ambientOcclusion(TriState mode);

	/**
	 * Controls whether glint should be applied.
	 *
	 * <p>By default, glint will be applied in item contexts if {@link ItemStack#isItemEnchanted()} the item stack has glint}.
	 * Set to {@link TriState#TRUE} or {@link TriState#FALSE} to override this behavior.
	 *
	 * <p>This property is guaranteed to be respected in item contexts. Some renderers may also respect it in block
	 * contexts, but this is not guaranteed.
	 */
	MaterialFinder glint(TriState mode);

	/**
	 * Copies all properties from the given {@link MaterialView} to this material finder.
	 */
	MaterialFinder copyFrom(MaterialView material);

	/**
	 * Resets this instance to default values. Values will match those
	 * in effect when an instance is newly obtained via {@link IndigoRenderer#materialFinder()}.
	 */
	MaterialFinder clear();

	/**
	 * Returns the standard material encoding all
	 * of the current settings in this finder. The settings in
	 * this finder are not changed.
	 *
	 * <p>Resulting instances can and should be re-used to prevent
	 * needless memory allocation. {@link IndigoRenderer} implementations
	 * may or may not cache standard material instances.
	 */
	RenderMaterial find();

	/**
	 * @deprecated Use {@link #blendMode(BlendMode)} instead.
	 */
	@Deprecated
	default MaterialFinder blendMode(int spriteIndex, RenderLayer renderLayer) {
		return blendMode(BlendMode.fromRenderLayer(renderLayer));
	}

	/**
	 * @deprecated Use {@link #blendMode(BlendMode)} instead.
	 */
	@Deprecated
	default MaterialFinder blendMode(int spriteIndex, BlendMode blendMode) {
		// Null check is kept for legacy reasons, but the new blendMode method will NPE if passed null!
		if (blendMode == null) {
			blendMode = BlendMode.DEFAULT;
		}

		return blendMode(blendMode);
	}

	/**
	 * @deprecated Use {@link #disableColorIndex(boolean)} instead.
	 */
	@Deprecated
	default MaterialFinder disableColorIndex(int spriteIndex, boolean disable) {
		return disableColorIndex(disable);
	}

	/**
	 * @deprecated Use {@link #emissive(boolean)} instead.
	 */
	@Deprecated
	default MaterialFinder emissive(int spriteIndex, boolean isEmissive) {
		return emissive(isEmissive);
	}

	/**
	 * @deprecated Use {@link #disableDiffuse(boolean)} instead.
	 */
	@Deprecated
	default MaterialFinder disableDiffuse(int spriteIndex, boolean disable) {
		return disableDiffuse(disable);
	}

	/**
	 * @deprecated Use {@link #ambientOcclusion(TriState)} instead.
	 */
	@Deprecated
	default MaterialFinder disableAo(int spriteIndex, boolean disable) {
		return ambientOcclusion(disable ? TriState.FALSE : TriState.DEFAULT);
	}

	/**
	 * Do not use. Does nothing.
	 */
	@Deprecated
	default MaterialFinder spriteDepth(int depth) {
		return this;
	}
}
