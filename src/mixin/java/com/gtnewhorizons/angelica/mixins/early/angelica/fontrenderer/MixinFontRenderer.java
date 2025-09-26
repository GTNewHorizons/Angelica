package com.gtnewhorizons.angelica.mixins.early.angelica.fontrenderer;

import com.gtnewhorizon.gtnhlib.util.font.IFontParameters;
import com.gtnewhorizons.angelica.client.font.BatchingFontRenderer;
import com.gtnewhorizons.angelica.glsm.GLStateManager;
import com.gtnewhorizons.angelica.mixins.interfaces.FontRendererAccessor;
import net.minecraft.client.gui.FontRenderer;
import net.minecraft.client.renderer.texture.TextureManager;
import net.minecraft.client.settings.GameSettings;
import net.minecraft.util.ResourceLocation;
import org.lwjgl.opengl.GL11;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;
import org.spongepowered.asm.mixin.injection.ModifyConstant;
import org.spongepowered.asm.mixin.injection.Constant;

import java.util.Random;

/**
 * Fixes the horrible performance of FontRenderer
 * @author eigenraven
 */
@Mixin(FontRenderer.class)
public abstract class MixinFontRenderer implements FontRendererAccessor, IFontParameters {

    @Shadow
    private boolean randomStyle;

    @Shadow
    private boolean boldStyle;

    @Shadow
    private boolean strikethroughStyle;

    @Shadow
    private boolean underlineStyle;

    @Shadow
    private boolean italicStyle;

    @Shadow
    private int[] colorCode;

    @Shadow
    private int textColor;

    @Shadow(remap = false)
    protected abstract void setColor(float r, float g, float b, float a);

    @Shadow
    private float alpha;

    @Shadow
    private float red;

    /** Actually green */
    @Shadow
    private float blue;

    /** Actually blue */
    @Shadow
    private float green;

    @Shadow
    public Random fontRandom;

    @Shadow
    protected int[] charWidth;

    @Shadow
    private boolean unicodeFlag;

    @Shadow
    protected float posX;

    @Shadow
    protected float posY;

    @Shadow
    protected abstract float renderCharAtPos(int p_78278_1_, char p_78278_2_, boolean p_78278_3_);

    @Shadow(remap = false)
    protected abstract void doDraw(float f);

    @Shadow
    @Final
    private static ResourceLocation[] unicodePageLocations;
    @Shadow
    protected byte[] glyphWidth;
    @Shadow
    @Final
    protected ResourceLocation locationFontTexture;
    @Shadow
    @Final
    private TextureManager renderEngine;
    @Shadow
    private boolean bidiFlag;

    @Shadow
    protected abstract String bidiReorder(String p_147647_1_);

    @Shadow(remap = false)
    protected abstract void bindTexture(ResourceLocation location);

    @Unique
    public BatchingFontRenderer angelica$batcher;

    @Unique
    private static final char angelica$FORMATTING_CHAR = 167; // §

    @Unique
    private static final float angelica$1_over_255 = 1.0f/255.0f; // §

    @Inject(method = "<init>", at = @At("TAIL"))
    private void angelica$injectBatcher(GameSettings settings, ResourceLocation fontLocation, TextureManager texManager,
        boolean unicodeMode, CallbackInfo ci) {
        angelica$batcher = new BatchingFontRenderer((FontRenderer) (Object) this, unicodePageLocations, this.charWidth, this.glyphWidth, this.colorCode, this.locationFontTexture);
    }

    @Unique
    private static boolean angelica$charInRange(char what, char fromInclusive, char toInclusive) {
        return (what >= fromInclusive) && (what <= toInclusive);
    }

    /**
     * Only allow using the batched renderer if we are not in an OpenGL Display List
     * Batched font renderer is not compatible with display lists, and won't really
     * help performance when display lists are already being used anyway.
     */
    @Inject(method = "drawString(Ljava/lang/String;IIIZ)I", at = @At("HEAD"), cancellable = true)
    public void angelica$BatchedFontRendererDrawString(String text, int x, int y, int argb, boolean dropShadow, CallbackInfoReturnable<Integer> cir)
    {
        if (GLStateManager.getListMode() == 0) {
            cir.setReturnValue(angelica$drawStringBatched(text, x, y, argb, dropShadow));
        }
    }

    /**
     * See above explanation about batched renderer in display lists.
     */
    @Inject(method = "renderString", at = @At("HEAD"), cancellable = true)
    public void angelica$BatchedFontRendererRenderString(String text, int x, int y, int argb, boolean dropShadow, CallbackInfoReturnable<Integer> cir) {
        if (GLStateManager.getListMode() == 0) {
            cir.setReturnValue(angelica$drawStringBatched(text, x, y, argb, dropShadow));
        }
    }

    @Override
    public int angelica$drawStringBatched(String text, int x, int y, int argb, boolean dropShadow) {
        if (text == null)
        {
            return 0;
        }
        else
        {
            if (this.bidiFlag)
            {
                text = this.bidiReorder(text);
            }

            if ((argb & 0xfc000000) == 0)
            {
                argb |= 0xff000000;
            }

            this.red = (float)(argb >> 16 & 255) / 255.0F;
            this.blue = (float)(argb >> 8 & 255) / 255.0F;
            this.green = (float)(argb & 255) / 255.0F;
            this.alpha = (float)(argb >> 24 & 255) / 255.0F;
            GL11.glColor4f(1.0f, 1.0f, 1.0f, 1.0f);
            this.posX = (float)x;
            this.posY = (float)y;
            return (int) angelica$batcher.drawString(x, y, argb, dropShadow, unicodeFlag, text, 0, text.length());
        }
    }

    @Override
    public BatchingFontRenderer angelica$getBatcher() {
        return angelica$batcher;
    }

    @Override
    public void angelica$bindTexture(ResourceLocation location) { this.bindTexture(location); }

    @ModifyConstant(method = "getCharWidth", constant = @Constant(intValue = 7))
    private int angelica$maxCharWidth(int original) {
        return Integer.MAX_VALUE;
    }

    @Inject(method = "getCharWidth", at = @At("HEAD"), cancellable = true)
    public void getCharWidth(char c, CallbackInfoReturnable<Integer> cir) {
        cir.setReturnValue((int) angelica$getBatcher().getCharWidthFine(c));
    }

    @Override
    public float getGlyphScaleX() {
        return angelica$getBatcher().getGlyphScaleX();
    }

    @Override
    public float getGlyphScaleY() {
        return angelica$getBatcher().getGlyphScaleY();
    }

    @Override
    public float getGlyphSpacing() {
        return angelica$getBatcher().getGlyphSpacing();
    }

    @Override
    public float getWhitespaceScale() {
        return angelica$getBatcher().getWhitespaceScale();
    }

    @Override
    public float getShadowOffset() {
        return angelica$getBatcher().getShadowOffset();
    }

    @Override
    public float getCharWidthFine(char chr) {
        return angelica$getBatcher().getCharWidthFine(chr);
    }
}
