package com.gtnewhorizons.angelica.rendering.tesr;

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
}
