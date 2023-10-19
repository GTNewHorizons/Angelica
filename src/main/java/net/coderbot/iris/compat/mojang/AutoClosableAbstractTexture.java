package net.coderbot.iris.compat.mojang;

import net.minecraft.client.renderer.texture.AbstractTexture;
import net.minecraft.client.resources.IResourceManager;
import org.lwjgl.opengl.GL11;

import java.io.IOException;

public abstract class AutoClosableAbstractTexture extends AbstractTexture implements AutoCloseable {

    public abstract void load(IResourceManager manager) throws IOException;

    @Override
    public void close() throws Exception {}

    // TODO: Is this needed?
    public void bind() {
        GL11.glBindTexture(GL11.GL_TEXTURE_2D, getGlTextureId());
    }
}
