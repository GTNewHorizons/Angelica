package org.embeddedt.embeddium.impl.render.chunk.multidraw;

import com.gtnewhorizons.angelica.rendering.RenderRegionKeys;
import org.embeddedt.embeddium.impl.model.quad.properties.ModelQuadFacing;
import org.embeddedt.embeddium.impl.render.chunk.region.RenderRegion;
import org.embeddedt.embeddium.impl.render.viewport.CameraTransform;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class RegionCullMaskTest {
    @Test
    void agreeingCornersImplyAgreementThroughoutTheRegion() {
        for (int cameraX = -40; cameraX <= 40; cameraX += 7) {
            for (int cameraY = -40; cameraY <= 40; cameraY += 11) {
                for (int cameraZ = -40; cameraZ <= 40; cameraZ += 7) {
                    assertCornerAgreementHolds(cameraX, cameraY, cameraZ, 0, 0, 0);
                    assertCornerAgreementHolds(cameraX, cameraY, cameraZ, -8, -4, -8);
                }
            }
        }
    }

    private static void assertCornerAgreementHolds(int cameraX, int cameraY, int cameraZ, int minX, int minY, int minZ) {
        int maxX = minX + RenderRegion.REGION_WIDTH - 1;
        int maxY = minY + RenderRegion.REGION_HEIGHT - 1;
        int maxZ = minZ + RenderRegion.REGION_LENGTH - 1;

        int atMin = BatchAssembler.getVisibleFaces(cameraX, cameraY, cameraZ, minX, minY, minZ);
        int atMax = BatchAssembler.getVisibleFaces(cameraX, cameraY, cameraZ, maxX, maxY, maxZ);

        if (atMin != atMax) {
            return;
        }

        for (int x = minX; x <= maxX; x++) {
            for (int y = minY; y <= maxY; y++) {
                for (int z = minZ; z <= maxZ; z++) {
                    assertEquals(atMin, BatchAssembler.getVisibleFaces(cameraX, cameraY, cameraZ, x, y, z),
                            "corners agreed on " + Integer.toBinaryString(atMin) + " but section (" + x + ", " + y + ", " + z
                                    + ") disagreed for camera (" + cameraX + ", " + cameraY + ", " + cameraZ + ")");
                }
            }
        }
    }

    @Test
    void unassignedIsAlwaysVisibleSoPerSectionRunEmissionAlwaysTerminates() {
        for (int camera = -64; camera <= 64; camera += 3) {
            int mask = BatchAssembler.getVisibleFaces(camera, camera, camera, 0, 0, 0);

            assertTrue((mask & (1 << ModelQuadFacing.UNASSIGNED.ordinal())) != 0,
                    "UNASSIGNED must always be set; the per-section run loop relies on a non-zero mask");
        }
    }

    @Test
    void uniformCullMaskEqualsEverySectionMaskWhenTheCameraIsOutsideTheRegion() {
        final RenderRegion region = RenderRegionKeys.create(0, 0, 0, 0);

        assertUniformMaskCoversTheRegion(region, new CameraTransform(-1000.0, -1000.0, -1000.0));
        assertUniformMaskCoversTheRegion(region, new CameraTransform(1000.0, 1000.0, 1000.0));
    }

    @Test
    void uniformCullMaskReportsNonUniformWhenTheCameraIsInsideTheRegion() {
        final RenderRegion region = RenderRegionKeys.create(0, 0, 0, 0);
        final CameraTransform camera = new CameraTransform(
                region.getCenterX(), region.getCenterY(), region.getCenterZ());

        assertEquals(BatchAssembler.MASK_NOT_UNIFORM, BatchAssembler.uniformCullMask(region, camera));
    }

    private static void assertUniformMaskCoversTheRegion(RenderRegion region, CameraTransform camera) {
        final int mask = BatchAssembler.uniformCullMask(region, camera);

        assertNotEquals(BatchAssembler.MASK_NOT_UNIFORM, mask,
                "camera (" + camera.intX + ", " + camera.intY + ", " + camera.intZ + ") is outside the region");

        final int minX = region.getChunkX(), minY = region.getChunkY(), minZ = region.getChunkZ();

        for (int x = minX; x < minX + RenderRegion.REGION_WIDTH; x++) {
            for (int y = minY; y < minY + RenderRegion.REGION_HEIGHT; y++) {
                for (int z = minZ; z < minZ + RenderRegion.REGION_LENGTH; z++) {
                    assertEquals(mask, BatchAssembler.getVisibleFaces(camera.intX, camera.intY, camera.intZ, x, y, z),
                            "section (" + x + ", " + y + ", " + z + ")");
                }
            }
        }
    }
}
