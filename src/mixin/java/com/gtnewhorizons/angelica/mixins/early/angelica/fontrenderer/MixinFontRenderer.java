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

import java.util.ArrayDeque;
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
        angelica$batcher = new BatchingFontRenderer((FontRenderer) (Object) this, this.charWidth, this.glyphWidth, this.colorCode, this.locationFontTexture);
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

    /**
     * Intercept getStringWidth to properly handle RGB color codes.
     * Without this, RGB color codes are counted as visible characters,
     * causing text wrapping issues in chat, GUIs, text fields, etc.
     */
    @Inject(method = "getStringWidth(Ljava/lang/String;)I", at = @At("HEAD"), cancellable = true)
    public void angelica$getStringWidthRgbAware(String text, CallbackInfoReturnable<Integer> cir) {
        if (text == null || text.isEmpty()) {
            cir.setReturnValue(0);
            return;
        }
        cir.setReturnValue((int) angelica$getBatcher().getStringWidthWithRgb(text));
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

    /**
     * Intercept sizeStringToWidth to properly handle RGB color codes.
     * This method finds the substring that fits within the given width.
     * Without this, RGB codes can be split across lines in chat/text wrapping.
     */
    @Inject(method = "sizeStringToWidth", at = @At("HEAD"), cancellable = true)
    public void angelica$sizeStringToWidthRgbAware(String str, int maxWidth, CallbackInfoReturnable<String> cir) {
        if (str == null || str.isEmpty()) {
            cir.setReturnValue("");
            return;
        }

        int length = str.length();
        float currentWidth = 0.0f;
        int lastSafePosition = 0;
        boolean isBold = false;

        for (int i = 0; i < length; ) {
            // Check for color codes (RGB or traditional)
            int codeLen = com.gtnewhorizons.angelica.client.font.ColorCodeUtils.detectColorCodeLength(str, i);

            if (codeLen > 0) {
                // This is a color code - skip it atomically (never split)
                // But first check if we need to update bold state
                if (codeLen == 2 && i + 1 < length) {
                    char fmt = Character.toLowerCase(str.charAt(i + 1));
                    if (fmt == 'l') {
                        isBold = true;
                    } else if (fmt == 'r') {
                        isBold = false;
                    } else if ((fmt >= '0' && fmt <= '9') || (fmt >= 'a' && fmt <= 'f')) {
                        isBold = false; // Color codes reset bold
                    }
                }

                i += codeLen;
                lastSafePosition = i; // Can safely break after a complete color code
                continue;
            }

            // Regular character
            char c = str.charAt(i);
            float charWidth = angelica$getBatcher().getCharWidthFine(c);

            if (charWidth < 0) {
                // Formatting character outside of detected codes
                charWidth = 0;
            }

            float nextWidth = currentWidth + charWidth;
            if (isBold && charWidth > 0) {
                nextWidth += angelica$getBatcher().getShadowOffset();
            }

            if (nextWidth > maxWidth) {
                // Would exceed width - return string up to last safe position
                cir.setReturnValue(str.substring(0, lastSafePosition));
                return;
            }

            currentWidth = nextWidth;
            i++;
            lastSafePosition = i;
        }

        // Entire string fits
        cir.setReturnValue(str);
    }

    /**
     * Intercept trimStringToWidth to properly handle RGB color codes.
     * Variant with reverse parameter for trimming from the end.
     */
    @Inject(method = "trimStringToWidth(Ljava/lang/String;IZ)Ljava/lang/String;", at = @At("HEAD"), cancellable = true)
    public void angelica$trimStringToWidthRgbAware(String text, int width, boolean reverse, CallbackInfoReturnable<String> cir) {
        if (text == null || text.isEmpty()) {
            cir.setReturnValue("");
            return;
        }

        if (!reverse) {
            // Forward direction - reuse sizeStringToWidth logic
            angelica$sizeStringToWidthRgbAware(text, width, cir);
            return;
        }

        // Reverse direction - trim from the end
        int length = text.length();
        float currentWidth = 0.0f;
        int firstSafePosition = length;
        boolean isBold = false;

        for (int i = length - 1; i >= 0; ) {
            // Check for color codes (need to scan backwards carefully)
            // For reverse, we'll be less aggressive and just avoid breaking simple cases
            char c = text.charAt(i);

            // Check if we're at the end of an RGB code
            if (i >= 6 && (c == '5' || c == 'F' || c == 'f' || (c >= '0' && c <= '9') || (c >= 'A' && c <= 'F') || (c >= 'a' && c <= 'f'))) {
                // Might be end of &RRGGBB, check backwards
                if (i >= 6 && text.charAt(i - 6) == '&') {
                    boolean validHex = true;
                    for (int j = i - 5; j <= i; j++) {
                        char hexChar = text.charAt(j);
                        if (!com.gtnewhorizons.angelica.client.font.ColorCodeUtils.isValidHexChar(hexChar)) {
                            validHex = false;
                            break;
                        }
                    }
                    if (validHex) {
                        // Skip the entire &RRGGBB
                        i -= 7;
                        firstSafePosition = i + 1;
                        continue;
                    }
                }
            }

            float charWidth = angelica$getBatcher().getCharWidthFine(c);

            if (charWidth < 0) {
                charWidth = 0;
            }

            float nextWidth = currentWidth + charWidth;
            if (isBold && charWidth > 0) {
                nextWidth += angelica$getBatcher().getShadowOffset();
            }

            if (nextWidth > width) {
                // Would exceed width - return string from first safe position
                cir.setReturnValue(text.substring(firstSafePosition));
                return;
            }

            currentWidth = nextWidth;
            i--;
            firstSafePosition = i + 1;
        }

        // Entire string fits
        cir.setReturnValue(text);
    }

    /**
     * Intercept listFormattedStringToWidth to properly handle RGB color codes.
     * This is the CRITICAL method for chat wrapping - it splits long strings into lines
     * and prepends formatting codes to each line. Without this, RGB codes get mangled.
     *
     * This follows vanilla's recursive algorithm but with RGB awareness.
     */
    @Inject(method = "listFormattedStringToWidth", at = @At("HEAD"), cancellable = true)
    public void angelica$listFormattedStringToWidthRgbAware(String str, int wrapWidth, CallbackInfoReturnable<java.util.List> cir) {
        if (str == null || str.isEmpty()) {
            cir.setReturnValue(java.util.Collections.emptyList());
            return;
        }

        String wrapped = angelica$wrapFormattedStringToWidth(str, wrapWidth);
        String[] lines = wrapped.split("\n");
        java.util.List<String> result = new java.util.ArrayList<>(lines.length);
        java.util.Collections.addAll(result, lines);
        cir.setReturnValue(result);
    }

    @Inject(method = "wrapFormattedStringToWidth", at = @At("HEAD"), cancellable = true)
    public void angelica$wrapFormattedStringToWidthRgbAwareEntry(String str, int wrapWidth, CallbackInfoReturnable<String> cir) {
        if (str == null || str.isEmpty()) {
            cir.setReturnValue("");
            return;
        }

        cir.setReturnValue(angelica$wrapFormattedStringToWidth(str, wrapWidth));
    }

    /**
     * RGB-aware version of vanilla's wrapFormattedStringToWidth.
     * Uses recursion like vanilla, but handles RGB color codes properly.
     */
    @Unique
    private String angelica$wrapFormattedStringToWidth(String str, int wrapWidth) {
        // Use our RGB-aware sizeStringToWidth via mixin callback
        CallbackInfoReturnable<String> cir = new CallbackInfoReturnable<>("angelica$sizeStringToWidth", true);
        angelica$sizeStringToWidthRgbAware(str, wrapWidth, cir);
        String sized = cir.getReturnValue();
        int breakPoint = sized.length();

        if (str.length() <= breakPoint) {
            // Everything fits
            return str;
        } else {
            // Need to wrap
            String firstPart = str.substring(0, breakPoint);
            char charAtBreak = str.charAt(breakPoint);
            boolean isSpaceOrNewline = charAtBreak == ' ' || charAtBreak == '\n';

            // Extract formatting codes from first part and prepend to remainder
            String formattingCodes = angelica$extractFormatFromString(firstPart);
            String remainder = formattingCodes + str.substring(breakPoint + (isSpaceOrNewline ? 1 : 0));

            // Recurse on remainder
            return firstPart + "\n" + angelica$wrapFormattedStringToWidth(remainder, wrapWidth);
        }
    }

    /**
     * RGB-aware version of vanilla's getFormatFromString.
     * Extracts active formatting codes from a string (RGB colors + traditional formatting).
     */
    @Unique
    private static String angelica$extractFormatFromString(String str) {
        String currentColorCode = null;
        StringBuilder styleCodes = new StringBuilder();
        ArrayDeque<String> colorStack = new ArrayDeque<>();

        for (int i = 0; i < str.length(); ) {
            int codeLen = com.gtnewhorizons.angelica.client.font.ColorCodeUtils.detectColorCodeLengthIgnoringRaw(str, i);

            if (codeLen > 0) {
                char firstChar = str.charAt(i);
                String code = str.substring(i, i + codeLen);

                if (codeLen == 7 && firstChar == '&') {
                    // &RRGGBB - inline RGB colour, clears prior colour stack
                    currentColorCode = code;
                    colorStack.clear();
                    styleCodes.setLength(0);
                } else if (codeLen == 9 && firstChar == '<') {
                    // <RRGGBB> - push current colour (if any) then apply new colour
                    colorStack.push(currentColorCode);
                    currentColorCode = code;
                    styleCodes.setLength(0);
                } else if (codeLen == 10 && firstChar == '<') {
                    // </RRGGBB> - pop back to previous colour (or none)
                    currentColorCode = colorStack.isEmpty() ? null : colorStack.pop();
                    styleCodes.setLength(0);
                } else if (codeLen == 2) {
                    // Traditional formatting code
                    char fmt = Character.toLowerCase(str.charAt(i + 1));

                    if ((fmt >= '0' && fmt <= '9') || (fmt >= 'a' && fmt <= 'f')) {
                        currentColorCode = code;
                        colorStack.clear();
                        styleCodes.setLength(0);
                    } else if (fmt == 'r') {
                        currentColorCode = null;
                        colorStack.clear();
                        styleCodes.setLength(0);
                    } else if (fmt == 'l' || fmt == 'o' || fmt == 'n' || fmt == 'm' || fmt == 'k') {
                        styleCodes.append(code);
                    }
                }

                i += codeLen;
                continue;
            }

            i++;
        }

        StringBuilder result = new StringBuilder();
        if (currentColorCode != null) {
            result.append(currentColorCode);
        }
        if (styleCodes.length() > 0) {
            result.append(styleCodes);
        }

        return result.toString();
    }

    @Inject(method = "getFormatFromString", at = @At("HEAD"), cancellable = true)
    private static void angelica$getFormatFromStringRgbAware(String text, CallbackInfoReturnable<String> cir) {
        if (text == null || text.isEmpty()) {
            cir.setReturnValue("");
            return;
        }

        cir.setReturnValue(angelica$extractFormatFromString(text));
    }
}
