package com.gtnewhorizons.angelica.mixins.early.mcpatcherforge.cit.client.renderer.entity;

import com.gtnewhorizons.angelica.glsm.GLStateManager;
import net.minecraft.client.gui.FontRenderer;
import net.minecraft.client.renderer.OpenGlHelper;
import net.minecraft.client.renderer.entity.Render;
import net.minecraft.client.renderer.entity.RenderItem;
import net.minecraft.client.renderer.texture.TextureManager;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.util.IIcon;
import net.minecraft.util.ResourceLocation;

import org.lwjglx.opengl.GL11;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.Redirect;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import com.prupe.mcpatcher.cit.CITUtils;

@Mixin(RenderItem.class)
public abstract class MixinRenderItem extends Render {

    @Final
    @Shadow
    private static ResourceLocation RES_ITEM_GLINT;

    @Shadow
    public float zLevel;

    @Shadow
    protected abstract void renderGlint(int p_77018_1_, int p_77018_2_, int p_77018_3_, int p_77018_4_, int p_77018_5_);

    // TODO: figure out if ForgeHooksClient#renderEntityItem also needs work

    @Redirect(
        method = "doRender(Lnet/minecraft/entity/item/EntityItem;DDDFF)V",
        at = @At(
            value = "INVOKE",
            target = "Lnet/minecraft/item/Item;getIcon(Lnet/minecraft/item/ItemStack;I)Lnet/minecraft/util/IIcon;",
            remap = false))
    private IIcon modifyDoRender(Item item, ItemStack itemStack, int pass) {
        return CITUtils.getIcon(item.getIcon(itemStack, pass), itemStack, pass);
    }

    @Inject(
        method = "renderItemIntoGUI(Lnet/minecraft/client/gui/FontRenderer;Lnet/minecraft/client/renderer/texture/TextureManager;Lnet/minecraft/item/ItemStack;IIZ)V",
        at = @At(value = "INVOKE", target = "Lorg/lwjgl/opengl/GL11;glColorMask(ZZZZ)V", remap = false, ordinal = 0),
        remap = false)
    private void modifyRenderItemIntoGUI1(FontRenderer fontRenderer, TextureManager manager, ItemStack itemStack, int x,
        int y, boolean renderEffect, CallbackInfo ci) {
        GLStateManager.glDepthMask(false);
    }

    @Inject(
        method = "renderItemIntoGUI(Lnet/minecraft/client/gui/FontRenderer;Lnet/minecraft/client/renderer/texture/TextureManager;Lnet/minecraft/item/ItemStack;IIZ)V",
        at = @At(value = "INVOKE", target = "Lorg/lwjgl/opengl/GL11;glEnable(I)V", remap = false, ordinal = 4),
        remap = false)
    private void modifyRenderItemIntoGUI2(FontRenderer fontRenderer, TextureManager manager, ItemStack itemStack, int x,
        int y, boolean renderEffect, CallbackInfo ci) {
        GLStateManager.glDepthMask(true);
    }

    @Redirect(
        method = "renderItemIntoGUI(Lnet/minecraft/client/gui/FontRenderer;Lnet/minecraft/client/renderer/texture/TextureManager;Lnet/minecraft/item/ItemStack;IIZ)V",
        at = @At(
            value = "INVOKE",
            target = "Lnet/minecraft/item/Item;getIcon(Lnet/minecraft/item/ItemStack;I)Lnet/minecraft/util/IIcon;",
            remap = false),
        remap = false)
    private IIcon modifyRenderItemIntoGUI3(Item item, ItemStack itemStack, int pass) {
        return CITUtils.getIcon(item.getIcon(itemStack, pass), itemStack, pass);
    }

    // if I don't do this the transparency in the inventory breaks, I'm sure there's a much better way of doing it but
    // my open gl knowledge is pretty much none-existent atm

    @Redirect(
        method = "renderItemIntoGUI(Lnet/minecraft/client/gui/FontRenderer;Lnet/minecraft/client/renderer/texture/TextureManager;Lnet/minecraft/item/ItemStack;IIZ)V",
        at = @At(value = "INVOKE", target = "Lorg/lwjgl/opengl/GL11;glEnable(I)V", remap = false, ordinal = 10),
        remap = false)
    private void cancelAlpha3(int cap) {

    }

    @Redirect(
        method = "renderItemIntoGUI(Lnet/minecraft/client/gui/FontRenderer;Lnet/minecraft/client/renderer/texture/TextureManager;Lnet/minecraft/item/ItemStack;IIZ)V",
        at = @At(value = "INVOKE", target = "Lorg/lwjgl/opengl/GL11;glDisable(I)V", remap = false, ordinal = 8),
        remap = false)
    private void cancelAlpha4(int cap) {

    }

    @Inject(
        method = "renderItemAndEffectIntoGUI(Lnet/minecraft/client/gui/FontRenderer;Lnet/minecraft/client/renderer/texture/TextureManager;Lnet/minecraft/item/ItemStack;II)V",
        at = @At(
            value = "INVOKE",
            target = "Lnet/minecraft/client/renderer/entity/RenderItem;renderItemIntoGUI(Lnet/minecraft/client/gui/FontRenderer;Lnet/minecraft/client/renderer/texture/TextureManager;Lnet/minecraft/item/ItemStack;IIZ)V",
            remap = false))
    private void modifyRenderItemAndEffectIntoGUI1(FontRenderer fontRenderer, TextureManager manager,
        ItemStack itemStack, int x, int y, CallbackInfo ci) {
        // Moved to before call, will not trigger with forge event
        GL11.glEnable(GL11.GL_ALPHA_TEST);
        GLStateManager.glAlphaFunc(GL11.GL_GREATER, 0.01f);
    }

    /**
     * Forge added a false && to the targeted if statement, this adds the entire statement back
     * TODO: target forges event render class instead & check compatibility
     */
    @SuppressWarnings("DuplicatedCode")
    @Inject(
        method = "renderItemAndEffectIntoGUI(Lnet/minecraft/client/gui/FontRenderer;Lnet/minecraft/client/renderer/texture/TextureManager;Lnet/minecraft/item/ItemStack;II)V",
        at = @At(value = "FIELD", target = "Lnet/minecraft/client/renderer/entity/RenderItem;zLevel:F", ordinal = 2))
    private void modifyRenderItemAndEffectIntoGUI2(FontRenderer fontRenderer, TextureManager manager,
        ItemStack itemStack, int x, int y, CallbackInfo ci) {
        if (!CITUtils.renderEnchantmentGUI(itemStack, x, y, this.zLevel) && itemStack.hasEffect(0)) {
            GLStateManager.glDepthFunc(GL11.GL_EQUAL);
            GL11.glDisable(GL11.GL_LIGHTING);
            GLStateManager.glDepthMask(false);
            manager.bindTexture(RES_ITEM_GLINT);
            GL11.glEnable(GL11.GL_ALPHA_TEST);
            GL11.glEnable(GL11.GL_BLEND);
            GLStateManager.glColor4f(0.5F, 0.25F, 0.8F, 1.0F);
            this.renderGlint(x * 431278612 + y * 32178161, x - 2, y - 2, 20, 20);
            GLStateManager.tryBlendFuncSeparate(770, 771, 1, 0);
            GLStateManager.glDepthMask(true);
            GL11.glEnable(GL11.GL_LIGHTING);
            GLStateManager.glDepthFunc(GL11.GL_LEQUAL);
        }
    }
}
