package com.gtnewhorizons.angelica.mixins.early.shadersmod.renderer;

import com.gtnewhorizons.angelica.client.ShadersTex;
import net.minecraft.client.renderer.texture.LayeredTexture;
import net.minecraft.client.resources.IResourceManager;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Overwrite;
import org.spongepowered.asm.mixin.Shadow;

import java.io.IOException;
import java.util.List;

@Mixin(LayeredTexture.class)
public class MixinLayeredTexture {
    // TODO: PBR
    @Final
    @Shadow
    public List<String> layeredTextureNames;

    /**
     * @author glowredman
     * @reason must take normal and specular maps into account
     */
    @Overwrite
    public void loadTexture(IResourceManager p_110551_1_) throws IOException {
        ShadersTex.loadLayeredTexture((LayeredTexture) (Object) this, p_110551_1_, this.layeredTextureNames);
    }

}
