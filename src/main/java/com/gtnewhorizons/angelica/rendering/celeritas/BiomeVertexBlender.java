package com.gtnewhorizons.angelica.rendering.celeritas;

import com.gtnewhorizon.gtnhlib.client.renderer.TessellatorManager;
import com.gtnewhorizons.angelica.config.AngelicaConfig;
import com.gtnewhorizons.angelica.proxy.ClientProxy;
import com.prupe.mcpatcher.ctm.CTMUtils;
import jss.notfine.config.MCPatcherForgeConfig;
import me.jellysquid.mods.sodium.client.gui.options.named.BiomeBlendMode;
import net.minecraft.block.BlockGrass;
import net.minecraft.client.renderer.RenderBlocks;
import net.minecraft.client.renderer.Tessellator;
import net.minecraft.init.Blocks;
import net.minecraft.util.IIcon;
import net.minecraft.world.IBlockAccess;
import org.embeddedt.embeddium.api.util.ColorMixer;

import java.nio.ByteOrder;

public final class BiomeVertexBlender {
    private static final boolean LITTLE_ENDIAN = ByteOrder.nativeOrder() == ByteOrder.LITTLE_ENDIAN;
    // Rows: -Y, +Y, -Z, +Z, -X, +X.
    // Columns: RenderBlocks top-left, bottom-left, bottom-right, top-right.
    // Corner bits: 1 = max X, 2 = max Y, 4 = max Z; unset = min.
    private static final int[][] FACE_CORNERS = {
        {4, 0, 1, 5}, {7, 3, 2, 6}, {2, 3, 1, 0},
        {6, 4, 5, 7}, {6, 2, 0, 4}, {5, 1, 3, 7}
    };
    private SmoothBiomeColorCache cache;
    private SmoothBiomeColorCache.ColorType type;
    private int blockX, blockY, blockZ;
    private boolean replacedBlockTint;
    private final int[] cornerColors = new int[8];
    private int populatedCorners;
    private float savedRtl, savedGtl, savedBtl, savedRbl, savedGbl, savedBbl;
    private float savedRbr, savedGbr, savedBbr, savedRtr, savedGtr, savedBtr;
    private boolean savedAo;
    private int savedLtl, savedLbl, savedLbr, savedLtr;
    private int savedColor, savedBrightness;

    public void setup(SmoothBiomeColorCache cache, SmoothBiomeColorCache.ColorType type, int x, int y, int z) {
        this.cache = cache;
        this.type = type;
        this.blockX = x;
        this.blockY = y;
        this.blockZ = z;
        this.replacedBlockTint = false;
        this.populatedCorners = 0;
    }

    public void clear() {
        cache = null;
        type = null;
        replacedBlockTint = false;
    }

    public boolean isGrass() {
        return type == SmoothBiomeColorCache.ColorType.GRASS;
    }

    public boolean isWater() {
        return type == SmoothBiomeColorCache.ColorType.WATER;
    }

    public boolean isActive() {
        return replacedBlockTint;
    }

    public static boolean isRendering() {
        if (SmoothBiomeColorCache.getActiveCache() == null || ClientProxy.options().quality.biomeBlendMode != BiomeBlendMode.FANCY) return false;
        final BiomeVertexBlender blender = ((BiomeBlendTessellator) TessellatorManager.get()).angelica$getBiomeBlender();
        return blender != null && blender.isActive();
    }

    public int deferBlockTint() {
        replacedBlockTint = true;
        return 0xFFFFFF;
    }

