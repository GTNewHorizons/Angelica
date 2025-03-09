package org.embeddedt.embeddium.impl.render.chunk.region;

import it.unimi.dsi.fastutil.longs.Long2ReferenceOpenHashMap;
import it.unimi.dsi.fastutil.objects.ObjectIterable;
import it.unimi.dsi.fastutil.objects.ObjectSet;
import it.unimi.dsi.fastutil.objects.Reference2ReferenceMap;
import it.unimi.dsi.fastutil.objects.Reference2ReferenceOpenHashMap;
import org.embeddedt.embeddium.impl.gl.arena.PendingUpload;
import org.embeddedt.embeddium.impl.gl.arena.staging.FallbackStagingBuffer;
import org.embeddedt.embeddium.impl.gl.arena.staging.MappedStagingBuffer;
import org.embeddedt.embeddium.impl.gl.arena.staging.StagingBuffer;
import org.embeddedt.embeddium.impl.gl.device.CommandList;
import org.embeddedt.embeddium.impl.gl.device.RenderDevice;
import org.embeddedt.embeddium.impl.render.chunk.RenderPassConfiguration;
import org.embeddedt.embeddium.impl.render.chunk.RenderSection;
import org.embeddedt.embeddium.impl.render.chunk.compile.ChunkBuildOutput;
import org.embeddedt.embeddium.impl.render.chunk.data.BuiltSectionMeshParts;
import org.embeddedt.embeddium.impl.render.chunk.terrain.TerrainRenderPass;
import org.jetbrains.annotations.NotNull;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Iterator;
import java.util.List;
import java.util.Objects;

public class RenderRegionManager {
    private final Long2ReferenceOpenHashMap<RenderRegion> regions = new Long2ReferenceOpenHashMap<>();

    private final StagingBuffer stagingBuffer;

    private final RenderPassConfiguration<?> renderPassConfiguration;

    public RenderRegionManager(CommandList commandList, RenderPassConfiguration<?> renderPassConfiguration) {
        this.stagingBuffer = createStagingBuffer(commandList);
        this.renderPassConfiguration = renderPassConfiguration;
    }

    public void update() {
        this.stagingBuffer.flip();

        try (CommandList commandList = RenderDevice.INSTANCE.createCommandList()) {
            Iterator<RenderRegion> it = this.regions.values()
                    .iterator();

            while (it.hasNext()) {
                RenderRegion region = it.next();
                region.update(commandList);

                if (region.isEmpty()) {
                    region.delete(commandList);

                    it.remove();
                }
            }
        }
    }

    public void uploadMeshes(CommandList commandList, Collection<ChunkBuildOutput> results) {
        for (var entry : this.createMeshUploadQueues(results)) {
            this.uploadMeshes(commandList, entry.getKey(), entry.getValue().stream().filter(o -> !o.isIndexOnlyUpload()).toList());
            this.uploadResorts(commandList, entry.getKey(), entry.getValue().stream().filter(ChunkBuildOutput::isIndexOnlyUpload).toList());
        }
    }

    /* Copied from fastutil 8 as we don't have access to it when limited to fastutil 7 */
    private static <K, V> ObjectIterable<Reference2ReferenceMap.Entry<K, V>> fastIterable(Reference2ReferenceMap<K, V> map) {
        final ObjectSet<Reference2ReferenceMap.Entry<K, V>> entries = map.reference2ReferenceEntrySet();
        return entries instanceof Reference2ReferenceMap.FastEntrySet ? () -> ((Reference2ReferenceMap.FastEntrySet<K, V>)entries).fastIterator() : entries;
    }

    private void uploadMeshes(CommandList commandList, RenderRegion region, Collection<ChunkBuildOutput> results) {
        var uploads = new ArrayList<PendingSectionUpload>();

        boolean needIndexBuffer = false;

        for (ChunkBuildOutput result : results) {
            // Delete all existing data for the section in the region
            region.removeMeshes(result.render.getSectionIndex());

            // Add uploads for any new data
            for (var entry : fastIterable(result.meshes)) {
                BuiltSectionMeshParts mesh = Objects.requireNonNull(entry.getValue());

                needIndexBuffer |= mesh.getIndexData() != null;

                uploads.add(new PendingSectionUpload(result.render, mesh, entry.getKey(),
                        new PendingUpload(mesh.getVertexData()), mesh.getIndexData() != null ? new PendingUpload(mesh.getIndexData()) : null));
            }
        }

        // If we have nothing to upload, abort!
        if (uploads.isEmpty()) {
            return;
        }

        var resources = region.createResources(commandList);
        var geometryArena = resources.getGeometryArena();

        boolean bufferChanged = geometryArena.upload(commandList, uploads.stream()
                .map(upload -> upload.vertexUpload));

        if (needIndexBuffer) {
            bufferChanged |= resources.getOrCreateIndexArena(commandList).upload(commandList, uploads.stream()
                    .map(upload -> upload.indexUpload).filter(Objects::nonNull));
        }


        // If any of the buffers changed, the tessellation will need to be updated
        // Once invalidated the tessellation will be re-created on the next attempted use
        if (bufferChanged) {
            region.refresh(commandList);
        }

        // Collect the upload results
        for (PendingSectionUpload upload : uploads) {
            var storage = region.createStorage(upload.pass);
            storage.setMeshes(upload.section.getSectionIndex(),
                    upload.vertexUpload.getResult(), upload.indexUpload != null ? upload.indexUpload.getResult() : null, upload.meshData.getVertexRanges());
        }

        region.removeEmptyStorages();
    }

