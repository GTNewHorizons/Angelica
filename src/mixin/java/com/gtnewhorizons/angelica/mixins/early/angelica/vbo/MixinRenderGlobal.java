package com.gtnewhorizons.angelica.mixins.early.angelica.vbo;

import com.gtnewhorizons.angelica.render.CloudRenderer;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.WorldClient;
import net.minecraft.client.renderer.RenderGlobal;
import net.minecraft.client.renderer.Tessellator;
import net.minecraftforge.client.IRenderHandler;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Overwrite;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;

@Mixin(value = RenderGlobal.class)
public class MixinRenderGlobal {
    @Shadow public WorldClient theWorld;
    @Shadow public Minecraft mc;
    @Shadow private int cloudTickCounter;

    /**
     * @author mitchej123
     * @reason VBO Clouds
     */
    @Overwrite
    public void renderClouds(float partialTicks) {
        final IRenderHandler renderer;
        if((renderer = theWorld.provider.getCloudRenderer()) != null) {
            renderer.render(partialTicks, theWorld, mc);
            return;
        }
        if(mc.theWorld.provider.isSurfaceWorld()) {
            CloudRenderer.getCloudRenderer().render(cloudTickCounter, partialTicks);
        }
    }
}
