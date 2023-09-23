package org.embeddedt.archaicfix.mixins.client.occlusion;

import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.WorldClient;
import net.minecraft.client.renderer.*;
import net.minecraft.client.renderer.culling.ICamera;
import net.minecraft.client.renderer.entity.RenderManager;
import net.minecraft.entity.Entity;
import net.minecraft.entity.EntityLivingBase;
import org.embeddedt.archaicfix.occlusion.*;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Overwrite;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.Redirect;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import java.util.*;

@Mixin(value = RenderGlobal.class, priority = -2)
public abstract class MixinRenderGlobal {
    /**
     * Queue a renderer to be updated.
     */
    @Inject(method = "markBlocksForUpdate", at = @At("HEAD"), cancellable = true)
    private void handleOffthreadUpdate(int x1, int y1, int z1, int x2, int y2, int z2, CallbackInfo ci) {
        ci.cancel();
        OcclusionHelpers.renderer.handleOffthreadUpdate(x1, y1, z1, x2, y2, z2);
    }

    @Redirect(method = "renderEntities", at = @At(value = "INVOKE", target = "Lnet/minecraft/client/renderer/entity/RenderManager;renderEntitySimple(Lnet/minecraft/entity/Entity;F)Z"))
    private boolean skipRenderingIfNotVisible(RenderManager instance, Entity entity, float tick) {
        return OcclusionHelpers.renderer.skipRenderingIfNotVisible(instance, entity, tick);
    }

    /**
     * @author skyboy, embeddedt
     * @reason Include information on occlusion
     */
    @Overwrite
    public String getDebugInfoRenders() {
        return OcclusionHelpers.renderer.getDebugInfoRenders();
    }

    @Inject(method = "<init>", at = @At("RETURN"))
    private void initBetterLists(Minecraft p_i1249_1_, CallbackInfo ci) {
        OcclusionHelpers.renderer = new OcclusionRenderer((RenderGlobal)(Object)this);
        OcclusionHelpers.renderer.initBetterLists();
    }

    @Redirect(method = "loadRenderers", at = @At(value = "INVOKE", target = "Ljava/util/List;clear()V", ordinal = 0))
    private void clearRendererUpdateQueue(List instance) {
        OcclusionHelpers.renderer.clearRendererUpdateQueue(instance);
    }

    @Redirect(method = { "loadRenderers", "markRenderersForNewPosition" }, at = @At(value = "INVOKE", target = "Ljava/util/List;add(Ljava/lang/Object;)Z", ordinal = 0))
    private boolean sortAndAddRendererUpdateQueue(List instance, Object renderer) {
        return OcclusionHelpers.renderer.sortAndAddRendererUpdateQueue(instance, renderer);
    }

    @Redirect(method = "<init>", at = @At(value = "INVOKE", target = "Lnet/minecraft/client/renderer/OpenGlCapsChecker;checkARBOcclusion()Z"))
    private boolean neverEnableOcclusion() {
        return false;
    }

    @Inject(method = "updateRenderers", at = @At("HEAD"), cancellable = true)
    private void performCullingUpdates(EntityLivingBase view, boolean p_72716_2_, CallbackInfoReturnable<Boolean> cir) {
        OcclusionHelpers.renderer.performCullingUpdates(view, p_72716_2_);
        cir.setReturnValue(true);
    }

    @Inject(method = "setWorldAndLoadRenderers", at = @At("HEAD"))
    private void setWorkerWorld(WorldClient world, CallbackInfo ci) {
        OcclusionHelpers.worker.setWorld((RenderGlobal)(Object)this, world);
    }

    @Inject(method = "loadRenderers", at = @At("HEAD"))
    private void resetLoadedRenderers(CallbackInfo ci) {
        OcclusionHelpers.renderer.resetLoadedRenderers();
    }

    @Inject(method = "loadRenderers", at = @At("TAIL"))
    private void resetOcclusionWorker(CallbackInfo ci) {
        OcclusionHelpers.renderer.resetOcclusionWorker();
    }

    @Redirect(method = "loadRenderers", at = @At(value = "INVOKE", target = "Ljava/util/Arrays;sort([Ljava/lang/Object;Ljava/util/Comparator;)V", ordinal = 0))
    private void skipSort2(Object[] ts, Comparator<?> comparator) {

    }

    @Redirect(method = "loadRenderers", at = @At(value = "INVOKE", target = "Lnet/minecraft/client/renderer/WorldRenderer;markDirty()V", ordinal = 0))
    private void markRendererInvisible(WorldRenderer instance) {
        OcclusionHelpers.renderer.markRendererInvisible(instance);
    }

    @Redirect(method = "markRenderersForNewPosition", at = @At(value = "INVOKE", target = "Lnet/minecraft/client/renderer/WorldRenderer;setPosition(III)V"))
    private void setPositionAndMarkInvisible(WorldRenderer wr, int x, int y, int z) {
        OcclusionHelpers.renderer.setPositionAndMarkInvisible(wr, x, y, z);
    }

    @Inject(method = "markRenderersForNewPosition", at = @At("TAIL"))
    private void runWorker(int p_72722_1_, int p_72722_2_, int p_72722_3_, CallbackInfo ci) {
        OcclusionHelpers.renderer.runWorker(p_72722_1_, p_72722_2_, p_72722_3_);
    }

    /**
     * @author skyboy, embeddedt
     * @reason Update logic
     */
    @Overwrite
    public int sortAndRender(EntityLivingBase view, int pass, double tick) {
        return OcclusionHelpers.renderer.sortAndRender(view, pass, tick);
    }

    /**
     * @author embeddedt, skyboy
     * @reason occlusion culling
     */
    @Overwrite
    @SuppressWarnings("unchecked")
    public int renderSortedRenderers(int start, int end, int pass, double tick) {
        return OcclusionHelpers.renderer.sortAndRender(start, end, pass, tick);
    }

    /**
     * @author makamys
     * @reason Integrate with the logic in {@link OcclusionWorker#run(boolean)}.
     */
    @Overwrite
    public void clipRenderersByFrustum(ICamera p_72729_1_, float p_72729_2_) {
        OcclusionHelpers.renderer.clipRenderersByFrustum(p_72729_1_, p_72729_2_);
    }
}
