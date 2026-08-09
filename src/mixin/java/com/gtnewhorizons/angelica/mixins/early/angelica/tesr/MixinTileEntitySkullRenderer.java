package com.gtnewhorizons.angelica.mixins.early.angelica.tesr;

import com.gtnewhorizons.angelica.rendering.tesr.VanillaModelMeshes;
import net.minecraft.client.model.ModelSkeletonHead;
import net.minecraft.client.renderer.tileentity.TileEntitySkullRenderer;
import net.minecraft.client.renderer.tileentity.TileEntitySpecialRenderer;
import net.minecraft.entity.Entity;
import net.minecraft.util.ResourceLocation;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;

@Mixin(TileEntitySkullRenderer.class)
public abstract class MixinTileEntitySkullRenderer extends TileEntitySpecialRenderer {

    @Unique
    private ResourceLocation angelica$skullTexture;

    @Redirect(method = "func_152674_a(FFFIFILcom/mojang/authlib/GameProfile;)V", at = @At(value = "INVOKE", target = "Lnet/minecraft/client/renderer/tileentity/TileEntitySkullRenderer;bindTexture(Lnet/minecraft/util/ResourceLocation;)V"))
    private void angelica$captureSkullTexture(TileEntitySkullRenderer self, ResourceLocation texture) {
        angelica$skullTexture = texture;
        this.bindTexture(texture);
    }

    @Redirect(method = "func_152674_a(FFFIFILcom/mojang/authlib/GameProfile;)V", at = @At(value = "INVOKE", target = "Lnet/minecraft/client/model/ModelSkeletonHead;render(Lnet/minecraft/entity/Entity;FFFFFF)V"))
    private void angelica$renderSkullCached(ModelSkeletonHead model, Entity entity, float limbSwing, float limbYaw, float age, float yaw, float pitch, float scale) {
        if (!VanillaModelMeshes.renderSkull(model, angelica$skullTexture, yaw, pitch, scale)) {
            model.render(entity, limbSwing, limbYaw, age, yaw, pitch, scale);
        }
    }
}