    private void uploadResorts(CommandList commandList, RenderRegion region, Collection<ChunkBuildOutput> results) {
        var uploads = new ArrayList<PendingResortUpload>();

        for (ChunkBuildOutput result : results) {
            for (var entry : fastIterable(result.meshes)) {
                var pass = entry.getKey();
                var mesh = entry.getValue();

                var storage = region.getStorage(pass);

                if (storage != null) {
                    storage.removeIndexBuffer(result.render.getSectionIndex());
                }

                Objects.requireNonNull(mesh.getIndexData());

                uploads.add(new PendingResortUpload(result.render, mesh, pass, new PendingUpload(mesh.getIndexData())));
            }
        }

        // If we have nothing to upload, abort!
        if (uploads.isEmpty()) {
            return;
        }

        var resources = region.createResources(commandList);

        boolean bufferChanged = resources.getOrCreateIndexArena(commandList).upload(commandList, uploads.stream()
                .map(upload -> upload.indexUpload).filter(Objects::nonNull));

        // If any of the buffers changed, the tessellation will need to be updated
        // Once invalidated the tessellation will be re-created on the next attempted use
        if (bufferChanged) {
            region.refresh(commandList);
        }

        // Collect the upload results
        for (PendingResortUpload upload : uploads) {
            var storage = region.createStorage(upload.pass);
            storage.replaceIndexBuffer(upload.section.getSectionIndex(), upload.indexUpload.getResult());
        }
    }

    private Reference2ReferenceMap.FastEntrySet<RenderRegion, List<ChunkBuildOutput>> createMeshUploadQueues(Collection<ChunkBuildOutput> results) {
        var map = new Reference2ReferenceOpenHashMap<RenderRegion, List<ChunkBuildOutput>>();

        for (var result : results) {
            var queue = map.computeIfAbsent(result.render.getRegion(), k -> new ArrayList<>());
            queue.add(result);
        }

        return map.reference2ReferenceEntrySet();
    }

    public void delete(CommandList commandList) {
        for (RenderRegion region : this.regions.values()) {
            region.delete(commandList);
        }

        this.regions.clear();
        this.stagingBuffer.delete(commandList);
    }

    public Collection<RenderRegion> getLoadedRegions() {
        return this.regions.values();
    }

    public StagingBuffer getStagingBuffer() {
        return this.stagingBuffer;
    }

    public RenderRegion createForChunk(int chunkX, int chunkY, int chunkZ) {
        return this.create(chunkX >> RenderRegion.REGION_WIDTH_SH,
                chunkY >> RenderRegion.REGION_HEIGHT_SH,
                chunkZ >> RenderRegion.REGION_LENGTH_SH);
    }

    @NotNull
    private RenderRegion create(int x, int y, int z) {
        var key = RenderRegion.key(x, y, z);
        var instance = this.regions.get(key);

        if (instance == null) {
            this.regions.put(key, instance = new RenderRegion(x, y, z, this.stagingBuffer, this.renderPassConfiguration.vertexType().getVertexFormat().getStride()));
        }

        return instance;
    }

    private record PendingSectionUpload(RenderSection section, BuiltSectionMeshParts meshData, TerrainRenderPass pass, PendingUpload vertexUpload, PendingUpload indexUpload) {
        private PendingSectionUpload(RenderSection section, BuiltSectionMeshParts meshData, TerrainRenderPass pass, PendingUpload vertexUpload) {
            this(section, meshData, pass, vertexUpload, null);
        }
    }

    private record PendingResortUpload(RenderSection section, BuiltSectionMeshParts meshData, TerrainRenderPass pass, PendingUpload indexUpload) {

    }


    private static StagingBuffer createStagingBuffer(CommandList commandList) {
        if (MappedStagingBuffer.isSupported(RenderDevice.INSTANCE)) {
            return new MappedStagingBuffer(commandList);
        }

        return new FallbackStagingBuffer(commandList);
    }
}
