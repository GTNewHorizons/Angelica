package com.gtnewhorizons.angelica.textures;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.awt.Rectangle;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

import net.minecraft.client.renderer.texture.Stitcher;
import net.minecraft.client.renderer.texture.TextureAtlasSprite;
import org.junit.jupiter.api.Test;

class FastTextureStitcherTest {

    @Test
    void packsSpritesWithoutOverlap() {
        Stitcher.Holder[] holders = {
            holder("a", 32, 16),
            holder("b", 16, 32),
            holder("c", 16, 16),
            holder("d", 8, 40),
            holder("e", 24, 8)
        };
        Arrays.sort(holders);

        FastTextureStitcher.Result result = FastTextureStitcher.stitch(holders, 128, 128, true);

        assertTrue(result.width <= 128);
        assertTrue(result.height <= 128);
        assertEquals(holders.length, result.sprites.size());

        List<Rectangle> rectangles = new ArrayList<>();
        for (Stitcher.Holder holder : holders) {
            TextureAtlasSprite sprite = holder.getAtlasSprite();
            Rectangle rectangle = new Rectangle(sprite.getOriginX(), sprite.getOriginY(), holder.getWidth(), holder.getHeight());
            assertTrue(rectangle.x >= 0);
            assertTrue(rectangle.y >= 0);
            assertTrue(rectangle.x + rectangle.width <= result.width);
            assertTrue(rectangle.y + rectangle.height <= result.height);

            for (Rectangle other : rectangles) {
                assertFalse(rectangle.intersects(other), rectangle + " overlaps " + other);
            }

            rectangles.add(rectangle);
        }
    }

    private static Stitcher.Holder holder(String name, int width, int height) {
        TextureAtlasSprite sprite = new TextureAtlasSprite(name);
        sprite.setIconWidth(width);
        sprite.setIconHeight(height);
        return new Stitcher.Holder(sprite, 0);
    }
}
