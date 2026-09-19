package com.gtnewhorizons.angelica.rendering.particles;

import com.gtnewhorizons.angelica.glsm.GLStateManager;

import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.texture.ITextureObject;
import net.minecraft.client.renderer.texture.TextureManager;
import net.minecraft.util.ResourceLocation;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.lwjgl.opengl.GL11;

public final class BopParticleDescriptors {

    private static final Logger LOG = LogManager.getLogger("Angelica");

    private static final String PIXIE_TRAIL_CLASS = "biomesoplenty.client.particles.EntityPixieTrailFX";
    private static final ResourceLocation PIXIE_TRAIL_TEXTURE = new ResourceLocation("biomesoplenty:textures/particles/pixietrail.png");

    private static final int UNRESOLVED = -1;

    private static int pixieTrailTexture = UNRESOLVED;

    public static final ParticleDescriptor PIXIE_TRAIL = (fx, partialTicks, out, state) -> {
        final int texture = pixieTrailTexture();
        if (texture == 0) return false;
        fx.particleScale = fx.particleScale * ParticleQuads.ramp(fx.particleAge, fx.particleMaxAge, partialTicks);
        ParticleQuads.vanillaQuad(fx, partialTicks, fx.particleScale, out);
        out.colorABGR = ParticleQuads.packColor(fx.particleRed, fx.particleGreen, fx.particleBlue, 1.0F);
        out.brightness = 175;
        state.texture = texture;
        state.blend = true;
        state.blendSrc = GL11.GL_SRC_ALPHA;
        state.blendDst = GL11.GL_ONE;
        state.blendSrcAlpha = GL11.GL_SRC_ALPHA;
        state.blendDstAlpha = GL11.GL_ONE;
        state.depthMask = false;
        return true;
    };

    private BopParticleDescriptors() {}

    public static void registerAll() {
        ParticleDescriptorRegistry.register(PIXIE_TRAIL_CLASS, PIXIE_TRAIL);
    }

    public static void invalidate() {
        pixieTrailTexture = UNRESOLVED;
    }

    private static int pixieTrailTexture() {
        int id = pixieTrailTexture;
        if (id != UNRESOLVED) return id;
        final TextureManager manager = Minecraft.getMinecraft().getTextureManager();
        ITextureObject texture = manager.getTexture(PIXIE_TRAIL_TEXTURE);
        if (texture == null) {
            final int previous = GLStateManager.getBoundTextureForServerState();
            manager.bindTexture(PIXIE_TRAIL_TEXTURE);
            texture = manager.getTexture(PIXIE_TRAIL_TEXTURE);
            GLStateManager.glBindTexture(GL11.GL_TEXTURE_2D, previous);
        }
        id = texture == null ? 0 : texture.getGlTextureId();
        if (id == 0) LOG.warn("Pixie trail texture {} is unavailable; those particles will not draw", PIXIE_TRAIL_TEXTURE);
        pixieTrailTexture = id;
        return id;
    }
}
