package com.gtnewhorizons.angelica.compat.hextext;

import com.gtnewhorizons.angelica.compat.ModStatus;
import kamkeel.hextext.client.render.FontRenderContext;
import kamkeel.hextext.client.render.RenderInstruction;
import kamkeel.hextext.client.render.RenderTextData;
import kamkeel.hextext.client.render.RenderTextProcessor;
import kamkeel.hextext.client.render.TokenHighlightUtils;
import kamkeel.hextext.common.util.ColorCodeUtils;
import kamkeel.hextext.common.util.ColorMath;
import kamkeel.hextext.common.util.TextEffectMath;
import kamkeel.hextext.config.HexTextConfig;
import net.minecraft.client.gui.FontRenderer;

import it.unimi.dsi.fastutil.ints.Int2ObjectMap;
import it.unimi.dsi.fastutil.ints.Int2ObjectOpenHashMap;
import it.unimi.dsi.fastutil.objects.ObjectArrayList;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Iterator;
import java.util.List;
import java.util.Map;

/**
 * Consolidated HexText compatibility helpers for Angelica.
 */
public final class HexTextCompat {

    private HexTextCompat() {
    }

    /**
     * Factory for a HexText-aware token highlighter.
     */
    public static Highlighter createHighlighter() {
        if (!FontRenderContext.isRawTextRendering()) {
            return Highlighter.NOOP;
        }
        try {
            return new DirectHighlighter();
        } catch (Throwable t) {
            ModStatus.LOGGER.warn("Failed to initialize HexText token highlighting", t);
            return Highlighter.NOOP;
        }
    }

    /**
     * Factory for the HexText render bridge used by the colour resolver.
     */
    public static Bridge tryCreateBridge() {
        try {
            return new DirectBridge();
        } catch (Throwable t) {
            ModStatus.LOGGER.warn("Failed to initialize HexText compatibility layer", t);
            return null;
        }
    }

    /**
     * Returns a helper for computing HexText dynamic effect values.
     */
    public static EffectsHelper getEffectsHelper() {
        return EffectsHelperHolder.INSTANCE;
    }

    public static int computeShadowColor(int rgb) {
        return ColorCodeUtils.calculateShadowColor(rgb);
    }

    public interface Highlighter {

        /**
         * Starts a new highlighting session.
         */
        boolean begin(FontRenderer renderer, CharSequence text, float posX, float posY);

        /**
         * Inspects the upcoming character before it is emitted.
         */
        void inspect(CharSequence text, int index, float currentX);

        /**
         * Advances the cursor and finalises any highlights that ended before {@code nextIndex}.
         */
        void advance(int nextIndex, float currentX);

        /**
         * Flushes remaining highlights at the end of the render.
         */
        void finish(int textLength, float currentX);

        /**
         * Returns the highlights computed for the current session.
         */
        List<Highlight> highlights();

        /**
         * Represents a token highlight rectangle.
         */
        final class Highlight {
            private final float x;
            private final float y;
            private final float width;
            private final int color;

            public Highlight(float x, float y, float width, int color) {
                this.x = x;
                this.y = y;
                this.width = width;
                this.color = color;
            }

            public float x() {
                return x;
            }

            public float y() {
                return y;
            }

            public float width() {
                return width;
            }

            public int color() {
                return color;
            }
        }

        /**
         * A no-op implementation used when HexText is not available.
         */
        Highlighter NOOP = new Highlighter() {
            @Override
            public boolean begin(FontRenderer renderer, CharSequence text, float posX, float posY) {
                return false;
            }

            @Override
            public void inspect(CharSequence text, int index, float currentX) {
            }

            @Override
            public void advance(int nextIndex, float currentX) {
            }

            @Override
            public void finish(int textLength, float currentX) {
            }

            @Override
            public List<Highlight> highlights() {
                return Collections.emptyList();
            }
        };
    }

    public interface Bridge {

        PreparedText prepare(CharSequence text, boolean rawMode);
    }

    public interface EffectsHelper {

        int computeRainbowColor(long now, int glyphIndex, int anchorIndex);

        int computeIgniteColor(long now, int baseColor);

        float computeShakeOffset(long now, int glyphIndex);

        default boolean isOperational() {
            return true;
        }
    }

    public static final class PreparedText {
        private final String sanitizedText;
        private final Int2ObjectMap<List<Instruction>> instructions;

        public PreparedText(String sanitizedText, Int2ObjectMap<List<Instruction>> instructions) {
            this.sanitizedText = sanitizedText;
            this.instructions = instructions;
        }

        public String sanitizedText() {
            return sanitizedText;
        }

        public Int2ObjectMap<List<Instruction>> instructions() {
            return instructions;
        }
    }

