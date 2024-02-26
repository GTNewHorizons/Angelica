package com.gtnewhorizons.angelica.models.material;

import net.minecraft.util.ResourceLocation;
import net.minecraft.block.Block;

/**
 * All model quads have an associated render material governing
 * how the quad will be rendered.
 *
 * <p>A material instance is always immutable and thread-safe.  References to a material
 * remain valid until the end of the current game session.
 *
 * <p>Materials can be registered and shared between mods using {@link Renderer#registerMaterial(net.minecraft.util.Identifier, RenderMaterial)}.
 * The registering mod is responsible for creating each registered material at startup.
 *
 * <p>Materials are not required to know their registration identity, and two materials
 * with the same attributes may or may not satisfy equality and identity tests. Model
 * implementations should never attempt to analyze materials or implement control logic based on them.
 * They are only tokens for communicating quad attributes to the ModelRenderer.
 *
 * <p>There are three classes of materials...
 *
 * <p><b>STANDARD MATERIALS</b>
 *
 * <p>Standard materials have "normal" rendering with control over lighting,
 * color, and texture blending. In the default renderer, "normal" rendering
 * emulates unmodified Minecraft. Other renderers may offer a different aesthetic.
 *
 * <p>The number of standard materials is finite, but not necessarily small.
 * To find a standard material, use {@link Renderer#materialFinder()}.
 *
 * <p>All renderer implementations should support standard materials.
 *
 * <p><b>SPECIAL MATERIALS</b>
 *
 * <p>Special materials are implemented directly by the Renderer implementation, typically
 * with the aim of providing advanced/extended features. Such materials may offer additional
 * vertex attributes via extensions to {@link MeshBuilder} and {@link MutableQuadView}.
 *
 * <p>Special materials can be obtained using {@link Renderer#materialById(Identifier)}
 * with a known identifier. Renderers may provide other means of access. Popular
 * special materials could be implemented by multiple renderers, however there is
 * no requirement that special materials be cross-compatible.
 */
public interface RenderMaterial extends MaterialView {
	/**
	 * This will be identical to the material that would be obtained by calling {@link MaterialFinder#find()}
	 * on a new, unaltered, {@link MaterialFinder} instance.  It is defined here for clarity and convenience.
	 *
	 * <p>Quads using this material use {@link Block#getRenderBlockPass()} of
	 * the associated block to determine texture blending, honor block color index, are non-emissive, and apply both
	 * diffuse and ambient occlusion shading to vertex colors.
	 *
	 * <p>All standard, non-fluid baked models are rendered using this material.
	 */
	ResourceLocation MATERIAL_STANDARD = new ResourceLocation("fabric", "standard");
}
