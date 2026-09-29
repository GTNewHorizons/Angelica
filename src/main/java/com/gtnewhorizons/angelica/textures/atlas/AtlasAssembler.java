package com.gtnewhorizons.angelica.textures.atlas;

import com.gtnewhorizon.gtnhlib.bytebuf.MemoryUtilities;
import com.gtnewhorizons.angelica.config.AngelicaConfig;
import com.gtnewhorizons.angelica.glsm.GLStateManager;
import com.gtnewhorizons.angelica.glsm.texture.TextureStaging;
import com.gtnewhorizons.angelica.glsm.threading.AngelicaWorkers;
import com.gtnewhorizons.angelica.rendering.celeritas.SpriteExtension;
import com.gtnewhorizons.angelica.utils.SpritePadding;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.texture.TextureAtlasSprite;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.Comparator;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Set;
import java.util.concurrent.CompletableFuture;

public final class AtlasAssembler {

    private static final Logger LOGGER = LogManager.getLogger("Angelica");
    private static final long BAND_BYTES = 4L << 20;

    private AtlasAssembler() {}

    private record Placement(TextureAtlasSprite sprite, int[][] frame, int width, int height, int gutter, int x, int y, int paddedWidth, int paddedHeight) {}

    private static final class Band {
        final int y0;
        final int y1;
        final int width;
        final TextureStaging[] stagings;
        CompletableFuture<Void> work;

        Band(int y0, int y1, int width, int levels) {
            this.y0 = y0;
            this.y1 = y1;
            this.width = width;
            this.stagings = new TextureStaging[levels];
        }
    }

    public static void upload(List<TextureAtlasSprite> sprites, int mipmapLevels, int atlasWidth, int atlasHeight) {
        for (TextureAtlasSprite sprite : sprites) {
            ((SpriteExtension) sprite).angelica$setUploaded(false);
        }
        if (!AngelicaConfig.enableBatchedAtlasUpload || sprites.isEmpty() || atlasWidth <= 0 || atlasHeight <= 0) return;
        if (Minecraft.getMinecraft().gameSettings.anaglyph) return;

        final Placement[] placements = collect(sprites, mipmapLevels, atlasWidth, atlasHeight);
        if (placements == null || placements.length == 0) return;

        final int align = 1 << mipmapLevels;
        final int levels = mipmapLevels + 1;
        final int bandRows = Math.max(align, (int) Math.min(atlasHeight, BAND_BYTES / (atlasWidth * 4L)) / align * align);
        final int bandCount = (atlasHeight + bandRows - 1) / bandRows;
        final boolean[] bandOk = new boolean[bandCount];
        int maxPaddedHeight = 0;
        for (Placement p : placements) maxPaddedHeight = Math.max(maxPaddedHeight, p.paddedHeight);

        Band previous = null;
        Band current = null;
        try {
            for (int b = 0; b < bandCount; b++) {
                final int y0 = b * bandRows;
                current = open(y0, Math.min(atlasHeight, y0 + bandRows), levels, atlasWidth);
                if (current != null) submit(current, placements, maxPaddedHeight, align);
                if (previous != null) {
                    final Band done = previous;
                    previous = null;
                    bandOk[done.y0 / bandRows] = finish(done);
                }
                previous = current;
                current = null;
                if (previous == null) break;
            }
            if (previous != null) {
                final Band done = previous;
                previous = null;
                bandOk[done.y0 / bandRows] = finish(done);
            }
        } catch (Throwable t) {
            LOGGER.warn("Batched atlas upload failed, remaining sprites use the per-sprite path", t);
            discard(previous);
            discard(current);
        } finally {
            GLStateManager.trimTextureStaging();
        }

        final TextureAtlasSprite last = sprites.getLast();
        for (Placement p : placements) {
            if (p.sprite != last && covered(bandOk, bandRows, p.y, p.y + p.paddedHeight)) {
                ((SpriteExtension) p.sprite).angelica$setUploaded(true);
            }
        }
    }

