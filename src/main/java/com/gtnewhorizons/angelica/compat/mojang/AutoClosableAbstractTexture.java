package com.gtnewhorizons.angelica.compat.mojang;

import com.gtnewhorizons.angelica.glsm.managers.GLTextureManager;
import net.minecraft.client.renderer.texture.AbstractTexture;
import org.lwjgl.opengl.GL11;

public abstract class AutoClosableAbstractTexture extends AbstractTexture implements AutoCloseable {
    @Override
    public void close() throws Exception {}

    // TODO: Is this needed?
    public void bind() {
        GLTextureManager.glBindTexture(GL11.GL_TEXTURE_2D, getGlTextureId());
    }
}
