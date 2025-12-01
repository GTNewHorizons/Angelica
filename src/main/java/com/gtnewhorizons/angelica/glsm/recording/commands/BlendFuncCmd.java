package com.gtnewhorizons.angelica.glsm.recording.commands;

import com.github.bsideup.jabel.Desugar;
import com.gtnewhorizons.angelica.glsm.GLDebug;
import com.gtnewhorizons.angelica.glsm.GLStateManager;
import org.jetbrains.annotations.NotNull;

/**
 * Command: glBlendFunc(src, dst) or glBlendFuncSeparate(srcRGB, dstRGB, srcAlpha, dstAlpha)
 * Sets the blend function for RGB and alpha channels.
 */
@Desugar
public record BlendFuncCmd(int srcRgb, int dstRgb, int srcAlpha, int dstAlpha) implements DisplayListCommand {

    /**
     * Constructor for simple glBlendFunc(src, dst) - same for RGB and alpha.
     */
    public BlendFuncCmd(int src, int dst) {
        this(src, dst, src, dst);
    }

    @Override
    public void execute() {
        if (srcRgb == srcAlpha && dstRgb == dstAlpha) {
            // Simple blend func
            GLStateManager.glBlendFunc(srcRgb, dstRgb);
        } else {
            // Separate blend func
            GLStateManager.tryBlendFuncSeparate(srcRgb, dstRgb, srcAlpha, dstAlpha);
        }
    }

    @Override
    public @NotNull String toString() {
        if (srcRgb == srcAlpha && dstRgb == dstAlpha) {
            return String.format("BlendFunc(%s, %s)", GLDebug.getBlendFactorName(srcRgb), GLDebug.getBlendFactorName(dstRgb));
        } else {
            return String.format("BlendFuncSeparate(%s, %s, %s, %s)",
                GLDebug.getBlendFactorName(srcRgb), GLDebug.getBlendFactorName(dstRgb),
                GLDebug.getBlendFactorName(srcAlpha), GLDebug.getBlendFactorName(dstAlpha));
        }
    }
}
