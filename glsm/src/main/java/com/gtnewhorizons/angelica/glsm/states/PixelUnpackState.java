package com.gtnewhorizons.angelica.glsm.states;

import org.lwjgl.opengl.GL11;
import org.lwjgl.opengl.GL12;

import static com.gtnewhorizons.angelica.glsm.backend.BackendManager.RENDER_BACKEND;

/**
 * Immutable client-side pixel unpack state (glPixelStore UNPACK_* params)
 */
public record PixelUnpackState(
    int alignment,
    int rowLength,
    int skipRows,
    int skipPixels,
    int imageHeight,
    int skipImages,
    boolean swapBytes,
    boolean lsbFirst
) {
    public static final PixelUnpackState DEFAULT = new PixelUnpackState(4, 0, 0, 0, 0, 0, false, false);

    public boolean isDefault() {
        return this.equals(DEFAULT);
    }

    public PixelUnpackState with(int pname, int param) {
        return switch (pname) {
            case GL11.GL_UNPACK_ALIGNMENT -> (param == 1 || param == 2 || param == 4 || param == 8) ? new PixelUnpackState(param, rowLength, skipRows, skipPixels, imageHeight, skipImages, swapBytes, lsbFirst) : this;
            case GL11.GL_UNPACK_ROW_LENGTH -> param >= 0 ? new PixelUnpackState(alignment, param, skipRows, skipPixels, imageHeight, skipImages, swapBytes, lsbFirst) : this;
            case GL11.GL_UNPACK_SKIP_ROWS -> param >= 0 ? new PixelUnpackState(alignment, rowLength, param, skipPixels, imageHeight, skipImages, swapBytes, lsbFirst) : this;
            case GL11.GL_UNPACK_SKIP_PIXELS -> param >= 0 ? new PixelUnpackState(alignment, rowLength, skipRows, param, imageHeight, skipImages, swapBytes, lsbFirst) : this;
            case GL12.GL_UNPACK_IMAGE_HEIGHT -> param >= 0 ? new PixelUnpackState(alignment, rowLength, skipRows, skipPixels, param, skipImages, swapBytes, lsbFirst) : this;
            case GL12.GL_UNPACK_SKIP_IMAGES -> param >= 0 ? new PixelUnpackState(alignment, rowLength, skipRows, skipPixels, imageHeight, param, swapBytes, lsbFirst) : this;
            case GL11.GL_UNPACK_SWAP_BYTES -> new PixelUnpackState(alignment, rowLength, skipRows, skipPixels, imageHeight, skipImages, param != 0, lsbFirst);
            case GL11.GL_UNPACK_LSB_FIRST -> new PixelUnpackState(alignment, rowLength, skipRows, skipPixels, imageHeight, skipImages, swapBytes, param != 0);
            default -> this;
        };
    }

    public static void applyDiff(PixelUnpackState from, PixelUnpackState to) {
        if (from.alignment != to.alignment) RENDER_BACKEND.pixelStorei(GL11.GL_UNPACK_ALIGNMENT, to.alignment);
        if (from.rowLength != to.rowLength) RENDER_BACKEND.pixelStorei(GL11.GL_UNPACK_ROW_LENGTH, to.rowLength);
        if (from.skipRows != to.skipRows) RENDER_BACKEND.pixelStorei(GL11.GL_UNPACK_SKIP_ROWS, to.skipRows);
        if (from.skipPixels != to.skipPixels) RENDER_BACKEND.pixelStorei(GL11.GL_UNPACK_SKIP_PIXELS, to.skipPixels);
        if (from.imageHeight != to.imageHeight) RENDER_BACKEND.pixelStorei(GL12.GL_UNPACK_IMAGE_HEIGHT, to.imageHeight);
        if (from.skipImages != to.skipImages) RENDER_BACKEND.pixelStorei(GL12.GL_UNPACK_SKIP_IMAGES, to.skipImages);
        if (from.swapBytes != to.swapBytes) RENDER_BACKEND.pixelStorei(GL11.GL_UNPACK_SWAP_BYTES, to.swapBytes ? 1 : 0);
        if (from.lsbFirst != to.lsbFirst) RENDER_BACKEND.pixelStorei(GL11.GL_UNPACK_LSB_FIRST, to.lsbFirst ? 1 : 0);
    }
}
