package com.gtnewhorizons.angelica.compat.mojang;

import com.gtnewhorizons.angelica.glsm.GLStateManager;
import net.minecraft.client.renderer.texture.AbstractTexture;
import net.minecraft.client.resources.IResourceManager;
import org.lwjgl.opengl.GL11;

import java.io.IOException;

public abstract class AutoClosableAbstractTexture extends AbstractTexture implements AutoCloseable {

    public abstract void loadTexture(IResourceManager manager) throws IOException;

    @Override
    public void close() throws Exception {}

    // TODO: Is this needed?
    public void bind() {
        GLStateManager.glBindTexture(GL11.GL_TEXTURE_2D, getGlTextureId());
    }
}