    private static Placement[] collect(List<TextureAtlasSprite> sprites, int mipmapLevels, int atlasWidth, int atlasHeight) {
        final int alignMask = (1 << mipmapLevels) - 1;
        final Set<TextureAtlasSprite> seen = Collections.newSetFromMap(new IdentityHashMap<>(sprites.size() * 2));
        final ArrayList<Placement> all = new ArrayList<>(sprites.size());
        final ArrayList<Placement> eligible = new ArrayList<>(sprites.size());
        boolean anyNonSquare = false;
        int maxPaddedHeight = 0;

        for (TextureAtlasSprite sprite : sprites) {
            if (!seen.add(sprite)) continue;
            final int w = sprite.getIconWidth();
            final int h = sprite.getIconHeight();
            final int g = Math.max(0, ((SpriteExtension) sprite).angelica$getGutterWidth());
            final int x = sprite.getOriginX() - g;
            final int y = sprite.getOriginY() - g;
            final int pw = w + 2 * g;
            final int ph = h + 2 * g;
            if (w <= 0 || h <= 0 || x < 0 || y < 0 || (x & alignMask) != 0 || (y & alignMask) != 0 || (long) x + pw > atlasWidth || (long) y + ph > atlasHeight) {
                return null;
            }
            final Placement p = new Placement(sprite, eligibleFrame(sprite, w, h, g, mipmapLevels), w, h, g, x, y, pw, ph);
            all.add(p);
            if (p.frame != null) eligible.add(p);
            anyNonSquare |= w != h;
            maxPaddedHeight = Math.max(maxPaddedHeight, ph);
        }

        if (anyNonSquare && overlaps(all, maxPaddedHeight)) return null;
        final Placement[] result = eligible.toArray(Placement[]::new);
        Arrays.sort(result, Comparator.comparingInt(Placement::y));
        return result;
    }

    private static int[][] eligibleFrame(TextureAtlasSprite sprite, int w, int h, int g, int mipmapLevels) {
        if (sprite.getFrameCount() <= 0) return null;
        final int[][] frame = sprite.getFrameTextureData(0);
        if (frame == null || frame.length != mipmapLevels + 1) return null;
        for (int l = 0; l <= mipmapLevels; l++) {
            final int lw = w >> l;
            final int lh = h >> l;
            final int lg = SpritePadding.gutterForLevel(g, l);
            if (lw <= 0 || lh <= 0 || frame[l] == null || frame[l].length < lw * lh) return null;
            if (((w + 2 * g) >> l) != lw + 2 * lg || ((h + 2 * g) >> l) != lh + 2 * lg) return null;
        }
        return frame;
    }

    private static boolean overlaps(ArrayList<Placement> all, int maxPaddedHeight) {
        final Placement[] sorted = all.toArray(Placement[]::new);
        Arrays.sort(sorted, Comparator.comparingInt(Placement::y));
        for (final Placement s : sorted) {
            if (s.width == s.height) continue;
            for (int j = lowerBound(sorted, s.y - maxPaddedHeight + 1); j < sorted.length && sorted[j].y < s.y + s.paddedHeight; j++) {
                final Placement o = sorted[j];
                if (o != s && o.y + o.paddedHeight > s.y && o.x < s.x + s.paddedWidth && o.x + o.paddedWidth > s.x) {
                    return true;
                }
            }
        }
        return false;
    }

    private static Band open(int y0, int y1, int levels, int atlasWidth) {
        final Band band = new Band(y0, y1, atlasWidth, levels);
        try {
            for (int l = 0; l < levels; l++) {
                final int ly0 = y0 >> l;
                final int rows = (y1 >> l) - ly0;
                final int lw = atlasWidth >> l;
                if (rows <= 0 || lw <= 0) continue;
                final TextureStaging staging = GLStateManager.beginTextureStaging(l, 0, ly0, lw, rows);
                if (staging == null) {
                    abandon(band, 0);
                    return null;
                }
                band.stagings[l] = staging;
            }
        } catch (Throwable t) {
            abandon(band, 0);
            throw t;
        }
        return band;
    }

