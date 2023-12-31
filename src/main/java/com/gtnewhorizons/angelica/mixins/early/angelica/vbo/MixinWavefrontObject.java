package com.gtnewhorizons.angelica.mixins.early.angelica.vbo;

import com.google.common.collect.ImmutableList;
import com.gtnewhorizons.angelica.compat.mojang.VertexBuffer;
import com.gtnewhorizons.angelica.compat.mojang.VertexFormatElement;
import com.gtnewhorizons.angelica.compat.toremove.DefaultVertexFormat;
import com.gtnewhorizons.angelica.compat.toremove.VertexFormat;
import com.gtnewhorizons.angelica.mixins.interfaces.IModelCustomExt;
import net.minecraft.client.renderer.Tessellator;
import net.minecraftforge.client.model.obj.GroupObject;
import net.minecraftforge.client.model.obj.WavefrontObject;
import org.lwjgl.opengl.GL11;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;

@Mixin(WavefrontObject.class)
public abstract class MixinWavefrontObject implements IModelCustomExt {
    @Unique private VertexBuffer vertexBuffer;

    @Shadow private GroupObject currentGroupObject;

    @Shadow public abstract void tessellateAll(Tessellator tessellator);

    @Unique VertexFormat format;

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
        ImmutableList.Builder<VertexFormatElement> builder = new ImmutableList.Builder<>();
        builder.add(DefaultVertexFormat.POSITION_ELEMENT);
        if(tessellator.hasTexture) builder.add(DefaultVertexFormat.TEXTURE_0_ELEMENT);
        if(tessellator.hasNormals) builder.add(DefaultVertexFormat.NORMAL_ELEMENT);
        format = new VertexFormat(builder.build());

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
//        GL11.glVertexPointer(3, GL11.GL_FLOAT, 12, 0L);
//        GL11.glEnableClientState(GL11.GL_VERTEX_ARRAY);
        format.setupBufferState(0L);
        vertexBuffer.draw(GL11.GL_QUADS);
        format.clearBufferState();
//        GL11.glDisableClientState(GL11.GL_VERTEX_ARRAY);
        vertexBuffer.unbind();
    }

}
