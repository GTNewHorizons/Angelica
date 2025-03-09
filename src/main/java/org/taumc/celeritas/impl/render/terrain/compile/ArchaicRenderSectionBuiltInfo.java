package org.taumc.celeritas.impl.render.terrain.compile;

import net.minecraft.client.renderer.texture.TextureAtlasSprite;
import net.minecraft.tileentity.TileEntity;
import org.embeddedt.embeddium.impl.common.datastructure.ContextBundle;
import org.embeddedt.embeddium.impl.render.chunk.RenderSection;

import java.util.Collection;
import java.util.List;

public class ArchaicRenderSectionBuiltInfo {
    /**
     * The collection of animated sprites contained by this rendered chunk section.
     */
    public static final ContextBundle.Key<RenderSection, Collection<TextureAtlasSprite>> ANIMATED_SPRITES = new ContextBundle.Key<>(RenderSection.class, List.of());
    /**
     * The collection of block entities contained by this rendered chunk, which are not part of its culling
     * volume. These entities should always be rendered regardless of the render being visible in the frustum.
     */
    public static final ContextBundle.Key<RenderSection, List<TileEntity>> GLOBAL_BLOCK_ENTITIES = new ContextBundle.Key<>(RenderSection.class, List.of());
    /**
     * The collection of block entities contained by this rendered chunk, which are part of its culling volume.
     */
    public static final ContextBundle.Key<RenderSection, List<TileEntity>> CULLED_BLOCK_ENTITIES = new ContextBundle.Key<>(RenderSection.class, List.of());
    public static final ContextBundle.Key<RenderSection, Boolean> HAS_BLOCK_GEOMETRY = new ContextBundle.Key<>(RenderSection.class, false);
}
