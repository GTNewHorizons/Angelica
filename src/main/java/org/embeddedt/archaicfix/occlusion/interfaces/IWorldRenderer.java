package org.embeddedt.archaicfix.occlusion.interfaces;

import org.embeddedt.archaicfix.occlusion.OcclusionWorker;

public interface IWorldRenderer {

    boolean arch$isInUpdateList();
    void arch$setInUpdateList(boolean b);

    OcclusionWorker.CullInfo arch$getCullInfo();

}
