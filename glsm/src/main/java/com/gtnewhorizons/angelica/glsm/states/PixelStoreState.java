package com.gtnewhorizons.angelica.glsm.states;

import org.lwjgl.opengl.GL11;
import org.lwjgl.opengl.GL12;

import static com.gtnewhorizons.angelica.glsm.backend.BackendManager.RENDER_BACKEND;

public record PixelStoreState(
    int alignment,
    int rowLength,
    int skipRows,
    int skipPixels,
    int imageHeight,
    int skipImages,
    boolean swapBytes,
    boolean lsbFirst
) {
    public static final PixelStoreState DEFAULT = new PixelStoreState(4, 0, 0, 0, 0, 0, false, false);

    public boolean isDefault() {
        return this.equals(DEFAULT);
    }

    public static boolean isPack(int pname) {
        return switch (pname) {
            case GL11.GL_PACK_ALIGNMENT, GL11.GL_PACK_ROW_LENGTH, GL11.GL_PACK_SKIP_ROWS, GL11.GL_PACK_SKIP_PIXELS,
                 GL12.GL_PACK_IMAGE_HEIGHT, GL12.GL_PACK_SKIP_IMAGES, GL11.GL_PACK_SWAP_BYTES, GL11.GL_PACK_LSB_FIRST -> true;
            default -> false;
        };
    }

    public PixelStoreState with(int pname, int param) {
        return switch (pname) {
            case GL11.GL_UNPACK_ALIGNMENT, GL11.GL_PACK_ALIGNMENT -> (param == alignment || (param != 1 && param != 2 && param != 4 && param != 8)) ? this : new PixelStoreState(param, rowLength, skipRows, skipPixels, imageHeight, skipImages, swapBytes, lsbFirst);
            case GL11.GL_UNPACK_ROW_LENGTH, GL11.GL_PACK_ROW_LENGTH -> (param == rowLength || param < 0) ? this : new PixelStoreState(alignment, param, skipRows, skipPixels, imageHeight, skipImages, swapBytes, lsbFirst);
            case GL11.GL_UNPACK_SKIP_ROWS, GL11.GL_PACK_SKIP_ROWS -> (param == skipRows || param < 0) ? this : new PixelStoreState(alignment, rowLength, param, skipPixels, imageHeight, skipImages, swapBytes, lsbFirst);
            case GL11.GL_UNPACK_SKIP_PIXELS, GL11.GL_PACK_SKIP_PIXELS -> (param == skipPixels || param < 0) ? this : new PixelStoreState(alignment, rowLength, skipRows, param, imageHeight, skipImages, swapBytes, lsbFirst);
            case GL12.GL_UNPACK_IMAGE_HEIGHT, GL12.GL_PACK_IMAGE_HEIGHT -> (param == imageHeight || param < 0) ? this : new PixelStoreState(alignment, rowLength, skipRows, skipPixels, param, skipImages, swapBytes, lsbFirst);
            case GL12.GL_UNPACK_SKIP_IMAGES, GL12.GL_PACK_SKIP_IMAGES -> (param == skipImages || param < 0) ? this : new PixelStoreState(alignment, rowLength, skipRows, skipPixels, imageHeight, param, swapBytes, lsbFirst);
            case GL11.GL_UNPACK_SWAP_BYTES, GL11.GL_PACK_SWAP_BYTES -> (param != 0) == swapBytes ? this : new PixelStoreState(alignment, rowLength, skipRows, skipPixels, imageHeight, skipImages, param != 0, lsbFirst);
            case GL11.GL_UNPACK_LSB_FIRST, GL11.GL_PACK_LSB_FIRST -> (param != 0) == lsbFirst ? this : new PixelStoreState(alignment, rowLength, skipRows, skipPixels, imageHeight, skipImages, swapBytes, param != 0);
            default -> this;
        };
    }

    public static void applyDiff(PixelStoreState from, PixelStoreState to, boolean pack) {
        if (from == to) return;
        if (from.alignment != to.alignment) RENDER_BACKEND.pixelStorei(pack ? GL11.GL_PACK_ALIGNMENT : GL11.GL_UNPACK_ALIGNMENT, to.alignment);
        if (from.rowLength != to.rowLength) RENDER_BACKEND.pixelStorei(pack ? GL11.GL_PACK_ROW_LENGTH : GL11.GL_UNPACK_ROW_LENGTH, to.rowLength);
        if (from.skipRows != to.skipRows) RENDER_BACKEND.pixelStorei(pack ? GL11.GL_PACK_SKIP_ROWS : GL11.GL_UNPACK_SKIP_ROWS, to.skipRows);
        if (from.skipPixels != to.skipPixels) RENDER_BACKEND.pixelStorei(pack ? GL11.GL_PACK_SKIP_PIXELS : GL11.GL_UNPACK_SKIP_PIXELS, to.skipPixels);
        if (from.imageHeight != to.imageHeight) RENDER_BACKEND.pixelStorei(pack ? GL12.GL_PACK_IMAGE_HEIGHT : GL12.GL_UNPACK_IMAGE_HEIGHT, to.imageHeight);
        if (from.skipImages != to.skipImages) RENDER_BACKEND.pixelStorei(pack ? GL12.GL_PACK_SKIP_IMAGES : GL12.GL_UNPACK_SKIP_IMAGES, to.skipImages);
        if (from.swapBytes != to.swapBytes) RENDER_BACKEND.pixelStorei(pack ? GL11.GL_PACK_SWAP_BYTES : GL11.GL_UNPACK_SWAP_BYTES, to.swapBytes ? 1 : 0);
        if (from.lsbFirst != to.lsbFirst) RENDER_BACKEND.pixelStorei(pack ? GL11.GL_PACK_LSB_FIRST : GL11.GL_UNPACK_LSB_FIRST, to.lsbFirst ? 1 : 0);
    }
}
