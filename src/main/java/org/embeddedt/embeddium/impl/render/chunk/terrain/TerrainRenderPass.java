package org.embeddedt.embeddium.impl.render.chunk.terrain;

import lombok.Builder;
import lombok.EqualsAndHashCode;
import lombok.Getter;
import lombok.experimental.Accessors;
import org.embeddedt.embeddium.impl.render.chunk.terrain.material.Material;

/**
 * A terrain render pass corresponds to a draw call to render some subset of terrain geometry. Passes are generally
 * used for fixed configuration that will not change from quad to quad and allow for optimizations to be made
 * within the terrain shader code at compile time (e.g. omitting the fragment discard conditional entirely on the solid pass).
 * <p></p>
 * Geometry that shares the same terrain render pass may still be able to specify some more dynamic properties. See {@link Material}
 * for more information.
 */
@Accessors(fluent = true)
@EqualsAndHashCode
public class TerrainRenderPass {
    /**
     * The friendly name of this render pass.
     */
    @Getter
    @EqualsAndHashCode.Exclude
    private final String name;

    /**
     * A callback used to set up/clear GPU pipeline state.
     */
    private final PipelineState pipelineState;

    /**
     * Whether sections on this render pass should be rendered farthest-to-nearest, rather than nearest-to-farthest.
     */
    private final boolean useReverseOrder;
    /**
     * Whether fragment alpha testing should be enabled for this render pass.
     */
    private final boolean fragmentDiscard;
    /**
     * Whether this render pass wants to opt in to translucency sorting if enabled.
     */
    private final boolean useTranslucencySorting;

    @Builder
    public TerrainRenderPass(String name, PipelineState pipelineState, boolean useReverseOrder, boolean fragmentDiscard, boolean useTranslucencySorting) {
        if(name == null || name.length() == 0) {
            throw new IllegalArgumentException("Name not specified for terrain pass");
        }
        this.name = name;
        this.pipelineState = pipelineState;
        this.useReverseOrder = useReverseOrder;
        this.fragmentDiscard = fragmentDiscard;
        this.useTranslucencySorting = useTranslucencySorting;
    }

    public boolean isReverseOrder() {
        return this.useReverseOrder;
    }

    public boolean isSorted() {
        return this.useTranslucencySorting;
    }

    public void startDrawing() {
        this.pipelineState.setup();
    }

    public void endDrawing() {
        this.pipelineState.clear();
    }

    public boolean supportsFragmentDiscard() {
        return this.fragmentDiscard;
    }

    @Override
    public String toString() {
        return "TerrainRenderPass[name=" + this.name + "]";
    }

    public interface PipelineState {
        PipelineState DEFAULT = new PipelineState() {
            @Override
            public void setup() {

            }

            @Override
            public void clear() {

            }
        };

        void setup();
        void clear();
    }
}
