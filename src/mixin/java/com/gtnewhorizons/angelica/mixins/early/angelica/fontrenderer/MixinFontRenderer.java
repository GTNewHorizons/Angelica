package com.gtnewhorizons.angelica.mixins.early.angelica.fontrenderer;

import com.gtnewhorizon.gtnhlib.util.font.IFontParameters;
import com.gtnewhorizons.angelica.client.font.BatchingFontRenderer;
import com.gtnewhorizons.angelica.client.font.ColorCodeUtils;
import com.gtnewhorizons.angelica.compat.GTNHLibCompat;
import com.gtnewhorizons.angelica.config.AngelicaConfig;
import com.gtnewhorizons.angelica.glsm.DisplayListManager;
import com.gtnewhorizons.angelica.mixins.interfaces.FontRendererAccessor;
import net.minecraft.client.gui.FontRenderer;
import net.minecraft.client.renderer.texture.TextureManager;
import net.minecraft.client.settings.GameSettings;
import net.minecraft.util.ResourceLocation;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Constant;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.ModifyConstant;
import org.spongepowered.asm.mixin.injection.ModifyVariable;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import java.util.Random;

import static com.gtnewhorizons.angelica.client.font.ColorCodeUtils.FORMATTING_CHAR;
import static com.gtnewhorizons.angelica.client.font.ColorCodeUtils.GRADIENT_LENGTH;
import static com.gtnewhorizons.angelica.client.font.ColorCodeUtils.SECTION_X_LENGTH;

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

    @Inject(method = "<init>", at = @At("TAIL"))
    private void angelica$injectBatcher(GameSettings settings, ResourceLocation fontLocation, TextureManager texManager,
        boolean unicodeMode, CallbackInfo ci) {
        angelica$batcher = new BatchingFontRenderer((FontRenderer) (Object) this, this.colorCode, this.locationFontTexture);
        if (GTNHLibCompat.HAS_TEXT_PREPROCESSOR) {
            GTNHLibCompat.registerPreprocessor();
        }
    }

    @Inject(method = "readFontTexture", at = @At("RETURN"))
    private void angelica$onReadFontTexture(CallbackInfo ci) {
        angelica$batcher.initializeTextures();
    }

    /**
     * Only allow using the batched renderer if we are not in an OpenGL Display List
     * Batched font renderer is not compatible with display lists, and won't really
     * help performance when display lists are already being used anyway.
     */
    @Inject(method = "drawString(Ljava/lang/String;IIIZ)I", at = @At("HEAD"), cancellable = true)
    public void angelica$BatchedFontRendererDrawString(String text, int x, int y, int argb, boolean dropShadow, CallbackInfoReturnable<Integer> cir)
    {
        if (DisplayListManager.getListMode() == 0) {
            cir.setReturnValue(angelica$drawStringBatched(text, x, y, argb, dropShadow));
        }
    }

    /**
     * See above explanation about batched renderer in display lists.
     */
    @Inject(method = "renderString", at = @At("HEAD"), cancellable = true)
    public void angelica$BatchedFontRendererRenderString(String text, int x, int y, int argb, boolean dropShadow, CallbackInfoReturnable<Integer> cir) {
        if (DisplayListManager.getListMode() == 0) {
            cir.setReturnValue(angelica$drawStringBatched(text, x, y, argb, dropShadow));
        }
    }

    @Override
    public final int angelica$drawStringBatched(String text, int x, int y, int argb, boolean dropShadow) {
        if (text == null)
        {
            return 0;
        }
        else
        {
            text = ColorCodeUtils.convertAmpersandToSectionX(text);

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
            this.posX = (float)x;
            this.posY = (float)y;
            return angelica$batcher.drawString(text, x, y, argb, dropShadow);
        }
    }

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
    public final byte[] angelica$getGlyphWidths() {
        return glyphWidth;
    }

    @Override
    public final int[] angelica$getCharWidths() {
        return this.charWidth;
    }

    @Override
    public final void angelica$bindTexture(ResourceLocation location) {
        this.bindTexture(location);
    }

    @Override
    public final BatchingFontRenderer angelica$getBatcher() {
        return angelica$batcher;
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

    @Inject(method = "getFormatFromString", at = @At("HEAD"), cancellable = true)
    private static void angelica$getFormatFromString(String text, CallbackInfoReturnable<String> cir) {
        if (!AngelicaConfig.enableRGBColors) return;
        cir.setReturnValue(angelica$extractFormat(text));
    }

    @Unique private static final StringBuilder EXTRACT_STYLES = new StringBuilder();
    @Unique private static final StringBuilder EXTRACT_EFFECTS = new StringBuilder();
    @Unique private static final StringBuilder EXTRACT_COLOR = new StringBuilder();
    @Unique private static final StringBuilder EXTRACT_RESULT = new StringBuilder();

    @Unique
    private static String angelica$extractFormat(String text) {
        text = ColorCodeUtils.convertAmpersandToSectionX(text);
        final int len = text.length();
        if (len < 2) return "";

        String lastColor = "";
        final StringBuilder styles = EXTRACT_STYLES; styles.setLength(0);
        final StringBuilder effects = EXTRACT_EFFECTS; effects.setLength(0);

        for (int i = 0; i < len - 1; i++) {
            if (text.charAt(i) != FORMATTING_CHAR) continue;
            final char code = Character.toLowerCase(text.charAt(i + 1));

            if (code == 'g' && i + GRADIENT_LENGTH <= len && ColorCodeUtils.isValidSectionX(text, i + 2) && ColorCodeUtils.isValidSectionX(text, i + 2 + SECTION_X_LENGTH)) {
                final StringBuilder cb = EXTRACT_COLOR; cb.setLength(0);
                for (int j = 0; j < GRADIENT_LENGTH; j++) cb.append(text.charAt(i + j));
                lastColor = cb.toString();
                styles.setLength(0);
                effects.setLength(0);
                i += GRADIENT_LENGTH - 1;
                continue;
            }

            if (code == 'x' && i + SECTION_X_LENGTH <= len && ColorCodeUtils.isValidSectionX(text, i)) {
                final StringBuilder cb = EXTRACT_COLOR; cb.setLength(0);
                for (int j = 0; j < SECTION_X_LENGTH; j++) cb.append(text.charAt(i + j));
                lastColor = cb.toString();
                styles.setLength(0);
                effects.setLength(0);
                i += SECTION_X_LENGTH - 1;
                continue;
            }

            if ((code >= '0' && code <= '9') || (code >= 'a' && code <= 'f')) {
                lastColor = ColorCodeUtils.sectionPrefix(code);
                styles.setLength(0);
                effects.setLength(0);
                i++;
            } else if (code == 'r') {
                lastColor = "";
                styles.setLength(0);
                effects.setLength(0);
                i++;
            } else if (code >= 'k' && code <= 'o') {
                styles.append(ColorCodeUtils.sectionPrefix(code));
                i++;
            } else if (code == 'u') {
                if (i + 2 + SECTION_X_LENGTH <= len && ColorCodeUtils.isValidSectionX(text, i + 2)) {
                    final StringBuilder cb = EXTRACT_COLOR;
                    cb.setLength(0);
                    for (int j = 0; j < 2 + SECTION_X_LENGTH; j++) cb.append(text.charAt(i + j));
                    effects.append(cb);
                    i += 1 + SECTION_X_LENGTH;
                } else {
                    effects.append(ColorCodeUtils.sectionPrefix(code));
                    i++;
                }
            } else if (code == 'q' || code == 'z' || code == 'v') {
                effects.append(ColorCodeUtils.sectionPrefix(code));
                i++;
            } else {
                i++;
            }
        }

        if (styles.length() == 0 && effects.length() == 0) return lastColor;
        final StringBuilder result = EXTRACT_RESULT; result.setLength(0);
        result.append(lastColor).append(styles).append(effects);
        return result.toString();
    }

    @ModifyVariable(method = "getStringWidth", at = @At("HEAD"), argsOnly = true, ordinal = 0)
    private String angelica$convertBeforeWidth(String str) {
        return ColorCodeUtils.convertAmpersandToSectionX(str);
    }

    @ModifyVariable(method = "listFormattedStringToWidth", at = @At("HEAD"), argsOnly = true, ordinal = 0)
    private String angelica$convertBeforeListWrap(String str) {
        return ColorCodeUtils.convertAmpersandToSectionX(str);
    }

    @ModifyVariable(method = "wrapFormattedStringToWidth", at = @At("HEAD"), argsOnly = true, ordinal = 0)
    private String angelica$convertBeforeWrap(String str) {
        return ColorCodeUtils.convertAmpersandToSectionX(str);
    }
}
