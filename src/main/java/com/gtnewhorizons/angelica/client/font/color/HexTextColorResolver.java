package com.gtnewhorizons.angelica.client.font.color;

import com.gtnewhorizons.angelica.compat.hextext.HexTextBridge;
import com.gtnewhorizons.angelica.compat.hextext.HexTextInstruction;
import com.gtnewhorizons.angelica.config.FontConfig;
import it.unimi.dsi.fastutil.ints.Int2ObjectMap;

import java.util.ArrayDeque;
import java.util.Deque;
import java.util.List;

import static com.gtnewhorizons.angelica.client.font.BatchingFontRenderer.FORMATTING_CHAR;

final class HexTextColorResolver implements AngelicaColorResolver {

    private final int[] vanillaPalette;
    private final HexTextBridge bridge;

    HexTextColorResolver(int[] vanillaPalette, HexTextBridge bridge) {
        this.vanillaPalette = vanillaPalette;
        this.bridge = bridge;
    }

    @Override
    public ResolvedText resolve(CharSequence text, int start, int end, int baseColor, int baseShadowColor) {
        if (text == null || start >= end) {
            return ResolvedText.builder(0).build();
        }

        int safeStart = Math.max(0, start);
        int safeEnd = Math.min(text.length(), end);
        if (safeStart >= safeEnd) {
            return ResolvedText.builder(0).build();
        }

        String segment = text.subSequence(safeStart, safeEnd).toString();
        HexTextBridge.PreparedText prepared = bridge.prepare(segment);
        String sanitized = prepared.sanitizedText() != null ? prepared.sanitizedText() : segment;
        Int2ObjectMap<List<HexTextInstruction>> instructions = prepared.instructions();

        ResolvedText.Builder builder = ResolvedText.builder(sanitized.length());
        FormattingState state = new FormattingState(baseColor, baseShadowColor);

        for (int index = 0; index < sanitized.length(); index++) {
            if (instructions != null) {
                List<HexTextInstruction> bucket = instructions.get(index);
                if (bucket != null) {
                    applyInstructions(state, bucket);
                }
            }

            char chr = sanitized.charAt(index);
            if (chr == FORMATTING_CHAR && index + 1 < sanitized.length()) {
                char fmtCode = Character.toLowerCase(sanitized.charAt(index + 1));
                index++;
                applyVanillaFormatting(state, fmtCode);
                continue;
            }

            builder.append(chr, state.color, state.shadowColor, state.bold, state.italic, state.underline,
                state.strikethrough, state.random);
        }

        return builder.build();
    }

    private void applyInstructions(FormattingState state, List<HexTextInstruction> instructions) {
        for (HexTextInstruction instruction : instructions) {
            switch (instruction.type()) {
                case APPLY_RGB:
                    applyRgb(state, instruction.rgb(), instruction.clearStack());
                    if (instruction.resetFormatting()) {
                        state.resetFormattingFlags();
                    }
                    break;
                case PUSH_RGB:
                    pushRgb(state, instruction.rgb());
                    if (instruction.resetFormatting()) {
                        state.resetFormattingFlags();
                    }
                    break;
                case POP_COLOR:
                    state.popColor();
                    if (instruction.resetFormatting()) {
                        state.resetFormattingFlags();
                    }
                    break;
                case RESET_TO_BASE:
                    state.reset();
                    break;
                case APPLY_VANILLA_COLOR:
                    applyVanillaPalette(state, instruction.parameter());
                    if (instruction.resetFormatting()) {
                        state.resetFormattingFlags();
                    }
                    break;
                case SET_RANDOM:
                    state.random = instruction.enabled();
                    break;
                case SET_BOLD:
                    state.bold = instruction.enabled();
                    break;
                case SET_STRIKETHROUGH:
                    state.strikethrough = instruction.enabled();
                    break;
                case SET_UNDERLINE:
                    state.underline = instruction.enabled();
                    break;
                case SET_ITALIC:
                    state.italic = instruction.enabled();
                    break;
                default:
                    // Dynamic effects such as rainbow, ignite, dinnerbone and shake are not handled here because
                    // the batching renderer operates on pre-baked vertices. We intentionally ignore them.
                    break;
            }
        }
    }

    private void applyRgb(FormattingState state, int rgb, boolean clearStack) {
        if (!FontConfig.preferHexTextRGB) {
            if (clearStack) {
                state.clearStacks();
            }
            return;
        }
        if (clearStack) {
            state.clearStacks();
        }
        state.color = (state.alphaMask | (rgb & 0x00FFFFFF));
        if (FontConfig.inheritAngelicaShadow) {
            state.shadowColor = (state.shadowAlphaMask | (((rgb & 0xFCFCFC) >> 2) & 0x00FFFFFF));
        } else {
            state.shadowColor = (state.shadowAlphaMask | (rgb & 0x00FFFFFF));
        }
    }

