package com.gtnewhorizons.angelica.sdlgpu.resource;

import org.junit.jupiter.api.Test;
import org.lwjgl.opengl.GL11;
import org.lwjgl.opengl.GL12;

import static org.junit.jupiter.api.Assertions.assertEquals;

class CopyAllMipsVolumeTest {

    private record Copy(int level, int layer, int w, int h, int d) {}

    private static java.util.List<Copy> plan(int glTarget, int width, int height, int depth, int levels) {
        final java.util.List<Copy> out = new java.util.ArrayList<>();
        final boolean volume = glTarget == GL12.GL_TEXTURE_3D;
        final int layers = volume ? 1 : Math.max(1, depth);
        for (int level = 0; level < Math.max(1, levels); level++) {
            final int w = Math.max(1, width >> level);
            final int h = Math.max(1, height >> level);
            final int d = volume ? Math.max(1, depth >> level) : 1;
            for (int layer = 0; layer < layers; layer++) out.add(new Copy(level, layer, w, h, d));
        }
        return out;
    }

    @Test
    void volumeTextureIsCopiedAsOneRegionPerLevel() {
        final var copies = plan(GL12.GL_TEXTURE_3D, 512, 256, 512, 1);
        assertEquals(1, copies.size(), "a 3D texture is one copy per mip, not one per depth slice");
        assertEquals(0, copies.get(0).layer(), "slice must stay 0; Metal rejects anything else on a 3D texture");
        assertEquals(512, copies.get(0).d(), "the whole depth must be copied");
    }

    @Test
    void arrayTextureStillCopiesPerLayer() {
        final var copies = plan(GL11.GL_TEXTURE_2D, 64, 64, 6, 1);
        assertEquals(6, copies.size(), "non-volume targets keep per-layer copies");
        for (Copy c : copies) assertEquals(1, c.d(), "layered copies stay one slice deep");
    }

    @Test
    void volumeDepthShrinksWithMipLevel() {
        final var copies = plan(GL12.GL_TEXTURE_3D, 512, 256, 512, 3);
        assertEquals(3, copies.size());
        assertEquals(512, copies.get(0).d());
        assertEquals(256, copies.get(1).d());
        assertEquals(128, copies.get(2).d());
    }
}
