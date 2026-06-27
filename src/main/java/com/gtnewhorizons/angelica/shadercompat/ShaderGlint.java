package com.gtnewhorizons.angelica.shadercompat;

import com.gtnewhorizons.angelica.glsm.GLStateManager;
import com.gtnewhorizons.angelica.glsm.hooks.GLSMHooks;
import com.gtnewhorizons.angelica.glsm.hooks.GlintColorHandler;
import it.unimi.dsi.fastutil.ints.Int2IntLinkedOpenHashMap;
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

    private static final Int2IntLinkedOpenHashMap colorToTexture = new Int2IntLinkedOpenHashMap();
    static {
        colorToTexture.defaultReturnValue(0);
    }

    private static final GlintColorHandler COLOR_HANDLER = ShaderGlint::onColorChanged;

    private static boolean injecting;
    private static boolean swapped;
    private static int depth;

    private static int prevTexture;

    private static ByteBuffer tintBuffer;

    private ShaderGlint() {}

    public static void beginGlint() {
        if (ShadowRenderer.ACTIVE || !IrisApi.getInstance().isShaderPackInUse()) return;

        // If a glint span is already active, keep the outer span's saved state and
        // let the nested render ride on its handler.
        if (depth++ > 0) return;

        swapped = false;
        injecting = false;

        // Save the texture bound before the glint section so endGlint can put it back if we swap
        prevTexture = GLStateManager.getBoundTextureForServerState(0);

        // Observe vertex-color changes for the rest of the glint section
        GLSMHooks.glintColorHandler = COLOR_HANDLER;
    }

    public static void endGlint() {
        if (depth == 0 || --depth > 0) return;
        if (GLSMHooks.glintColorHandler != COLOR_HANDLER) return;
        GLSMHooks.glintColorHandler = null;

        if (swapped) {
            GLStateManager.glBindTexture(GL11.GL_TEXTURE_2D, prevTexture);
        }
        swapped = false;
        injecting = false;
    }

    private static void onColorChanged(float red, float green, float blue, float alpha) {
        if (injecting) return;

        final int texture = getTintedTexture(red, green, blue);
        if (texture <= 0) return;

        injecting = true;
        GLStateManager.glBindTexture(GL11.GL_TEXTURE_2D, texture);
        GLStateManager.glColor4f(1.0F, 1.0F, 1.0F, alpha);
        injecting = false;
        swapped = true;
    }

    private static int getTintedTexture(float r, float g, float b) {
        ensureMaskLoaded();
        if (!maskLoaded) return -1;

        final int ri = clamp8(r);
        final int gi = clamp8(g);
        final int bi = clamp8(b);
        final int key = (ri << 16) | (gi << 8) | bi;

        final int cached = colorToTexture.getAndMoveToLast(key);
        if (cached != 0) return cached;

        if (colorToTexture.size() >= MAX_CACHE) {
            GLStateManager.glDeleteTextures(colorToTexture.remove(colorToTexture.firstIntKey()));
        }

        final int texture = uploadTinted(ri / 255.0F, gi / 255.0F, bi / 255.0F);
        colorToTexture.putAndMoveToLast(key, texture);
        return texture;
    }

    private static int uploadTinted(float r, float g, float b) {
        final ByteBuffer pixels = tintBuffer;
        pixels.clear();
        for (final int l : maskLum) {
            pixels.put((byte) Math.round(l * r));
            pixels.put((byte) Math.round(l * g));
            pixels.put((byte) Math.round(l * b));
            pixels.put((byte) 0xFF);
        }
        pixels.flip();

        final int texture = GLStateManager.glGenTextures();
        GLStateManager.glBindTexture(GL11.GL_TEXTURE_2D, texture);
        // Match the vanilla glint texture
        GLStateManager.glTexParameteri(GL11.GL_TEXTURE_2D, GL11.GL_TEXTURE_MIN_FILTER, GL11.GL_LINEAR);
        GLStateManager.glTexParameteri(GL11.GL_TEXTURE_2D, GL11.GL_TEXTURE_MAG_FILTER, GL11.GL_LINEAR);
        GLStateManager.glTexParameteri(GL11.GL_TEXTURE_2D, GL11.GL_TEXTURE_WRAP_S, GL11.GL_REPEAT);
        GLStateManager.glTexParameteri(GL11.GL_TEXTURE_2D, GL11.GL_TEXTURE_WRAP_T, GL11.GL_REPEAT);
        GLStateManager.glTexImage2D(GL11.GL_TEXTURE_2D, 0, GL11.GL_RGBA8, maskW, maskH, 0,
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
            tintBuffer = BufferUtils.createByteBuffer(maskW * maskH * 4);
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