    private void pushRgb(FormattingState state, int rgb) {
        state.pushColor();
        if (FontConfig.preferHexTextRGB) {
            state.color = (state.alphaMask | (rgb & 0x00FFFFFF));
            if (FontConfig.inheritAngelicaShadow) {
                state.shadowColor = (state.shadowAlphaMask | (((rgb & 0xFCFCFC) >> 2) & 0x00FFFFFF));
            } else {
                state.shadowColor = (state.shadowAlphaMask | (rgb & 0x00FFFFFF));
            }
        }
    }

    private void applyVanillaPalette(FormattingState state, int paletteIndex) {
        state.clearStacks();
        int clamped = Math.max(0, Math.min(15, paletteIndex));
        if (vanillaPalette != null && vanillaPalette.length >= 32) {
            int main = vanillaPalette[clamped];
            state.color = (state.alphaMask | (main & 0x00FFFFFF));
            int shadow = vanillaPalette[clamped + 16];
            state.shadowColor = (state.shadowAlphaMask | (shadow & 0x00FFFFFF));
        } else {
            state.color = state.alphaMask | (0xFFFFFF & vanillaFallback(clamped));
            state.shadowColor = state.shadowAlphaMask | (vanillaFallback(clamped) & 0x00FFFFFF);
        }
        state.resetFormattingFlags();
    }

    private int vanillaFallback(int index) {
        int rgb = 0xFFFFFF;
        switch (index) {
            case 0:
                rgb = 0x000000;
                break;
            case 1:
                rgb = 0x0000AA;
                break;
            case 2:
                rgb = 0x00AA00;
                break;
            case 3:
                rgb = 0x00AAAA;
                break;
            case 4:
                rgb = 0xAA0000;
                break;
            case 5:
                rgb = 0xAA00AA;
                break;
            case 6:
                rgb = 0xFFAA00;
                break;
            case 7:
                rgb = 0xAAAAAA;
                break;
            case 8:
                rgb = 0x555555;
                break;
            case 9:
                rgb = 0x5555FF;
                break;
            case 10:
                rgb = 0x55FF55;
                break;
            case 11:
                rgb = 0x55FFFF;
                break;
            case 12:
                rgb = 0xFF5555;
                break;
            case 13:
                rgb = 0xFF55FF;
                break;
            case 14:
                rgb = 0xFFFF55;
                break;
            case 15:
                rgb = 0xFFFFFF;
                break;
            default:
                break;
        }
        return rgb;
    }

    private void applyVanillaFormatting(FormattingState state, char fmtCode) {
        if (charInRange(fmtCode, '0', '9') || charInRange(fmtCode, 'a', 'f')) {
            applyVanillaPalette(state, charInRange(fmtCode, '0', '9') ? (fmtCode - '0') : (fmtCode - 'a' + 10));
        } else if (fmtCode == 'k') {
            state.random = true;
        } else if (fmtCode == 'l') {
            state.bold = true;
        } else if (fmtCode == 'm') {
            state.strikethrough = true;
        } else if (fmtCode == 'n') {
            state.underline = true;
        } else if (fmtCode == 'o') {
            state.italic = true;
        } else if (fmtCode == 'r') {
            state.reset();
        }
    }

    private static boolean charInRange(char what, char fromInclusive, char toInclusive) {
        return what >= fromInclusive && what <= toInclusive;
    }

    private static final class FormattingState {
        final int baseColor;
        final int baseShadowColor;
        final int alphaMask;
        final int shadowAlphaMask;
        final Deque<Integer> colorStack = new ArrayDeque<>();
        final Deque<Integer> shadowStack = new ArrayDeque<>();

        int color;
        int shadowColor;
        boolean italic;
        boolean random;
        boolean bold;
        boolean strikethrough;
        boolean underline;

        FormattingState(int baseColor, int baseShadowColor) {
            this.baseColor = baseColor;
            this.baseShadowColor = baseShadowColor;
            this.alphaMask = baseColor & 0xFF000000;
            this.shadowAlphaMask = baseShadowColor & 0xFF000000;
            this.color = baseColor;
            this.shadowColor = baseShadowColor;
        }

        void reset() {
            clearStacks();
            color = baseColor;
            shadowColor = baseShadowColor;
            resetFormattingFlags();
        }

        void resetFormattingFlags() {
            italic = false;
            random = false;
            bold = false;
            strikethrough = false;
            underline = false;
        }

        void clearStacks() {
            colorStack.clear();
            shadowStack.clear();
        }

        void pushColor() {
            colorStack.push(color);
            shadowStack.push(shadowColor);
        }

        void popColor() {
            color = colorStack.isEmpty() ? baseColor : colorStack.pop();
            shadowColor = shadowStack.isEmpty() ? baseShadowColor : shadowStack.pop();
        }
    }
}
