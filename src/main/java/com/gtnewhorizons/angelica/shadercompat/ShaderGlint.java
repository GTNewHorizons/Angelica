package com.gtnewhorizons.angelica.shadercompat;

import com.gtnewhorizons.angelica.glsm.GLStateManager;
import com.gtnewhorizons.angelica.glsm.states.Color4;
import it.unimi.dsi.fastutil.ints.Int2IntMap;
import it.unimi.dsi.fastutil.ints.Int2IntOpenHashMap;
import net.coderbot.iris.pipeline.ShadowRenderer;
import net.irisshaders.iris.api.v0.IrisApi;
import net.minecraft.client.Minecraft;
import net.minecraft.util.ResourceLocation;
import org.lwjgl.BufferUtils;
import org.lwjgl.opengl.GL11;

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.InputStream;
import java.nio.ByteBuffer;

/**
 * Maybe overcomplicated, but it's required to bridge the gap between the modern glint texture and white texture 1.7.10
 * has. Some shaders are able to render the glint just fine as they seem to have some amount of support for older
 * Minecraft, but others like AstraLex do *not* render correctly. It expects the color to be within the texture.
 * This might introduce some oddities that I'm not seeing, but for now this appears to fix the issue.
 */
public final class ShaderGlint {

    private static final ResourceLocation GLINT_MASK = new ResourceLocation("textures/misc/enchanted_item_glint.png");
    private static final int MAX_CACHE = 64;

    private static boolean maskLoaded;
    private static boolean maskFailed;
    private static int maskW;
    private static int maskH;
    private static int[] maskLum;

    private static final Int2IntMap colorToTexture = new Int2IntOpenHashMap();
    static {
        colorToTexture.defaultReturnValue(0);
    }

    private static boolean glintActive;
    private static boolean captured;
    private static float capR, capG, capB, capA;

    private ShaderGlint() {}

    public static void beginGlint() {
        glintActive = true;
        captured = false;
    }

    public static void endGlint() {
        glintActive = false;
        captured = false;
    }

    public static void onGlintDraw() {
        if (!glintActive || ShadowRenderer.ACTIVE) return;
        if (!IrisApi.getInstance().isShaderPackInUse()) return;

        if (!captured) {
            final Color4 color = GLStateManager.getColor();
            capR = color.getRed();
            capG = color.getGreen();
            capB = color.getBlue();
            capA = color.getAlpha();
            captured = true;
        }

        final int texture = getTintedTexture(capR, capG, capB);
        if (texture <= 0) return;

        GLStateManager.glBindTexture(GL11.GL_TEXTURE_2D, texture);
        GLStateManager.glColor4f(1.0F, 1.0F, 1.0F, capA);
    }

    private static int getTintedTexture(float r, float g, float b) {
        ensureMaskLoaded();
        if (!maskLoaded) return -1;

        final int ri = clamp8(r);
        final int gi = clamp8(g);
        final int bi = clamp8(b);
        final int key = (ri << 16) | (gi << 8) | bi;

        final int cached = colorToTexture.get(key);
        if (cached != 0) return cached;

        if (colorToTexture.size() >= MAX_CACHE) {
            for (final int id : colorToTexture.values()) {
                GL11.glDeleteTextures(id);
            }
            colorToTexture.clear();
        }

        final int texture = uploadTinted(ri / 255.0F, gi / 255.0F, bi / 255.0F);
        colorToTexture.put(key, texture);
        return texture;
    }

    private static int uploadTinted(float r, float g, float b) {
        final ByteBuffer pixels = BufferUtils.createByteBuffer(maskW * maskH * 4);
        for (final int l : maskLum) {
            pixels.put((byte) Math.round(l * r));
            pixels.put((byte) Math.round(l * g));
            pixels.put((byte) Math.round(l * b));
            pixels.put((byte) 0xFF);
        }
        pixels.flip();

        final int texture = GL11.glGenTextures();
        GLStateManager.glBindTexture(GL11.GL_TEXTURE_2D, texture);
        // Match the vanilla glint texture
        GL11.glTexParameteri(GL11.GL_TEXTURE_2D, GL11.GL_TEXTURE_MIN_FILTER, GL11.GL_LINEAR);
        GL11.glTexParameteri(GL11.GL_TEXTURE_2D, GL11.GL_TEXTURE_MAG_FILTER, GL11.GL_LINEAR);
        GL11.glTexParameteri(GL11.GL_TEXTURE_2D, GL11.GL_TEXTURE_WRAP_S, GL11.GL_REPEAT);
        GL11.glTexParameteri(GL11.GL_TEXTURE_2D, GL11.GL_TEXTURE_WRAP_T, GL11.GL_REPEAT);
        GL11.glTexImage2D(GL11.GL_TEXTURE_2D, 0, GL11.GL_RGBA8, maskW, maskH, 0,
            GL11.GL_RGBA, GL11.GL_UNSIGNED_BYTE, pixels);
        return texture;
    }

    private static void ensureMaskLoaded() {
        if (maskLoaded || maskFailed) return;
        try (final InputStream in = Minecraft.getMinecraft().getResourceManager().getResource(GLINT_MASK).getInputStream()) {
            final BufferedImage img = ImageIO.read(in);
            maskW = img.getWidth();
            maskH = img.getHeight();
            maskLum = new int[maskW * maskH];
            final int[] argb = img.getRGB(0, 0, maskW, maskH, null, 0, maskW);
            for (int i = 0; i < argb.length; i++) {
                final int p = argb[i];
                final int rr = (p >> 16) & 0xFF;
                final int gg = (p >> 8) & 0xFF;
                final int bb = p & 0xFF;
                maskLum[i] = (rr + gg + bb) / 3;
            }
            maskLoaded = true;
        } catch (final Exception e) {
            maskFailed = true;
        }
    }

    private static int clamp8(float v) {
        final int i = Math.round(v * 255.0F);
        if (i < 0) return 0;
        return Math.min(i, 255);
    }
}