    private static void submit(Band band, Placement[] placements, int maxPaddedHeight, int align) {
        final int threads = AngelicaWorkers.threads();
        final int perStripe = (band.y1 - band.y0 + threads - 1) / threads;
        final int stripeRows = Math.max(align, (perStripe + align - 1) / align * align);
        final ArrayList<CompletableFuture<Void>> stripes = new ArrayList<>(threads);
        try {
            for (int sy0 = band.y0; sy0 < band.y1; sy0 += stripeRows) {
                final int stripeY0 = sy0;
                final int stripeY1 = Math.min(band.y1, sy0 + stripeRows);
                final int lo = lowerBound(placements, stripeY0 - maxPaddedHeight + 1);
                final int hi = lowerBound(placements, stripeY1);
                stripes.add(AngelicaWorkers.run(() -> fillStripe(band, placements, lo, hi, stripeY0, stripeY1)));
            }
        } finally {
            band.work = CompletableFuture.allOf(stripes.toArray(CompletableFuture[]::new));
        }
    }

    private static void fillStripe(Band band, Placement[] placements, int lo, int hi, int stripeY0, int stripeY1) {
        for (int l = 0; l < band.stagings.length; l++) {
            final TextureStaging staging = band.stagings[l];
            if (staging == null) continue;
            final int stride = (band.width >> l) * 4;
            final int levelY0 = band.y0 >> l;
            final long address = MemoryUtilities.memAddress(staging.buffer());
            final int clipY0 = (stripeY0 >> l) - levelY0;
            final int clipY1 = (stripeY1 >> l) - levelY0;
            if (clipY1 <= clipY0) continue;
            MemoryUtilities.memSet(address + (long) clipY0 * stride, 0, (long) (clipY1 - clipY0) * stride);
            final boolean swap = !staging.bgra();
            for (int i = lo; i < hi; i++) {
                final Placement p = placements[i];
                if (p.y + p.paddedHeight <= stripeY0) continue;
                SpritePadding.writePaddedLevel(p.frame[l], p.width >> l, p.height >> l, SpritePadding.gutterForLevel(p.gutter, l),
                    address, stride, p.x >> l, (p.y >> l) - levelY0, clipY0, clipY1, swap);
            }
        }
    }

    private static boolean finish(Band band) {
        try {
            band.work.join();
        } catch (Throwable t) {
            LOGGER.warn("Batched atlas upload failed for rows {}-{}, those sprites use the per-sprite path", band.y0, band.y1, t);
            abandon(band, 0);
            return false;
        }
        for (int l = 0; l < band.stagings.length; l++) {
            final TextureStaging staging = band.stagings[l];
            if (staging == null) continue;
            band.stagings[l] = null;
            boolean committed = false;
            try {
                committed = GLStateManager.commitTextureStaging(staging);
            } finally {
                if (!committed) abandon(band, l + 1);
            }
            if (!committed) {
                LOGGER.warn("Batched atlas upload could not commit level {} for rows {}-{}, those sprites use the per-sprite path", l, band.y0, band.y1);
                return false;
            }
        }
        return true;
    }

    private static void discard(Band band) {
        if (band == null) return;
        if (band.work != null) band.work.handle((v, e) -> null).join();
        abandon(band, 0);
    }

    private static void abandon(Band band, int fromLevel) {
        for (int l = fromLevel; l < band.stagings.length; l++) {
            final TextureStaging staging = band.stagings[l];
            if (staging == null) continue;
            band.stagings[l] = null;
            GLStateManager.abandonTextureStaging(staging);
        }
    }

    private static boolean covered(boolean[] bandOk, int bandRows, int y0, int y1) {
        for (int b = y0 / bandRows, last = (y1 - 1) / bandRows; b <= last; b++) {
            if (!bandOk[b]) return false;
        }
        return true;
    }

    private static int lowerBound(Placement[] placements, int y) {
        int lo = 0;
        int hi = placements.length;
        while (lo < hi) {
            final int mid = (lo + hi) >>> 1;
            if (placements[mid].y < y) lo = mid + 1;
            else hi = mid;
        }
        return lo;
    }
}
