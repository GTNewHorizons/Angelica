package com.gtnewhorizons.angelica.mixins.early.celeritas.terrain;

import com.gtnewhorizons.angelica.rendering.culling.GpuCulling;
import com.gtnewhorizons.angelica.mixins.interfaces.SectionRenderDataStorageRegionAccessor;
import org.embeddedt.embeddium.impl.gl.arena.GlBufferSegment;
import org.embeddedt.embeddium.impl.gl.util.VertexRange;
import org.embeddedt.embeddium.impl.model.quad.properties.ModelQuadFacing;
import org.embeddedt.embeddium.impl.render.chunk.compile.sorting.ChunkPrimitiveType;
import org.embeddedt.embeddium.impl.render.chunk.data.SectionRenderDataStorage;
import org.embeddedt.embeddium.impl.render.chunk.data.SectionRenderDataUnsafe;
import org.embeddedt.embeddium.impl.render.chunk.region.RenderRegion;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import java.util.Arrays;
import java.util.Map;

@Mixin(value = SectionRenderDataStorage.class, remap = false)
public abstract class MixinSectionRenderDataStorage implements SectionRenderDataStorageRegionAccessor {

    @Unique private RenderRegion angelica$region;
    @Unique private int angelica$passIndex = -1;
    @Unique private volatile int[] angelica$slotCache;

    @Override public RenderRegion angelica$getRegion() { return angelica$region; }
    @Override public void angelica$setRegion(RenderRegion region) { this.angelica$region = region; }
    @Override public int angelica$getPassIndex() { return angelica$passIndex; }
    @Override public void angelica$setPassIndex(int passIndex) { this.angelica$passIndex = passIndex; }
    @Override public int[] angelica$getSlotCache() { return angelica$slotCache; }

    @Shadow public abstract long getDataPointer(int sectionIndex);

    @Shadow public abstract SectionRenderDataUnsafe.Strategy getStorageStrategy();

    @Shadow public abstract ChunkPrimitiveType getPrimitiveType();

    @Inject(method = "setMeshes", at = @At("RETURN"))
    private void angelica$onSetMeshes(int localSectionIndex, GlBufferSegment allocation, GlBufferSegment indexAllocation, Map<ModelQuadFacing, VertexRange> ranges, CallbackInfo ci) {
        angelica$pushUpdate(localSectionIndex);
    }

    @Inject(method = "replaceIndexBuffer", at = @At("RETURN"))
    private void angelica$onReplaceIndexBuffer(int localSectionIndex, GlBufferSegment indexAllocation, CallbackInfo ci) {
        angelica$pushUpdate(localSectionIndex);
    }

    @Inject(method = "removeMeshes", at = @At("RETURN"))
    private void angelica$onRemoveMeshes(int localSectionIndex, CallbackInfo ci) {
        final RenderRegion r = angelica$region;
        if (r == null) return;
        GpuCulling.sectionMeta().remove(angelica$passIndex, r.getOriginX(), r.getOriginY(), r.getOriginZ(), localSectionIndex);
        if (angelica$slotCache != null) angelica$slotCache[localSectionIndex] = -1;
    }

    @Inject(method = "updateMeshes", at = @At("RETURN"))
    private void angelica$onUpdateMeshes(int sectionIndex, CallbackInfo ci) {
        angelica$pushUpdate(sectionIndex);
    }

    @Inject(method = "delete", at = @At("HEAD"))
    private void angelica$onDelete(CallbackInfo ci) {
        final int[] cache = angelica$slotCache;
        final RenderRegion r = angelica$region;
        if (cache == null || r == null) return;
        for (int i = 0; i < cache.length; i++) {
            if (cache[i] < 0) continue;
            cache[i] = -1;
            GpuCulling.sectionMeta().remove(angelica$passIndex, r.getOriginX(), r.getOriginY(), r.getOriginZ(), i);
        }
    }

    @Unique
    private void angelica$pushUpdate(int localSectionIndex) {
        final RenderRegion r = angelica$region;
        if (r == null) return;
        final long dataPtr = getDataPointer(localSectionIndex);
        if (SectionRenderDataUnsafe.getSliceMask(dataPtr) == 0) {
            angelica$releaseSlot(r, localSectionIndex);
            return;
        }
        final int slot = GpuCulling.sectionMeta().update(angelica$passIndex, r.getOriginX(), r.getOriginY(), r.getOriginZ(), localSectionIndex, dataPtr, getStorageStrategy(), getPrimitiveType());
        if (slot < 0) return;
        int[] cache = angelica$slotCache;
        if (cache == null) {
            cache = new int[RenderRegion.REGION_SIZE];
            Arrays.fill(cache, -1);
            cache[localSectionIndex] = slot;
            angelica$slotCache = cache;
        } else {
            cache[localSectionIndex] = slot;
        }
    }

    @Unique
    private void angelica$releaseSlot(RenderRegion r, int localSectionIndex) {
        final int[] cache = angelica$slotCache;
        if (cache == null || cache[localSectionIndex] < 0) return;
        cache[localSectionIndex] = -1;
        GpuCulling.sectionMeta().remove(angelica$passIndex, r.getOriginX(), r.getOriginY(), r.getOriginZ(), localSectionIndex);
    }
}
