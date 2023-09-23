package org.embeddedt.archaicfix.occlusion;

public interface IWorldRenderer {

    boolean arch$isInUpdateList();
    void arch$setInUpdateList(boolean b);

    OcclusionWorker.CullInfo arch$getCullInfo();

}
