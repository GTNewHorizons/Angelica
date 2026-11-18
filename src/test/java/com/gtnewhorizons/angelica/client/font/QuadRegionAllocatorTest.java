package com.gtnewhorizons.angelica.client.font;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import static com.gtnewhorizons.angelica.glsm.backend.RenderBackend.MAX_FRAMES_IN_FLIGHT;
import static org.junit.jupiter.api.Assertions.assertEquals;

class QuadRegionAllocatorTest {

    @Test
    void allocatesTheFirstHoleThatFits() {
        final QuadRegionAllocator allocator = new QuadRegionAllocator(16);
        assertEquals(0, allocator.allocate(4));
        assertEquals(4, allocator.allocate(4));
        assertEquals(8, allocator.allocate(4));
        allocator.retire(4, 4, 0);
        allocator.advance(MAX_FRAMES_IN_FLIGHT);
        assertEquals(4, allocator.allocate(2));
        assertEquals(12, allocator.allocate(4));
        assertEquals(6, allocator.allocate(2));
        assertEquals(-1, allocator.allocate(1));
    }

    @ParameterizedTest(name = "retire order {0}")
    @ValueSource(strings = { "012", "021", "102", "120", "201", "210" })
    void reclaimedNeighborsCoalesce(String order) {
        final QuadRegionAllocator allocator = new QuadRegionAllocator(12);
        for (int i = 0; i < 3; i++) assertEquals(4 * i, allocator.allocate(4));
        for (int i = 0; i < 3; i++) {
            allocator.retire(4 * (order.charAt(i) - '0'), 4, i);
            allocator.advance(i + MAX_FRAMES_IN_FLIGHT);
        }
        assertEquals(0, allocator.allocate(12));
    }

    @Test
    void retiredRegionsWaitForTheFramesInFlight() {
        final QuadRegionAllocator allocator = new QuadRegionAllocator(4);
        assertEquals(0, allocator.allocate(4));
        allocator.retire(0, 4, 10);
        for (int frame = 10; frame < 10 + MAX_FRAMES_IN_FLIGHT; frame++) {
            allocator.advance(frame);
            assertEquals(-1, allocator.allocate(4), "frame " + frame);
        }
        allocator.advance(10 + MAX_FRAMES_IN_FLIGHT);
        assertEquals(0, allocator.allocate(4));
    }

    @Test
    void aFullAllocatorRefuses() {
        final QuadRegionAllocator allocator = new QuadRegionAllocator(8);
        assertEquals(-1, allocator.allocate(9));
        assertEquals(0, allocator.allocate(8));
        assertEquals(-1, allocator.allocate(1));
    }

    @Test
    void resetRestoresTheWholeCapacityAndDropsRetiredRegions() {
        final QuadRegionAllocator allocator = new QuadRegionAllocator(8);
        assertEquals(0, allocator.allocate(4));
        allocator.retire(0, 4, 0);
        allocator.reset(32);
        assertEquals(0, allocator.allocate(32));
        allocator.advance(MAX_FRAMES_IN_FLIGHT);
        assertEquals(-1, allocator.allocate(1));
    }
}