    public static final class Instruction {

        public enum Type {
            APPLY_RGB,
            PUSH_RGB,
            POP_COLOR,
            RESET_TO_BASE,
            APPLY_VANILLA_COLOR,
            SET_RANDOM,
            SET_BOLD,
            SET_STRIKETHROUGH,
            SET_UNDERLINE,
            SET_ITALIC,
            SET_RAINBOW,
            SET_DINNERBONE,
            SET_IGNITE,
            SET_SHAKE
        }

        private final Type type;
        private final int rgb;
        private final boolean clearStack;
        private final int parameter;
        private final boolean enabled;
        private final boolean resetFormatting;

        public Instruction(Type type, int rgb, boolean clearStack, int parameter, boolean enabled,
                           boolean resetFormatting) {
            this.type = type;
            this.rgb = rgb;
            this.clearStack = clearStack;
            this.parameter = parameter;
            this.enabled = enabled;
            this.resetFormatting = resetFormatting;
        }

        public Type type() {
            return type;
        }

        public int rgb() {
            return rgb;
        }

        public boolean clearStack() {
            return clearStack;
        }

        public int parameter() {
            return parameter;
        }

        public boolean enabled() {
            return enabled;
        }

        public boolean resetFormatting() {
            return resetFormatting;
        }
    }

    private static final class DirectHighlighter implements Highlighter {

        private final List<PendingHighlight> activeHighlights = new ObjectArrayList<>();
        private final List<Highlight> completedHighlights = new ObjectArrayList<>();

        private ColorCodeUtils.FormattingEnvironment formattingEnvironment;
        private float baseY;
        private boolean active;
        private int skip;

        @Override
        public boolean begin(FontRenderer renderer, CharSequence text, float posX, float posY) {
            activeHighlights.clear();
            completedHighlights.clear();
            formattingEnvironment = null;
            skip = 0;

            if (renderer == null || text == null || text.length() == 0) {
                active = false;
                return false;
            }

            formattingEnvironment = ColorCodeUtils.captureFormattingEnvironment(false);
            baseY = posY;
            active = true;
            return true;
        }

        @Override
        public void inspect(CharSequence text, int index, float currentX) {
            if (!active || text == null || index < 0 || index >= text.length()) {
                return;
            }

            if (skip > 0) {
                skip--;
                return;
            }

            int tokenLength = ColorCodeUtils.detectColorCodeLengthIgnoringRaw(text, index, formattingEnvironment);
            if (tokenLength == 0) {
                tokenLength = detectRawAmpersandTokenLength(text, index);
            }
            if (tokenLength <= 0) {
                return;
            }

            char current = text.charAt(index);
            if (current != BatchingConstants.FORMATTING_CHAR) {
                int color = TokenHighlightUtils.getTokenHighlightColor(text, index);
                activeHighlights.add(new PendingHighlight(index + tokenLength, currentX, baseY, color));
            }
            skip = Math.max(tokenLength - 1, 0);
        }

        @Override
        public void advance(int nextIndex, float currentX) {
            if (!active || activeHighlights.isEmpty()) {
                return;
            }

            Iterator<PendingHighlight> iterator = activeHighlights.iterator();
            while (iterator.hasNext()) {
                PendingHighlight highlight = iterator.next();
                if (highlight.endIndex <= nextIndex) {
                    float width = currentX - highlight.startX;
                    if (width > 0.0f) {
                        completedHighlights.add(new Highlight(highlight.startX, highlight.baseY, width, highlight.color));
                    }
                    iterator.remove();
                }
            }
        }

        @Override
        public void finish(int textLength, float currentX) {
            advance(textLength, currentX);
            active = false;
        }

        @Override
        public List<Highlight> highlights() {
            return completedHighlights;
        }

        private static int detectRawAmpersandTokenLength(CharSequence text, int index) {
            if (text == null || index < 0 || index >= text.length() - 1) {
                return 0;
            }
            if (text.charAt(index) != '&') {
                return 0;
            }
            if (text.charAt(index + 1) == '#') {
                return index + 8 <= text.length() && ColorCodeUtils.isValidHexString(text, index + 2) ? 8 : 0;
            }
            return ColorCodeUtils.isFormattingCode(text.charAt(index + 1)) ? 2 : 0;
        }

        private static final class PendingHighlight {
            private final int endIndex;
            private final float startX;
            private final float baseY;
            private final int color;

            private PendingHighlight(int endIndex, float startX, float baseY, int color) {
                this.endIndex = Math.max(endIndex, 0);
                this.startX = startX;
                this.baseY = baseY;
                this.color = color;
            }
        }

        private static final class BatchingConstants {
            private static final char FORMATTING_CHAR = 167;