    public boolean beginFace(RenderBlocks renderer, int face, double x, double y, double z, IIcon icon) {
        if (!isActive()) return false;
        final Tessellator tessellator = TessellatorManager.get();
        if (((BiomeBlendTessellator) tessellator).angelica$getBiomeBlender() != this) return false;
        final IIcon originalIcon = AngelicaConfig.enableMCPatcherForgeFeatures && MCPatcherForgeConfig.ConnectedTextures.enabled ? CTMUtils.getOriginalIcon(icon) : icon;
        if (isGrass() && icon != Blocks.grass.getIcon(1, 0) && icon != BlockGrass.getIconSideOverlay()
            && originalIcon != Blocks.grass.getIcon(1, 0) && originalIcon != BlockGrass.getIconSideOverlay()) {
            return false;
        }

        savedRtl = renderer.colorRedTopLeft; savedGtl = renderer.colorGreenTopLeft; savedBtl = renderer.colorBlueTopLeft;
        savedRbl = renderer.colorRedBottomLeft; savedGbl = renderer.colorGreenBottomLeft; savedBbl = renderer.colorBlueBottomLeft;
        savedRbr = renderer.colorRedBottomRight; savedGbr = renderer.colorGreenBottomRight; savedBbr = renderer.colorBlueBottomRight;
        savedRtr = renderer.colorRedTopRight; savedGtr = renderer.colorGreenTopRight; savedBtr = renderer.colorBlueTopRight;
        final boolean ao = savedAo = renderer.enableAO;
        savedLtl = renderer.brightnessTopLeft; savedLbl = renderer.brightnessBottomLeft;
        savedLbr = renderer.brightnessBottomRight; savedLtr = renderer.brightnessTopRight;
        final int color = savedColor = tessellator.color;
        final int brightness = savedBrightness = tessellator.brightness;
        if (!ao) {
            final int abgr = nativeToABGR(color);
            renderer.colorRedTopLeft = renderer.colorRedBottomLeft = renderer.colorRedBottomRight = renderer.colorRedTopRight = (abgr & 255) / 255.0f;
            renderer.colorGreenTopLeft = renderer.colorGreenBottomLeft = renderer.colorGreenBottomRight = renderer.colorGreenTopRight = (abgr >> 8 & 255) / 255.0f;
            renderer.colorBlueTopLeft = renderer.colorBlueBottomLeft = renderer.colorBlueBottomRight = renderer.colorBlueTopRight = (abgr >> 16 & 255) / 255.0f;
            renderer.brightnessTopLeft = renderer.brightnessBottomLeft = renderer.brightnessBottomRight = renderer.brightnessTopRight = brightness;
            renderer.enableAO = true;
        }
        try {
            tintFace(renderer, face, x, y, z);
        } catch (Throwable t) {
            endFace(renderer);
            throw t;
        }
        return true;
    }

    public void endFace(RenderBlocks renderer) {
        renderer.colorRedTopLeft = savedRtl; renderer.colorGreenTopLeft = savedGtl; renderer.colorBlueTopLeft = savedBtl;
        renderer.colorRedBottomLeft = savedRbl; renderer.colorGreenBottomLeft = savedGbl; renderer.colorBlueBottomLeft = savedBbl;
        renderer.colorRedBottomRight = savedRbr; renderer.colorGreenBottomRight = savedGbr; renderer.colorBlueBottomRight = savedBbr;
        renderer.colorRedTopRight = savedRtr; renderer.colorGreenTopRight = savedGtr; renderer.colorBlueTopRight = savedBtr;
        renderer.enableAO = savedAo;
        if (!savedAo) {
            renderer.brightnessTopLeft = savedLtl; renderer.brightnessBottomLeft = savedLbl;
            renderer.brightnessBottomRight = savedLbr; renderer.brightnessTopRight = savedLtr;
            final Tessellator tessellator = TessellatorManager.get();
            tessellator.color = savedColor;
            tessellator.brightness = savedBrightness;
        }
    }

    public static long smoothColor(IBlockAccess access, SmoothBiomeColorCache.ColorType type, int x, int y, int z) {
        final SmoothBiomeColorCache cache = SmoothBiomeColorCache.getActiveCache();
        if (cache != null) return cache.getColor(type, x, y, z) & 0xFFFFFFFFL;
        if (access instanceof WorldClientExtension ext) return ext.celeritas$getSmoothBiomeColorCache().getColor(type, x, y, z) & 0xFFFFFFFFL;
        return -1L;
    }

