package com.gtnewhorizons.angelica.mixins.early.angelica.vbo;

import com.gtnewhorizons.angelica.client.renderer.CapturingTessellator;
import com.gtnewhorizons.angelica.compat.mojang.VertexBuffer;
import com.gtnewhorizons.angelica.compat.mojang.VertexFormat;
import com.gtnewhorizons.angelica.compat.mojang.DefaultVertexFormat;
import com.gtnewhorizons.angelica.compat.nd.Quad;
import com.gtnewhorizons.angelica.glsm.TessellatorManager;
import com.gtnewhorizons.angelica.mixins.interfaces.IModelCustomExt;
import net.minecraft.client.renderer.Tessellator;
import net.minecraftforge.client.model.obj.GroupObject;
import net.minecraftforge.client.model.obj.WavefrontObject;
import org.lwjgl.opengl.GL11;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;

import java.nio.ByteBuffer;
import java.util.List;

@Mixin(value = WavefrontObject.class, remap = false)
public abstract class MixinWavefrontObject implements IModelCustomExt {
    @Unique private VertexBuffer vertexBuffer;

    @Shadow private GroupObject currentGroupObject;

    @Shadow public abstract void tessellateAll(Tessellator tessellator);

    @Unique VertexFormat format = DefaultVertexFormat.ITEM_VBO;

    @Override
    public void rebuildVBO() {
        if(currentGroupObject == null) {
            throw new RuntimeException("No group object selected");
        }
        if (this.vertexBuffer != null) {
            this.vertexBuffer.close();
        }
        TessellatorManager.startCapturing();
        final CapturingTessellator tess = (CapturingTessellator) TessellatorManager.get();
        tess.startDrawing(currentGroupObject.glDrawingMode);
        tessellateAll(tess);
        final ByteBuffer byteBuffer = TessellatorManager.stopCapturingToBuffer(format);

        this.vertexBuffer = new VertexBuffer(format);
        vertexBuffer.upload(byteBuffer);
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
