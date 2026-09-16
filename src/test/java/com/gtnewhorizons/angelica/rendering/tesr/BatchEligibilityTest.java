package com.gtnewhorizons.angelica.rendering.tesr;

import com.gtnewhorizons.angelica.glsm.testutil.Reflect;
import com.gtnewhorizons.angelica.rendering.items.DroppedItemInstancer;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import net.minecraft.client.renderer.Tessellator;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static com.gtnewhorizons.angelica.rendering.tesr.BatchEligibility.DENIED;
import static com.gtnewhorizons.angelica.rendering.tesr.BatchEligibility.SAFE;
import static com.gtnewhorizons.angelica.rendering.tesr.BatchEligibility.UNKNOWN;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class BatchEligibilityTest {

    private long drawCalls;

    @BeforeEach
    void reset() {
        drawCalls = 0L;
    }

    private void draw(int count) {
        drawCalls += count;
    }

    private void unbatchedPart(int draws) {
        final long before = drawCalls;
        draw(draws);
        BatchEligibility.onPartFallback(before, drawCalls);
    }

    @Test
    void aRendererDrawingOnlyModelPartsIsPromoted() {
        assertFalse(BatchEligibility.begin(UNKNOWN, drawCalls), "unproven renderers run unbatched");
        unbatchedPart(1);
        unbatchedPart(1);
        assertEquals(SAFE, BatchEligibility.end(UNKNOWN, drawCalls));
    }

    @Test
    void aRendererMixingItsOwnDrawsIsDenied() {
        BatchEligibility.begin(UNKNOWN, drawCalls);
        unbatchedPart(1);
        draw(1);
        assertEquals(DENIED, BatchEligibility.end(UNKNOWN, drawCalls));
    }

    @Test
    void aPromotedRendererIsDemotedWhenItStartsMixing() {
        assertTrue(BatchEligibility.begin(SAFE, drawCalls), "proven renderers batch");
        BatchEligibility.onPartQueued();
        draw(1);
        assertEquals(DENIED, BatchEligibility.end(SAFE, drawCalls));
    }

    @Test
    void aBatchedPartFallingBackCountsAsMixing() {
        BatchEligibility.begin(SAFE, drawCalls);
        BatchEligibility.onPartQueued();
        unbatchedPart(1);
        assertEquals(DENIED, BatchEligibility.end(SAFE, drawCalls));
    }

    @Test
    void aDeniedRendererIsNeverPromoted() {
        assertFalse(BatchEligibility.begin(DENIED, drawCalls));
        unbatchedPart(1);
        assertEquals(DENIED, BatchEligibility.end(DENIED, drawCalls));
    }

    @Test
    void aDispatchWithoutPartsProvesNothing() {
        BatchEligibility.begin(UNKNOWN, drawCalls);
        draw(3);
        assertEquals(UNKNOWN, BatchEligibility.end(UNKNOWN, drawCalls));
    }

    @Test
    void aNestedDispatchDoesNotObserveSeparately() {
        BatchEligibility.begin(UNKNOWN, drawCalls);
        unbatchedPart(1);

        BatchEligibility.begin(UNKNOWN, drawCalls);
        draw(1);
        assertEquals(UNKNOWN, BatchEligibility.end(UNKNOWN, drawCalls), "the inner dispatch leaves the verdict to the outer one");

        assertEquals(DENIED, BatchEligibility.end(UNKNOWN, drawCalls), "the nested draw still counts against the outer renderer");
    }

    @Test
    void statePersistsNothingBetweenDispatches() {
        BatchEligibility.begin(UNKNOWN, drawCalls);
        unbatchedPart(1);
        draw(1);
        assertEquals(DENIED, BatchEligibility.end(UNKNOWN, drawCalls));

        BatchEligibility.begin(UNKNOWN, drawCalls);
        unbatchedPart(1);
        assertEquals(SAFE, BatchEligibility.end(UNKNOWN, drawCalls), "the previous dispatch's foreign draws must not leak");
    }

    @Test
    void batchingIsDisallowedOutsideADispatch() {
        BatchEligibility.begin(SAFE, drawCalls);
        BatchEligibility.end(SAFE, drawCalls);
        assertFalse(BatchEligibility.batchingAllowed());
    }

    @Test
    void bracketedDrawsWithAQueuedPartPromoteUnknownToSafe() {
        BatchEligibility.begin(UNKNOWN, drawCalls);
        BatchEligibility.onPartQueued();
        BatchEligibility.beginExpectedDraws(drawCalls);
        draw(2);
        BatchEligibility.endExpectedDraws(drawCalls);
        assertEquals(SAFE, BatchEligibility.end(UNKNOWN, drawCalls));

        BatchEligibility.begin(SAFE, drawCalls);
        BatchEligibility.onPartQueued();
        BatchEligibility.beginExpectedDraws(drawCalls);
        draw(2);
        BatchEligibility.endExpectedDraws(drawCalls);
        assertEquals(SAFE, BatchEligibility.end(SAFE, drawCalls));
    }

    @Test
    void bracketDoesNotDoubleCountAFallbackPartsOwnDraws() {
        BatchEligibility.begin(UNKNOWN, drawCalls);
        BatchEligibility.beginExpectedDraws(drawCalls);
        unbatchedPart(2);
        BatchEligibility.endExpectedDraws(drawCalls);
        draw(3);
        assertEquals(DENIED, BatchEligibility.end(UNKNOWN, drawCalls));
        final long foreign = Reflect.getStatic(BatchEligibility.class, "foreignDraws");
        assertEquals(3, foreign, "only the 3 unbracketed draws must count as foreign");
    }

    @Test
    void nestedBracketsCountOnlyTheOutermostSpan() {
        BatchEligibility.begin(UNKNOWN, drawCalls);
        BatchEligibility.onPartQueued();
        BatchEligibility.beginExpectedDraws(drawCalls);
        draw(1);
        BatchEligibility.beginExpectedDraws(drawCalls);
        draw(1);
        BatchEligibility.endExpectedDraws(drawCalls);
        draw(1);
        BatchEligibility.endExpectedDraws(drawCalls);
        draw(2);
        assertEquals(DENIED, BatchEligibility.end(UNKNOWN, drawCalls));
        final long foreign = Reflect.getStatic(BatchEligibility.class, "foreignDraws");
        assertEquals(2, foreign, "the inner bracket must not reset the outer span");
    }

    @Test
    void shadowPassQueuesTheGlintWithoutTouchingTheCacheOrFallingBack() {
        try {
            Reflect.set(ModelPartBatcher.INSTANCE, "active", true);
            Reflect.set(ModelPartBatcher.INSTANCE, "shadow", true);
            Reflect.setStatic(DroppedItemInstancer.class, "basePart", true);

            final long glintBefore = DroppedItemInstancer.statGlintInstanced();
            final long fallbackBefore = DroppedItemInstancer.statFallback();
            final boolean[] originalCalled = { false };
            final Operation<Void> original = args -> {
                originalCalled[0] = true;
                return null;
            };

            BatchEligibility.begin(SAFE, 0L);
            DroppedItemInstancer.glint(new Tessellator(), 1f, 0f, 0f, 1f, 16, 16, 0.0625f, original);
            assertEquals(SAFE, BatchEligibility.end(SAFE, 0L));

            assertFalse(originalCalled[0], "the shadow-pass early return must not fall back to the original draw");
            assertEquals(glintBefore, DroppedItemInstancer.statGlintInstanced(), "no instanced draw was queued");
            assertEquals(fallbackBefore, DroppedItemInstancer.statFallback(), "no fallback was recorded");
        } finally {
            Reflect.set(ModelPartBatcher.INSTANCE, "active", false);
            Reflect.set(ModelPartBatcher.INSTANCE, "shadow", false);
            Reflect.setStatic(DroppedItemInstancer.class, "basePart", false);
            DroppedItemInstancer.clear();
        }
    }
}
