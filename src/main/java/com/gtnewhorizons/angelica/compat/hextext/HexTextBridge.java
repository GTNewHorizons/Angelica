package com.gtnewhorizons.angelica.compat.hextext;

import it.unimi.dsi.fastutil.ints.Int2ObjectMap;

import java.util.List;

public interface HexTextBridge {

    PreparedText prepare(CharSequence text);

    final class PreparedText {
        private final String sanitizedText;
        private final Int2ObjectMap<List<HexTextInstruction>> instructions;

        public PreparedText(String sanitizedText, Int2ObjectMap<List<HexTextInstruction>> instructions) {
            this.sanitizedText = sanitizedText;
            this.instructions = instructions;
        }

        public String sanitizedText() {
            return sanitizedText;
        }

        public Int2ObjectMap<List<HexTextInstruction>> instructions() {
            return instructions;
        }
    }
}
