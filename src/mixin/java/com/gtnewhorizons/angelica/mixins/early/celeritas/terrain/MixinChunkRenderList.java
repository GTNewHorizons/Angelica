package com.gtnewhorizons.angelica.mixins.early.celeritas.terrain;

import com.gtnewhorizons.angelica.mixins.interfaces.ChunkRenderListReuse;
import org.embeddedt.embeddium.impl.render.chunk.lists.ChunkRenderList;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;

@Mixin(value = ChunkRenderList.class, remap = false)
public abstract class MixinChunkRenderList implements ChunkRenderListReuse {
    @Shadow private int sectionsWithGeometryCount;
    @Shadow private int sectionsWithSpritesCount;
    @Shadow private int sectionsWithEntitiesCount;
    @Shadow private int sectionsNeedingDynamicSortCount;
    @Shadow private int size;

    @Override
    public void angelica$reset() {
        this.sectionsWithGeometryCount = 0;
        this.sectionsWithSpritesCount = 0;
        this.sectionsWithEntitiesCount = 0;
        this.sectionsNeedingDynamicSortCount = 0;
        this.size = 0;
    }
}
