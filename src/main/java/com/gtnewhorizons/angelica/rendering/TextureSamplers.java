package com.gtnewhorizons.angelica.rendering;

import com.gtnewhorizons.angelica.glsm.RenderSystem;
import lombok.Getter;
import net.coderbot.iris.gl.sampler.GlSampler;
import org.lwjgl.opengl.EXTTextureFilterAnisotropic;

public final class TextureSamplers {
    private static final GlSampler[][] TERRAIN_SAMPLERS = new GlSampler[2][17];
    @Getter
    private static GlSampler guiSampler;

    private TextureSamplers() {
    }

    public static void initRenderer() {
        guiSampler = new GlSampler(false, false, false, false, false, true);
    }

    public static GlSampler getTerrainSampler(boolean linear, int anisotropy) {
        final GlSampler[] samplers = TERRAIN_SAMPLERS[linear ? 1 : 0];
        GlSampler sampler = samplers[anisotropy];
        if (sampler == null) {
            sampler = new GlSampler(linear, true, true, false, false, true);
            if (anisotropy > 1) {
                RenderSystem.samplerParameterf(sampler.getId(), EXTTextureFilterAnisotropic.GL_TEXTURE_MAX_ANISOTROPY_EXT, anisotropy);
            }
            samplers[anisotropy] = sampler;
        }
        return sampler;
    }
}
