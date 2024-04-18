package com.gtnewhorizons.angelica.mixins.early.mcpatcherforge.renderpass;

import net.minecraft.client.renderer.EntityRenderer;
import net.minecraft.client.renderer.GLAllocation;
import net.minecraft.client.renderer.RenderGlobal;
import net.minecraft.entity.EntityLivingBase;
import net.minecraft.world.IWorldAccess;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.ModifyVariable;
import org.spongepowered.asm.mixin.injection.Redirect;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import com.prupe.mcpatcher.renderpass.RenderPass;
import com.prupe.mcpatcher.renderpass.RenderPassMap;

@Mixin(RenderGlobal.class)
public abstract class MixinRenderGlobal implements IWorldAccess {

    @Redirect(
        method = "<init>(Lnet/minecraft/client/Minecraft;)V",
        at = @At(
            value = "INVOKE",
            target = "Lnet/minecraft/client/renderer/GLAllocation;generateDisplayLists(I)I",
            ordinal = 0))
    private int modifyRenderGlobal(int n) {
        return GLAllocation.generateDisplayLists(n / 3 * 5);
    }

    @ModifyVariable(
        method = "loadRenderers()V",
        at = @At(
            value = "INVOKE",
            target = "Ljava/util/List;add(Ljava/lang/Object;)Z",
            remap = false,
            shift = At.Shift.AFTER),
        ordinal = 1)
    private int modifyLoadRenderers(int input) {
        return input + 2;
    }

    // Order important here!

    @Inject(
        method = "sortAndRender(Lnet/minecraft/entity/EntityLivingBase;ID)I",
        at = @At(value = "HEAD"),
        cancellable = true)
    private void modifySortAndRender1(EntityLivingBase entity, int map18To17, double partialTickTime,
        CallbackInfoReturnable<Integer> cir) {
        if (!RenderPass.preRenderPass(RenderPassMap.map17To18(map18To17))) {
            cir.setReturnValue(RenderPass.postRenderPass(0));
        }
    }

    @ModifyVariable(
        method = "sortAndRender(Lnet/minecraft/entity/EntityLivingBase;ID)I",
        at = @At(value = "HEAD"),
        ordinal = 0,
        argsOnly = true)
    private int modifySortAndRender2(int map18To17) {
        return RenderPassMap.map18To17(map18To17);
    }

    @Inject(
        method = "sortAndRender(Lnet/minecraft/entity/EntityLivingBase;ID)I",
        at = @At(value = "RETURN"),
        cancellable = true)
    private void modifySortAndRender3(EntityLivingBase entity, int renderPass, double partialTickTime,
        CallbackInfoReturnable<Integer> cir) {
        cir.setReturnValue(RenderPass.postRenderPass(cir.getReturnValue()));
    }

    @Redirect(
        method = "renderAllRenderLists(ID)V",
        at = @At(value = "INVOKE", target = "Lnet/minecraft/client/renderer/EntityRenderer;enableLightmap(D)V"))
    private void modifyRenderAllRenderLists(EntityRenderer instance, double partialTick) {
        RenderPass.enableDisableLightmap(instance, partialTick);
    }

}
