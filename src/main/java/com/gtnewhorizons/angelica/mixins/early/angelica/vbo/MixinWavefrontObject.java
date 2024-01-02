package com.gtnewhorizons.angelica.mixins.early.angelica.vbo;

import com.gtnewhorizons.angelica.compat.mojang.VertexBuffer;
import com.gtnewhorizons.angelica.compat.mojang.VertexFormat;
import com.gtnewhorizons.angelica.compat.toremove.DefaultVertexFormat;
import com.gtnewhorizons.angelica.mixins.interfaces.IModelCustomExt;
import net.minecraft.client.renderer.Tessellator;
import net.minecraftforge.client.model.obj.GroupObject;
import net.minecraftforge.client.model.obj.WavefrontObject;
import org.lwjgl.opengl.GL11;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;

@Mixin(value = WavefrontObject.class, remap = false)
public abstract class MixinWavefrontObject implements IModelCustomExt {
    @Unique private VertexBuffer vertexBuffer;

    @Shadow private GroupObject currentGroupObject;

    @Shadow public abstract void tessellateAll(Tessellator tessellator);

    @Unique VertexFormat format = DefaultVertexFormat.VBO;

    @Override
    public void rebuildVBO() {
        if(currentGroupObject == null) {
            throw new RuntimeException("No group object selected");
        }
        if (this.vertexBuffer != null) {
            this.vertexBuffer.close();
        }
        final Tessellator tessellator = Tessellator.instance;
        tessellator.startDrawing(currentGroupObject.glDrawingMode);
        tessellateAll(tessellator);

        this.vertexBuffer = new VertexBuffer();
        this.vertexBuffer.bind();
        this.vertexBuffer.upload(tessellator, format);
        this.vertexBuffer.unbind();
    }

    @Override
    public void renderAllVBO() {
        if(vertexBuffer == null) {
            rebuildVBO();
        }
        vertexBuffer.bind();
        format.setupBufferState(0L);
        vertexBuffer.draw(GL11.GL_QUADS);
        format.clearBufferState();
        vertexBuffer.unbind();
    }

}
