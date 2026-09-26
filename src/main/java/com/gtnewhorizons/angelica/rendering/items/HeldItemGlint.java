package com.gtnewhorizons.angelica.rendering.items;

import com.gtnewhorizons.angelica.config.AngelicaConfig;
import com.gtnewhorizons.angelica.glsm.GLStateManager;
import com.gtnewhorizons.angelica.glsm.ffp.CombinedGlint;
import com.gtnewhorizons.angelica.rendering.GlintClock;
import com.gtnewhorizons.angelica.rendering.tesr.ModelPartBatcher;
import jss.notfine.core.Settings;
import net.minecraft.client.Minecraft;
import net.minecraft.client.resources.IReloadableResourceManager;
import net.minecraft.item.ItemStack;
import net.minecraft.util.ResourceLocation;
import org.joml.Matrix4f;
import org.lwjgl.opengl.GL11;

import javax.imageio.ImageIO;
import java.io.IOException;

/** One draw for the two vanilla held-item layers; modified glint sequences retain their original draws. */
public final class HeldItemGlint {
    private static final ResourceLocation TEXTURE = new ResourceLocation("textures/misc/enchanted_item_glint.png");
    private static final Matrix4f first = new Matrix4f();
    private static final Matrix4f second = new Matrix4f();
    private static long matrixTime = Long.MIN_VALUE;
    private static boolean listening, checked, opaque;

    private HeldItemGlint() {}

    private static boolean batchedGlintGuarded() {
        return ModelPartBatcher.INSTANCE.entityPassActive() && ModelPartBatcher.INSTANCE.canCaptureGlint() && canResetStencil();
    }

    public static boolean needsImmediateBase(ItemStack stack, int pass) {
        return stack.hasEffect(pass) && (boolean) Settings.MODE_GLINT_WORLD.option.getStore()
            && !ModelPartBatcher.INSTANCE.isShadowPass() && !batchedGlintGuarded();
    }

    public static boolean layersCoverSamePixels() {
        return GlintCompatibility.held() && opaqueTexture(Minecraft.getMinecraft());
    }

    public static boolean canResetStencil() {
        if (!GLStateManager.getStencilTest().isEnabled()) return true;
        final var stencil = GLStateManager.getStencilState();
        return stencil.getFuncFront() == GL11.GL_ALWAYS && stencil.getFuncBack() == GL11.GL_ALWAYS
            && stencil.getFailOpFront() == GL11.GL_KEEP && stencil.getFailOpBack() == GL11.GL_KEEP
            && stencil.getZFailOpFront() == GL11.GL_KEEP && stencil.getZFailOpBack() == GL11.GL_KEEP
            && stencil.getZPassOpFront() == GL11.GL_KEEP && stencil.getZPassOpBack() == GL11.GL_KEEP;
    }

    public static boolean eligible() {
        return AngelicaConfig.enableEntityBatching && GLStateManager.getActiveProgram() == 0
            && (boolean) Settings.MODE_GLINT_WORLD.option.getStore()
            && GlintCompatibility.held() && opaqueTexture(Minecraft.getMinecraft());
    }

    public static boolean begin() {
        if (!eligible()) return false;
        final var mc = Minecraft.getMinecraft();
        final var texture = mc.getTextureManager().getTexture(TEXTURE);
        if (texture == null || texture.getGlTextureId() != GLStateManager.getTextures().getTextureUnitBindings(0).getBinding()) return false;
        return firstMatrix().equals(GLStateManager.getTextures().getTextureUnitMatrix(0), 0.000001f) && CombinedGlint.begin(secondMatrix());
    }

    public static ResourceLocation texture() {
        return TEXTURE;
    }

    public static Matrix4f firstMatrix() {
        updateMatrices();
        return first;
    }

    public static Matrix4f secondMatrix() {
        updateMatrices();
        return second;
    }

    private static void updateMatrices() {
        final long time = GlintClock.millis();
        if (matrixTime == time) return;
        matrixTime = time;
        first.scaling(0.125f).translate((float) (time % 3000L) / 3000.0f * 8.0f, 0, 0)
            .rotate((float) Math.toRadians(-50), 0, 0, 1);
        second.scaling(0.125f).translate(-(float) (time % 4873L) / 4873.0f * 8.0f, 0, 0)
            .rotate((float) Math.toRadians(10), 0, 0, 1);
    }

    private static boolean opaqueTexture(Minecraft mc) {
        if (!listening) {
            listening = true;
            ((IReloadableResourceManager) mc.getResourceManager()).registerReloadListener(manager -> checked = false);
        }
        if (checked) return opaque;
        checked = true;
        opaque = false;
        try (var stream = mc.getResourceManager().getResource(TEXTURE).getInputStream()) {
            final var image = ImageIO.read(stream);
            if (image == null) return false;
            for (int y = 0; y < image.getHeight(); y++) for (int x = 0; x < image.getWidth(); x++) {
                if ((image.getRGB(x, y) >>> 24) != 255) return false;
            }
            opaque = true;
        } catch (IOException | RuntimeException e) {
            GLStateManager.LOGGER.debug("Could not inspect held glint texture; keeping separate layers", e);
        }
        return opaque;
    }
}