            private BatchingConstants() {
            }
        }
    }

    private static final class DirectBridge implements Bridge {

        @Override
        public PreparedText prepare(CharSequence text, boolean rawMode) {
            String asString = text == null ? "" : text.toString();
            try {
                RenderTextData data = RenderTextProcessor.prepare(asString, rawMode);
                if (data == null) {
                    return new PreparedText(asString, null);
                }

                String sanitized = data.shouldReplaceText() ? data.getDisplayText() : asString;
                if (sanitized == null) {
                    sanitized = asString;
                }

                Int2ObjectMap<List<Instruction>> instructions = convertInstructions(data.getInstructions());
                return new PreparedText(sanitized, instructions);
            } catch (Throwable t) {
                ModStatus.LOGGER.warn("Failed to query HexText render data", t);
                return new PreparedText(asString, null);
            }
        }

        private Int2ObjectMap<List<Instruction>> convertInstructions(
            Map<Integer, List<RenderInstruction>> source) {
            if (source == null || source.isEmpty()) {
                return null;
            }

            Int2ObjectOpenHashMap<List<Instruction>> converted = new Int2ObjectOpenHashMap<>(source.size());
            for (Map.Entry<Integer, List<RenderInstruction>> entry : source.entrySet()) {
                Integer index = entry.getKey();
                List<RenderInstruction> bucket = entry.getValue();
                if (index == null || bucket == null || bucket.isEmpty()) {
                    continue;
                }

                List<Instruction> mapped = new ArrayList<>(bucket.size());
                for (RenderInstruction instruction : bucket) {
                    Instruction mappedInstruction = mapInstruction(instruction);
                    if (mappedInstruction != null) {
                        mapped.add(mappedInstruction);
                    }
                }

                if (!mapped.isEmpty()) {
                    converted.put(index, Collections.unmodifiableList(mapped));
                }
            }

            return converted.isEmpty() ? null : converted;
        }

        private Instruction mapInstruction(RenderInstruction instruction) {
            if (instruction == null) {
                return null;
            }

            RenderInstruction.Type type = instruction.getType();
            if (type == null) {
                return null;
            }

            Instruction.Type mappedType;
            try {
                mappedType = Instruction.Type.valueOf(type.name());
            } catch (IllegalArgumentException ignored) {
                return null;
            }

            return new Instruction(
                mappedType,
                instruction.getRgb(),
                instruction.shouldClearStack(),
                instruction.getParameter(),
                instruction.isEnabled(),
                instruction.resetsFormatting()
            );
        }
    }

    private static final class EffectsHelperHolder {
        private static final EffectsHelper INSTANCE = create();

        private static EffectsHelper create() {
            try {
                return new DirectEffectsHelper();
            } catch (Throwable t) {
                ModStatus.LOGGER.warn("Failed to initialize HexText dynamic effects", t);
                return NOOP_EFFECTS_HELPER;
            }
        }
    }

    private static final EffectsHelper NOOP_EFFECTS_HELPER = new EffectsHelper() {
        @Override
        public int computeRainbowColor(long now, int glyphIndex, int anchorIndex) {
            return 0;
        }

        @Override
        public int computeIgniteColor(long now, int baseColor) {
            return baseColor & 0x00FFFFFF;
        }

        @Override
        public float computeShakeOffset(long now, int glyphIndex) {
            return 0.0f;
        }

        @Override
        public boolean isOperational() {
            return false;
        }
    };

    private static final class DirectEffectsHelper implements EffectsHelper {

        private static final float RAINBOW_SPREAD = 12.0f;
        private static final float SHAKE_VERTICAL_RANGE = 1.05f;
        private static final long SHAKE_Y_SALT = 0xC6A4A7935BD1E995L;
        private static final float IGNITE_MIN_FACTOR = 0.35f;

        @Override
        public int computeRainbowColor(long now, int glyphIndex, int anchorIndex) {
            return TextEffectMath.computeRainbowColor(now, HexTextConfig.getRainbowSpeed(), glyphIndex, anchorIndex,
                RAINBOW_SPREAD);
        }

        @Override
        public int computeIgniteColor(long now, int baseColor) {
            float brightness = TextEffectMath.computeIgniteBrightness(now, HexTextConfig.getIgniteInterval(),
                IGNITE_MIN_FACTOR);
            return ColorMath.scaleBrightness(baseColor, brightness);
        }

        @Override
        public float computeShakeOffset(long now, int glyphIndex) {
            long seed = TextEffectMath.computeShakeSeed(glyphIndex, now, HexTextConfig.getShakeInterval());
            return TextEffectMath.computeShakeOffset(seed ^ SHAKE_Y_SALT, SHAKE_VERTICAL_RANGE);
        }
    }
}
