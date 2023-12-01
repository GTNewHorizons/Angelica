package com.gtnewhorizons.angelica.mixins.early.angelica.fontrenderer;

import com.gtnewhorizons.angelica.client.font.BatchingFontRenderer;
import com.gtnewhorizons.angelica.mixins.interfaces.FontRendererAccessor;
import net.minecraft.client.gui.FontRenderer;
import net.minecraft.client.renderer.texture.TextureManager;
import net.minecraft.client.settings.GameSettings;
import net.minecraft.util.ResourceLocation;
import org.lwjgl.opengl.GL11;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Overwrite;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import java.util.Random;

/**
 * Fixes the horrible performance of FontRenderer
 * @author eigenraven
 */
@Mixin(FontRenderer.class)
public abstract class MixinFontRenderer implements FontRendererAccessor {

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

    @Unique
    public BatchingFontRenderer angelica$batcher;

    @Unique
    private static final char angelica$FORMATTING_CHAR = 167; // §

    @Unique
    private static final float angelica$1_over_255 = 1.0f/255.0f; // §

    @Inject(method = "<init>", at = @At("TAIL"))
    private void angelica$injectBatcher(GameSettings settings, ResourceLocation fontLocation, TextureManager texManager,
        boolean unicodeMode, CallbackInfo ci) {
        angelica$batcher = new BatchingFontRenderer((FontRenderer) (Object) this, unicodePageLocations, this.charWidth, this.glyphWidth, this.colorCode, this.locationFontTexture, this.renderEngine);
    }

    @Unique
    private static boolean angelica$charInRange(char what, char fromInclusive, char toInclusive) {
        return (what >= fromInclusive) && (what <= toInclusive);
    }

    /**
     * @author eigenraven
     * @reason Replace with more sensible batched rendering and optimize some operations
     */
    @Overwrite
    public int drawString(String text, int x, int y, int argb, boolean dropShadow)
    {
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

    /**
     * @author eigenraven
     * @reason Replace with more sensible batched rendering and optimize some operations
     */
    @Overwrite
    private int renderString(String text, int x, int y, int argb, boolean dropShadow) {
        return drawString(text, x, y, argb, dropShadow);
    }

    @Override
    public BatchingFontRenderer angelica$getBatcher() {
        return angelica$batcher;
    }
}
