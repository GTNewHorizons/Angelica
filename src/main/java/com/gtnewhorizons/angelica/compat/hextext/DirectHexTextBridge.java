package com.gtnewhorizons.angelica.compat.hextext;

import com.gtnewhorizons.angelica.compat.ModStatus;
import it.unimi.dsi.fastutil.ints.Int2ObjectMap;
import it.unimi.dsi.fastutil.ints.Int2ObjectOpenHashMap;
import kamkeel.hextext.client.render.RenderInstruction;
import kamkeel.hextext.client.render.RenderTextData;
import kamkeel.hextext.client.render.RenderTextProcessor;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;

final class DirectHexTextBridge implements HexTextBridge {

    @Override
    public PreparedText prepare(CharSequence text) {
        String asString = text == null ? "" : text.toString();
        try {
            RenderTextData data = RenderTextProcessor.prepare(asString, false);
            if (data == null) {
                return new PreparedText(asString, null);
            }

            String sanitized = data.shouldReplaceText() ? data.getDisplayText() : asString;
            if (sanitized == null) {
                sanitized = asString;
            }

            Int2ObjectMap<List<HexTextInstruction>> instructions = convertInstructions(data.getInstructions());
            return new PreparedText(sanitized, instructions);
        } catch (Throwable t) {
            ModStatus.LOGGER.warn("Failed to query HexText render data", t);
            return new PreparedText(asString, null);
        }
    }

    private Int2ObjectMap<List<HexTextInstruction>> convertInstructions(
        Map<Integer, List<RenderInstruction>> source) {
        if (source == null || source.isEmpty()) {
            return null;
        }

        Int2ObjectOpenHashMap<List<HexTextInstruction>> converted = new Int2ObjectOpenHashMap<>(source.size());
        for (Map.Entry<Integer, List<RenderInstruction>> entry : source.entrySet()) {
            Integer index = entry.getKey();
            List<RenderInstruction> bucket = entry.getValue();
            if (index == null || bucket == null || bucket.isEmpty()) {
                continue;
            }

            List<HexTextInstruction> mapped = new ArrayList<>(bucket.size());
            for (RenderInstruction instruction : bucket) {
                HexTextInstruction mappedInstruction = mapInstruction(instruction);
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

    private HexTextInstruction mapInstruction(RenderInstruction instruction) {
        if (instruction == null) {
            return null;
        }

        RenderInstruction.Type type = instruction.getType();
        if (type == null) {
            return null;
        }

        HexTextInstruction.Type mappedType;
        try {
            mappedType = HexTextInstruction.Type.valueOf(type.name());
        } catch (IllegalArgumentException ignored) {
            return null;
        }

        return new HexTextInstruction(
            mappedType,
            instruction.getRgb(),
            instruction.shouldClearStack(),
            instruction.getParameter(),
            instruction.isEnabled(),
            instruction.resetsFormatting()
        );
    }
}
