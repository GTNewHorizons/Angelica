package com.gtnewhorizons.angelica.rendering.celeritas;

import org.embeddedt.embeddium.impl.render.viewport.Viewport;
import org.joml.FrustumIntersection;

/**
 * Pairs the main render pass's {@link Viewport} with the JOML {@link FrustumIntersection} it was
 * built from, so the tile entity render path can perform inside/intersect containment queries that
 * the boolean {@code Frustum} interface hides.
 *
 * <p>Recorded when the vanilla {@code Frustrum} creates its viewport. Iris shadow frustums build
 * their viewports through their own {@code ViewportProvider} implementations and are never
 * recorded here; the identity check in {@link #getFor} therefore returns null for them, which
 * makes callers fall back to plain per-box visibility tests.
 */
public final class MainPassFrustum {
    private static Viewport viewport;
    private static FrustumIntersection frustum;

    private MainPassFrustum() {}

    public static void record(Viewport newViewport, FrustumIntersection newFrustum) {
        viewport = newViewport;
        frustum = newFrustum;
    }

    /** The frustum backing this exact viewport, or null if it isn't the recorded main-pass one. */
    public static FrustumIntersection getFor(Viewport candidate) {
        return (candidate != null && candidate == viewport) ? frustum : null;
    }

    public static void clear() {
        viewport = null;
        frustum = null;
    }
}
