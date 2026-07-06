package com.gtnewhorizons.angelica.mixins.early.mcpatcherforge.hd;

import java.awt.image.BufferedImage;

import net.minecraft.client.gui.FontRenderer;
import net.minecraft.util.ResourceLocation;

import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Mutable;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Constant;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.ModifyConstant;
import org.spongepowered.asm.mixin.injection.ModifyVariable;
import org.spongepowered.asm.mixin.injection.Redirect;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;
import org.spongepowered.asm.mixin.injection.callback.LocalCapture;

import com.llamalad7.mixinextras.sugar.Share;
import com.llamalad7.mixinextras.sugar.ref.LocalIntRef;
import com.prupe.mcpatcher.cc.ColorizeWorld;
import com.prupe.mcpatcher.hd.FontUtils;

import jss.notfine.util.FontRendererExpansion;

@Mixin(FontRenderer.class)
public abstract class MixinFontRenderer implements FontRendererExpansion {

    @Shadow
    @Final
    private static ResourceLocation[] unicodePageLocations;

    @Shadow
    protected int[] charWidth;

    @Mutable
    @Shadow
    @Final
    protected ResourceLocation locationFontTexture;

    @Shadow(remap = false)
    protected abstract void bindTexture(ResourceLocation location);

    @Shadow(remap = false)
    protected abstract void setColor(float r, float g, float b, float a);

    @Unique
    private float[] mcpatcher_forge$charWidthf;

    @Unique
    private ResourceLocation mcpatcher_forge$defaultFont;

    @Unique
    private ResourceLocation mcpatcher_forge$hdFont;

    @Unique
    private boolean mcpatcher_forge$isHD;

    @Unique
    private float mcpatcher_forge$fontAdj;

    public float[] getCharWidthf() {
        return mcpatcher_forge$charWidthf;
    }

    public void setCharWidthf(float[] widthf) {
        mcpatcher_forge$charWidthf = widthf;
    }

    public ResourceLocation getDefaultFont() {
        return mcpatcher_forge$defaultFont;
    }

    public void setDefaultFont(ResourceLocation font) {
        mcpatcher_forge$defaultFont = font;
    }

    public ResourceLocation getHDFont() {
        return mcpatcher_forge$hdFont;
    }

    public void setHDFont(ResourceLocation font) {
        mcpatcher_forge$hdFont = font;
    }

    public boolean getIsHD() {
        return mcpatcher_forge$isHD;
    }

    public void setIsHD(boolean isHD) {
        this.mcpatcher_forge$isHD = isHD;
    }

    public float getFontAdj() {
        return mcpatcher_forge$fontAdj;
    }

    public void setFontAdj(float fontAdj) {
        this.mcpatcher_forge$fontAdj = fontAdj;
    }

    @Redirect(
        method = "readFontTexture()V",
        at = @At(
            value = "FIELD",
            target = "Lnet/minecraft/client/gui/FontRenderer;locationFontTexture:Lnet/minecraft/util/ResourceLocation;"))
    private ResourceLocation modifyReadFontTexture1(FontRenderer instance) {
        this.locationFontTexture = FontUtils.getFontName((FontRenderer) (Object) this, this.locationFontTexture, 0.0f);
        return this.locationFontTexture;
    }

    @SuppressWarnings("InvalidInjectorMethodSignature") // IDEA plugin struggles with local capture
    @Inject(method = "readFontTexture()V", at = @At(value = "RETURN"), locals = LocalCapture.CAPTURE_FAILHARD)
    private void modifyReadFontTexture2(CallbackInfo ci, BufferedImage bufferedimage, int i, int j, int[] aint) {
        setCharWidthf(
            FontUtils
                .computeCharWidthsf((FontRenderer) (Object) this, locationFontTexture, bufferedimage, aint, charWidth));
    }

    @ModifyConstant(method = "renderCharAtPos(ICZ)F", constant = @Constant(floatValue = 4.0f))
    private float modifyRenderCharAtPos(float constant) {
        return this.mcpatcher_forge$charWidthf[32];
    }

    @ModifyConstant(method = "renderDefaultChar(IZ)F", constant = @Constant(floatValue = 1.0f))
    private float modifyRenderDefaultChar1(float constant) {
        return this.mcpatcher_forge$fontAdj;
    }

    @Inject(method = "renderDefaultChar(IZ)F", at = @At("RETURN"), cancellable = true)
    private void modifyRenderDefaultChar2(int ch, boolean b, CallbackInfoReturnable<Float> cir) {
        cir.setReturnValue(FontUtils.getCharWidthf((FontRenderer) (Object) this, this.charWidth, ch));
    }

    @Inject(
        method = "getUnicodePageLocation(I)Lnet/minecraft/util/ResourceLocation;",
        at = @At("RETURN"),
        cancellable = true)
    private void modifyGetUnicodePageLocation(int index, CallbackInfoReturnable<ResourceLocation> cir) {
        cir.setReturnValue(FontUtils.getUnicodePage(unicodePageLocations[index]));
    }

    @Inject(
        method = "renderStringAtPos(Ljava/lang/String;Z)V",
        locals = LocalCapture.CAPTURE_FAILHARD,
        at = @At(value = "FIELD", target = "Lnet/minecraft/client/gui/FontRenderer;colorCode:[I"))
    private void modifyRenderStringAtPos1(String string, boolean bool, CallbackInfo ci, int i, char c0, int j,
        @Share("renderStringAtPosIndex") LocalIntRef renderStringAtPosIndex) {
        renderStringAtPosIndex.set(j);
    }

    // IDEA plugin really struggles with this for some reason
    @SuppressWarnings("InvalidInjectorMethodSignature")
    @ModifyVariable(
        method = "renderStringAtPos(Ljava/lang/String;Z)V",
        at = @At(value = "STORE", ordinal = 0),
        ordinal = 2)
    private int modifyRenderStringAtPos2(int color,
        @Share("renderStringAtPosIndex") LocalIntRef renderStringAtPosIndex) {
        return ColorizeWorld.colorizeText(color, renderStringAtPosIndex.get());
    }

    @ModifyVariable(method = "renderString(Ljava/lang/String;IIIZ)I", at = @At("HEAD"), ordinal = 2, argsOnly = true)
    private int modifyRenderString(int colorizeText) {
        return ColorizeWorld.colorizeText(colorizeText);
    }

    @Inject(method = "getStringWidth(Ljava/lang/String;)I", at = @At("HEAD"), cancellable = true)
    private void modifyGetStringWidth(String p_78256_1_, CallbackInfoReturnable<Integer> cir) {
        if (getIsHD()) {
            cir.setReturnValue((int) FontUtils.getStringWidthf((FontRenderer) (Object) this, p_78256_1_));
        }
    }
}
