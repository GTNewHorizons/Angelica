package org.embeddedt.embeddium.impl.render.chunk;

import lombok.Getter;
import org.embeddedt.embeddium.impl.common.datastructure.ContextBundle;
import org.embeddedt.embeddium.impl.render.chunk.lists.RenderVisualsService;
import org.embeddedt.embeddium.impl.render.chunk.occlusion.VisibilityEncoding;
import org.embeddedt.embeddium.impl.render.chunk.region.RenderRegion;
import org.embeddedt.embeddium.impl.render.chunk.sorting.TranslucentQuadAnalyzer;
import org.embeddedt.embeddium.impl.render.chunk.terrain.TerrainRenderPass;
import org.embeddedt.embeddium.impl.util.task.CancellationToken;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.taumc.celeritas.impl.render.terrain.ArchaicRenderVisualsService;

import java.util.Collections;
import java.util.Map;
import java.util.ServiceLoader;

/**
 * The render state object for a chunk section. This contains all the graphics state for each render pass along with
 * data about the render in the chunk visibility graph.
 */
public class RenderSection extends AbstractSection {
    private static final RenderVisualsService VISUALS_SERVICE = new ArchaicRenderVisualsService();

    // Render Region State
    private final RenderRegion region;

    // We must use EVERYTHING here for the default visibility encoding, so that ContextBundle.empty() would correspond
    // to the data generated for an empty section.
    public static final ContextBundle.Key<RenderSection, Long> VISIBILITY_DATA = new ContextBundle.Key<>(RenderSection.class, VisibilityEncoding.EVERYTHING);

    // Rendering State
    private boolean built = false; // merge with the flags?
    private ContextBundle<RenderSection> contextData;
    private boolean hasAnythingToRender;
    @Getter
    private int visualsServiceFlags;

    /**
     * A mapping from translucent render passes to the sort state for that particular pass (which contains data needed
     * to perform a resort of the geometry as the camera moves). Will be empty for sections without any translucent
     * render passes.
     */
    @Getter
    @NotNull
    private Map<TerrainRenderPass, TranslucentQuadAnalyzer.SortState> translucencySortStates = Collections.emptyMap();

    @Getter
    private TranslucentQuadAnalyzer.Level highestSortingLevel = TranslucentQuadAnalyzer.Level.NONE;

    @Getter
    private boolean needsDynamicTranslucencySorting;

    // Pending Update State
    @Nullable
    private CancellationToken buildCancellationToken = null;

    @Nullable
    private ChunkUpdateType pendingUpdateType;

    private int lastBuiltFrame = -1;
    private int lastSubmittedFrame = -1;

    // Lifetime state
    private boolean disposed;

    // Used by the translucency sorter, to determine when a section needs sorting again
    public double lastCameraX, lastCameraY, lastCameraZ;

    public RenderSection(RenderRegion region, int chunkX, int chunkY, int chunkZ) {
        super(chunkX, chunkY, chunkZ);

        this.region = region;

        this.contextData = null;
        this.updateCachedContextDataFlags();
    }

    /**
     * Deletes all data attached to this render and drops any pending tasks. This should be used when the render falls
     * out of view or otherwise needs to be destroyed. After the render has been destroyed, the object can no longer
     * be used.
     */
    public void delete() {
        if (this.buildCancellationToken != null) {
            this.buildCancellationToken.setCancelled();
            this.buildCancellationToken = null;
        }

        this.clearRenderState();
        this.disposed = true;
    }

    public void setInfo(@Nullable ContextBundle<RenderSection> info) {
        if (info != null) {
            this.setRenderState(info);
        } else {
            this.clearRenderState();
        }
    }

    private void setRenderState(@NotNull ContextBundle<RenderSection> info) {
        this.built = true;
        this.contextData = info;
        this.updateCachedContextDataFlags();
    }

    private void clearRenderState() {
        this.built = false;
        this.contextData = null;
        this.updateCachedContextDataFlags();
    }

    public boolean isDisposed() {
        return this.disposed;
    }

    public boolean isBuilt() {
        return this.built;
    }

    public RenderRegion getRegion() {
        return this.region;
    }

    public @Nullable ContextBundle<RenderSection> getBuiltContext() {
        return this.contextData;
    }

    public <T> T getContextOrDefault(ContextBundle.Key<RenderSection, T> key) {
        var ctx = this.contextData;
        if (ctx != null) {
            return ctx.getContext(key);
        } else {
            return key.defaultValue();
        }
    }

    public void updateCachedContextDataFlags() {
        this.hasAnythingToRender = this.contextData != null && this.contextData.hasAnyContext();
        if (this.hasAnythingToRender) {
            this.visualsServiceFlags = VISUALS_SERVICE.getVisualBitmaskForSection(this.contextData);
        } else {
            this.visualsServiceFlags = 0;
        }
    }

    public boolean hasAnythingToRender() {
        return this.hasAnythingToRender;
    }

    @Deprecated
    public boolean containsSortableGeometry() {
        return !translucencySortStates.isEmpty();
    }

    public void setTranslucencySortStates(@NotNull Map<TerrainRenderPass, TranslucentQuadAnalyzer.SortState> sortStates) {
        this.translucencySortStates = Map.copyOf(sortStates);

        TranslucentQuadAnalyzer.Level level = TranslucentQuadAnalyzer.Level.NONE;
        boolean needsDynamicSorting = false;

        if (!sortStates.isEmpty()) {
            // Find highest level among all sort states
            for (TranslucentQuadAnalyzer.SortState state : sortStates.values()) {
                level = state.level().ordinal() > level.ordinal() ? state.level() : level;
                needsDynamicSorting |= state.requiresDynamicSorting();
            }
        }

        this.highestSortingLevel = level;
        this.needsDynamicTranslucencySorting = needsDynamicSorting;
    }

    public @Nullable CancellationToken getBuildCancellationToken() {
        return this.buildCancellationToken;
    }

    public void setBuildCancellationToken(@Nullable CancellationToken token) {
        this.buildCancellationToken = token;
    }

    public @Nullable ChunkUpdateType getPendingUpdate() {
        return this.pendingUpdateType;
    }

    public void setPendingUpdate(@Nullable ChunkUpdateType type) {
        this.pendingUpdateType = type;
    }

    public int getLastBuiltFrame() {
        return this.lastBuiltFrame;
    }

    public void setLastBuiltFrame(int lastBuiltFrame) {
        this.lastBuiltFrame = lastBuiltFrame;
    }

    public int getLastSubmittedFrame() {
        return this.lastSubmittedFrame;
    }

    public void setLastSubmittedFrame(int lastSubmittedFrame) {
        this.lastSubmittedFrame = lastSubmittedFrame;
    }
}