    public void tintFace(RenderBlocks renderer, int face, double x, double y, double z) {
        final int[] corners = FACE_CORNERS[face];
        final int tl = faceColor(renderer, face, corners[0], x, y, z);
        final int bl = faceColor(renderer, face, corners[1], x, y, z);
        final int br = faceColor(renderer, face, corners[2], x, y, z);
        final int tr = faceColor(renderer, face, corners[3], x, y, z);
        renderer.colorRedTopLeft *= (tl >> 16 & 255) / 255.0f;
        renderer.colorGreenTopLeft *= (tl >> 8 & 255) / 255.0f;
        renderer.colorBlueTopLeft *= (tl & 255) / 255.0f;
        renderer.colorRedBottomLeft *= (bl >> 16 & 255) / 255.0f;
        renderer.colorGreenBottomLeft *= (bl >> 8 & 255) / 255.0f;
        renderer.colorBlueBottomLeft *= (bl & 255) / 255.0f;
        renderer.colorRedBottomRight *= (br >> 16 & 255) / 255.0f;
        renderer.colorGreenBottomRight *= (br >> 8 & 255) / 255.0f;
        renderer.colorBlueBottomRight *= (br & 255) / 255.0f;
        renderer.colorRedTopRight *= (tr >> 16 & 255) / 255.0f;
        renderer.colorGreenTopRight *= (tr >> 8 & 255) / 255.0f;
        renderer.colorBlueTopRight *= (tr & 255) / 255.0f;
    }

    private int faceColor(RenderBlocks renderer, int face, int corner, double x, double y, double z) {
        if (renderer.renderFromInside) corner ^= face < 4 ? 1 : 4;
        return sampleVertex(
            x + ((corner & 1) == 0 ? renderer.renderMinX : renderer.renderMaxX),
            y + ((corner & 2) == 0 ? renderer.renderMinY : renderer.renderMaxY),
            z + ((corner & 4) == 0 ? renderer.renderMinZ : renderer.renderMaxZ));
    }

    public static int nativeToABGR(int color) {
        return LITTLE_ENDIAN ? color : Integer.reverseBytes(color);
    }

    public int tint(int nativeColor, double x, double y, double z) {
        if (!replacedBlockTint) return nativeColor;
        final int rgb = sampleVertex(x, y, z);
        final int abgr = LITTLE_ENDIAN ? nativeColor : Integer.reverseBytes(nativeColor);
        final int tinted = multiply(abgr, rgb);
        return LITTLE_ENDIAN ? tinted : Integer.reverseBytes(tinted);
    }

    private int sampleVertex(double x, double y, double z) {
        final double dx = x - blockX;
        final double dy = y - blockY;
        final double dz = z - blockZ;
        if ((dx != 0 && dx != 1) || (dy != 0 && dy != 1) || (dz != 0 && dz != 1)) {
            return cache.getVertexColor(type, x, y, z);
        }
        final int corner = (int) dx | ((int) dy << 1) | ((int) dz << 2);
        final int bit = 1 << corner;
        if ((populatedCorners & bit) == 0) {
            cornerColors[corner] = cache.getVertexColor(type, x, y, z);
            populatedCorners |= bit;
        }
        return cornerColors[corner];
    }

    static int vertexColor(SmoothBiomeColorCache cache, SmoothBiomeColorCache.ColorType type, double x, double y, double z) {
        x -= 0.5;
        y -= 0.5;
        z -= 0.5;
        final int ix = (int) Math.floor(x);
        final int iy = (int) Math.floor(y);
        final int iz = (int) Math.floor(z);
        final int c00 = cache.getColor(type, ix, iy, iz);
        final int c01 = cache.getColor(type, ix, iy, iz + 1);
        final int c10 = cache.getColor(type, ix + 1, iy, iz);
        final int c11 = cache.getColor(type, ix + 1, iy, iz + 1);
        final int z0 = c00 == c01 ? c00 : ColorMixer.mix(c01, c00, (float) (z - iz));
        final int z1 = c10 == c11 ? c10 : ColorMixer.mix(c11, c10, (float) (z - iz));
        return z0 == z1 ? z0 : ColorMixer.mix(z1, z0, (float) (x - ix));
    }

    static int multiply(int abgr, int rgb) {
        final int r = (abgr & 255) * ((rgb >> 16) & 255) / 255;
        final int g = ((abgr >> 8) & 255) * ((rgb >> 8) & 255) / 255;
        final int b = ((abgr >> 16) & 255) * (rgb & 255) / 255;
        return (abgr & 0xFF000000) | (b << 16) | (g << 8) | r;
    }
}
