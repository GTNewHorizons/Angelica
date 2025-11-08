package com.gtnewhorizons.angelica.compat.hextext.render;

import com.gtnewhorizons.angelica.compat.ModStatus;
import com.gtnewhorizons.angelica.compat.hextext.render.CompatRenderInstruction.Type;
import kamkeel.hextext.api.rendering.RenderDirective;
import kamkeel.hextext.api.rendering.RenderDirective.InstructionType;

/**
 * Converts HexText {@link RenderDirective} instances into Angelica-friendly instruction records.
 */
final class RenderDirectiveAdapter {

    private RenderDirectiveAdapter() {}

    static CompatRenderInstruction adapt(RenderDirective directive) {
        if (directive == null) {
            return null;
        }

        Type type = mapType(directive.getType());
        if (type == null) {
            return null;
        }

        return new CompatRenderInstruction(
            type,
            directive.getRgb(),
            directive.shouldClearStack(),
            directive.getParameter(),
            directive.isEnabled(),
            directive.resetsFormatting()
        );
    }

    private static Type mapType(InstructionType typeObject) {
        if (typeObject == null) {
            return null;
        }

        String fallback = typeObject.toString();
        try {
            return Type.valueOf(fallback);
        } catch (IllegalArgumentException iae) {
            ModStatus.LOGGER.warn("Unsupported HexText render directive: {}", fallback);
            return null;
        }
    }
}
