package com.gtnewhorizons.angelica.rendering;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class OverridesMethodTest {

    static class Base {
        public double dist(double x, double y, double z) { return 0; }
        public boolean pass(int p) { return p == 0; }
    }

    static class Plain extends Base {}

    static class Direct extends Base {
        @Override public double dist(double x, double y, double z) { return 1; }
    }

    static class Inherited extends Direct {}

    interface DefaultDist {
        default double dist(double x, double y, double z) { return 2; }
    }

    static class WithInterfaceDefault extends Base implements DefaultDist {}

    private static final String[] DIST = { "dist" };
    private static final String[] PASS = { "pass" };
    private static final Class<?>[] DIST_PARAMS = { double.class, double.class, double.class };

    @Test
    void baseDeclarationIsNotAnOverride() {
        assertFalse(TileEntityRenderBoundsRegistry.overridesMethod(Base.class, Base.class, DIST, DIST_PARAMS));
        assertFalse(TileEntityRenderBoundsRegistry.overridesMethod(Plain.class, Base.class, DIST, DIST_PARAMS));
        assertFalse(TileEntityRenderBoundsRegistry.overridesMethod(Plain.class, Base.class, PASS, int.class));
    }

    @Test
    void directOverrideDetected() {
        assertTrue(TileEntityRenderBoundsRegistry.overridesMethod(Direct.class, Base.class, DIST, DIST_PARAMS));
    }

    @Test
    void inheritedOverrideDetected() {
        assertTrue(TileEntityRenderBoundsRegistry.overridesMethod(Inherited.class, Base.class, DIST, DIST_PARAMS));
    }

    @Test
    void classMethodBeatsInterfaceDefault() {
        assertFalse(TileEntityRenderBoundsRegistry.overridesMethod(WithInterfaceDefault.class, Base.class, DIST, DIST_PARAMS));
    }

    @Test
    void unresolvableNameTreatedAsOverriding() {
        assertTrue(TileEntityRenderBoundsRegistry.overridesMethod(Plain.class, Base.class, new String[] { "func_145835_a" }, DIST_PARAMS));
    }

    @Test
    void wrongParametersTreatedAsOverriding() {
        assertTrue(TileEntityRenderBoundsRegistry.overridesMethod(Plain.class, Base.class, DIST, double.class));
    }

    @Test
    void firstMatchingCandidateNameWins() {
        assertTrue(TileEntityRenderBoundsRegistry.overridesMethod(Direct.class, Base.class, new String[] { "func_145835_a", "dist" }, DIST_PARAMS));
        assertFalse(TileEntityRenderBoundsRegistry.overridesMethod(Plain.class, Base.class, new String[] { "func_145835_a", "dist" }, DIST_PARAMS));
    }
}
