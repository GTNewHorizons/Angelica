package net.coderbot.iris.pipeline;

import com.gtnewhorizons.angelica.config.AngelicaConfig;
import com.gtnewhorizons.angelica.glsm.ffp.Instancing;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class CubeInstancingGateTest {

    private final boolean savedBatching = AngelicaConfig.enableEntityBatching;
    private final boolean savedCubes = AngelicaConfig.enableCubeInstancing;

    @AfterEach
    void restore() {
        AngelicaConfig.enableEntityBatching = savedBatching;
        AngelicaConfig.enableCubeInstancing = savedCubes;
    }

    @Test
    void cubeSupportRequiresBothFlags() {
        assertTrue(cubeSupport(true, true));
        assertFalse(cubeSupport(true, false));
        assertFalse(cubeSupport(false, true));
        assertFalse(cubeSupport(false, false));
    }

    @Test
    void otherKindsIgnoreTheCubeFlags() {
        AngelicaConfig.enableEntityBatching = false;
        AngelicaConfig.enableCubeInstancing = false;
        final boolean[] support = DeferredWorldRenderingPipeline.initialInstancingSupport();
        assertFalse(support[Instancing.NONE.ordinal()]);
        assertTrue(support[Instancing.TEMPLATE.ordinal()]);
        assertTrue(support[Instancing.PARTICLE.ordinal()]);
    }

    private static boolean cubeSupport(boolean batching, boolean cubes) {
        AngelicaConfig.enableEntityBatching = batching;
        AngelicaConfig.enableCubeInstancing = cubes;
        return DeferredWorldRenderingPipeline.initialInstancingSupport()[Instancing.CUBE.ordinal()];
    }
}
