package com.gtnewhorizons.angelica.mixins.early.angelica.fontrenderer;

import com.gtnewhorizon.gtnhlib.util.font.IFontParameters;
import com.gtnewhorizons.angelica.client.font.BatchingFontRenderer;
import com.gtnewhorizons.angelica.client.font.ColorCodeUtils;
import com.gtnewhorizons.angelica.compat.GTNHLibCompat;
import com.gtnewhorizons.angelica.config.AngelicaConfig;
import com.gtnewhorizons.angelica.glsm.GLStateManager;
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
        angelica$batcher = new BatchingFontRenderer((FontRenderer) (Object) this, this.charWidth, this.colorCode, this.locationFontTexture);
        if (GTNHLibCompat.HAS_TEXT_PREPROCESSOR) {
            GTNHLibCompat.registerPreprocessor();
        }
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
            if (AngelicaConfig.enableAmpersandConversion) {
                text = ColorCodeUtils.convertAmpersandToSectionX(text);
            }

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
            GLStateManager.glColor4f(1.0f, 1.0f, 1.0f, 1.0f);
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

    @Inject(method = "getFormatFromString", at = @At("HEAD"), cancellable = true)
    private static void angelica$getFormatFromString(String text, CallbackInfoReturnable<String> cir) {
        if (!AngelicaConfig.enableRGBColors) return;
        cir.setReturnValue(angelica$extractFormat(text));
    }

    @Unique
    private static String angelica$extractFormat(String text) {
        String lastColor = "";
        StringBuilder styles = new StringBuilder();
        StringBuilder effects = new StringBuilder();

        for (int i = 0; i < text.length() - 1; i++) {
            char ch = text.charAt(i);

            // Handle & ampersand shorthand
            if (ch == '&' && AngelicaConfig.enableAmpersandConversion && i + 1 < text.length()) {
                // &#RRGGBB RGB color (hash + 6 hex digits)
                if (text.charAt(i + 1) == '#' && i + 7 < text.length()) {
                    boolean validHex = true;
                    for (int j = 2; j <= 7; j++) {
                        if (Character.digit(text.charAt(i + j), 16) == -1) {
                            validHex = false;
                            break;
                        }
                    }
                    if (validHex) {
                        StringBuilder sb = new StringBuilder(14);
                        sb.append(angelica$FORMATTING_CHAR).append('x');
                        for (int j = 2; j <= 7; j++) {
                            sb.append(angelica$FORMATTING_CHAR).append(text.charAt(i + j));
                        }
                        lastColor = sb.toString();
                        styles.setLength(0);
                        effects.setLength(0);
                        i += 7;
                        continue;
                    }
                }

                char ampCode = Character.toLowerCase(text.charAt(i + 1));

                // &g&#RRGGBB&#RRGGBB gradient (18 chars)
                if (ampCode == 'g' && i + 17 < text.length()
                    && text.charAt(i + 2) == '&' && text.charAt(i + 3) == '#'
                    && text.charAt(i + 10) == '&' && text.charAt(i + 11) == '#') {
                    boolean valid = true;
                    for (int j = 4; j <= 9 && valid; j++) {
                        if (Character.digit(text.charAt(i + j), 16) == -1) valid = false;
                    }
                    for (int j = 12; j <= 17 && valid; j++) {
                        if (Character.digit(text.charAt(i + j), 16) == -1) valid = false;
                    }
                    if (valid) {
                        StringBuilder sb = new StringBuilder(30);
                        sb.append(angelica$FORMATTING_CHAR).append('g');
                        sb.append(angelica$FORMATTING_CHAR).append('x');
                        for (int j = 4; j <= 9; j++) {
                            sb.append(angelica$FORMATTING_CHAR).append(text.charAt(i + j));
                        }
                        sb.append(angelica$FORMATTING_CHAR).append('x');
                        for (int j = 12; j <= 17; j++) {
                            sb.append(angelica$FORMATTING_CHAR).append(text.charAt(i + j));
                        }
                        lastColor = sb.toString();
                        styles.setLength(0);
                        effects.setLength(0);
                        i += 17;
                        continue;
                    }
                }

                // Single-char & format code
                if ("0123456789abcdefklmnorqzv".indexOf(ampCode) != -1) {
                    if ((ampCode >= '0' && ampCode <= '9') || (ampCode >= 'a' && ampCode <= 'f')) {
                        lastColor = "" + angelica$FORMATTING_CHAR + ampCode;
                        styles.setLength(0);
                        effects.setLength(0);
                    } else if (ampCode == 'r') {
                        lastColor = "";
                        styles.setLength(0);
                        effects.setLength(0);
                    } else if (ampCode >= 'k' && ampCode <= 'o') {
                        styles.append(angelica$FORMATTING_CHAR).append(ampCode);
                    } else if (ampCode == 'q' || ampCode == 'z' || ampCode == 'v' || ampCode == 'g') {
                        effects.append(angelica$FORMATTING_CHAR).append(ampCode);
                    }
                    i++;
                    continue;
                }
            }

            if (ch != angelica$FORMATTING_CHAR) continue;
            char code = Character.toLowerCase(text.charAt(i + 1));

            if (code == 'g' && i + 29 < text.length()) {
                if (angelica$isValidSectionX(text, i + 2) && angelica$isValidSectionX(text, i + 16)) {
                    lastColor = text.substring(i, i + 30);
                    styles.setLength(0);
                    effects.setLength(0);
                    i += 29;
                    continue;
                }
            }

            if (code == 'x' && i + 13 < text.length()) {
                if (angelica$isValidSectionX(text, i)) {
                    lastColor = text.substring(i, i + 14);
                    styles.setLength(0);
                    effects.setLength(0);
                    i += 13;
                    continue;
                }
            }

            if ((code >= '0' && code <= '9') || (code >= 'a' && code <= 'f')) {
                lastColor = text.substring(i, i + 2);
                styles.setLength(0);
                effects.setLength(0);
                i++;
            } else if (code == 'r') {
                lastColor = "";
                styles.setLength(0);
                effects.setLength(0);
                i++;
            } else if (code >= 'k' && code <= 'o') {
                styles.append(angelica$FORMATTING_CHAR).append(code);
                i++;
            } else if (code == 'q' || code == 'z' || code == 'v') {
                effects.append(angelica$FORMATTING_CHAR).append(code);
                i++;
            } else {
                i++;
            }
        }

        return lastColor + effects.toString() + styles.toString();
    }

    @Unique
    private static boolean angelica$isValidSectionX(String text, int start) {
        if (text.charAt(start) != angelica$FORMATTING_CHAR) return false;
        if (Character.toLowerCase(text.charAt(start + 1)) != 'x') return false;
        for (int i = 0; i < 6; i++) {
            int pairStart = start + 2 + i * 2;
            if (text.charAt(pairStart) != angelica$FORMATTING_CHAR) return false;
            if (Character.digit(text.charAt(pairStart + 1), 16) == -1) return false;
        }
        return true;
    }

    @ModifyVariable(method = "getStringWidth", at = @At("HEAD"), argsOnly = true, ordinal = 0)
    private String angelica$convertBeforeWidth(String str) {
        if (AngelicaConfig.enableAmpersandConversion) {
            return ColorCodeUtils.convertAmpersandToSectionX(str);
        }
        return str;
    }

    @ModifyVariable(method = "listFormattedStringToWidth", at = @At("HEAD"), argsOnly = true, ordinal = 0)
    private String angelica$convertBeforeListWrap(String str) {
        if (AngelicaConfig.enableAmpersandConversion) {
            return ColorCodeUtils.convertAmpersandToSectionX(str);
        }
        return str;
    }

    @ModifyVariable(method = "wrapFormattedStringToWidth", at = @At("HEAD"), argsOnly = true, ordinal = 0)
    private String angelica$convertBeforeWrap(String str) {
        if (AngelicaConfig.enableAmpersandConversion) {
            return ColorCodeUtils.convertAmpersandToSectionX(str);
        }
        return str;
    }
}
