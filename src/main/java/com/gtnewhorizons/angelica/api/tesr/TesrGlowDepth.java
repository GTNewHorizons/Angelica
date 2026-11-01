package com.gtnewhorizons.angelica.api.tesr;

import com.gtnewhorizons.angelica.rendering.tesr.GlowReplayExclusions;

/// Under Fancy translucency, block entities drawing glows (blended draws without depth writes) in the translucent
/// pass are rendered a second time. Renderers that advance state per call opt out here.
public final class TesrGlowDepth {

    private TesrGlowDepth() {}

    /// Excludes block entities of `type`, or rendered by a renderer of `type`, including subclasses.
    public static void excludeFromReplay(Class<?> type) {
        GlowReplayExclusions.exclude(type);
    }
}
