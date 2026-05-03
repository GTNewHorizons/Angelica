package com.gtnewhorizons.umbra.mixins.early.glsm;

import com.gtnewhorizon.gtnhlib.client.renderer.DirectTessellator;
import net.minecraft.client.renderer.Tessellator;
import net.minecraft.client.renderer.WorldRenderer;
import net.minecraft.client.shader.TesselatorVertexState;
import net.minecraft.entity.EntityLivingBase;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import com.gtnewhorizons.angelica.glsm.GLStateManager;
import net.minecraftforge.client.ForgeHooksClient;

@Mixin(WorldRenderer.class)
public class MixinWorldRenderer_VertexState {

    @Shadow private TesselatorVertexState vertexState;

    @Inject(method = "postRenderBlocks", at = @At("HEAD"), cancellable = true)
    private void umbra$skipVertexStateForDirectTessellator(int pass, EntityLivingBase entity, CallbackInfo ci) {
        if (Tessellator.instance instanceof DirectTessellator) {
            // Can't get vertex state from DirectTessellator, draw and return
            Tessellator.instance.draw();
            ForgeHooksClient.onPostRenderWorld((WorldRenderer)(Object)this, pass);
            GLStateManager.glPopMatrix();
            GLStateManager.glEndList();
            ci.cancel();
        }
    }
}
