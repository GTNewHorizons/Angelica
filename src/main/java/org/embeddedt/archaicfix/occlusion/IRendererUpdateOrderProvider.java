package org.embeddedt.archaicfix.occlusion;

import net.minecraft.client.renderer.WorldRenderer;

import java.util.List;

/** Provides a traversal order of the elements of RenderGlobal#worldRenderersToUpdate. Ideally, the order should be from
 *  the closest renderer to the farthest. */
public interface IRendererUpdateOrderProvider {
    
    /** Prepare providing a batch of renderers. */
    void prepare(List<WorldRenderer> worldRenderersToUpdateList);
    
    boolean hasNext(List<WorldRenderer> worldRenderersToUpdateList);
    
    WorldRenderer next(List<WorldRenderer> worldRenderersToUpdateList);

    /** End the batch. Remove the renderers that were provided during the batch from worldRenderersToUpdate */
    void cleanup(List<WorldRenderer> worldRenderersToUpdateList);

}
