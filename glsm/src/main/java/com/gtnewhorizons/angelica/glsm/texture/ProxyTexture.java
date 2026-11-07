package com.gtnewhorizons.angelica.glsm.texture;

import org.lwjgl.opengl.GL11;
import org.lwjgl.opengl.GL12;
import org.lwjgl.opengl.GL13;
import org.lwjgl.opengl.GL30;
import org.lwjgl.opengl.GL32;
import org.lwjgl.opengl.GL40;

/** Emulates GL proxy textures for backends that lack them. */
public final class ProxyTexture {

    private final int maxTextureSize;
    private final int max3DTextureSize;
    private final int maxCubeMapTextureSize;
    private final int maxArrayTextureLayers;

    private int target;
    private int width;
    private int height;
    private int depth;
    private int internalFormat;

    public ProxyTexture(int maxTextureSize, int max3DTextureSize, int maxCubeMapTextureSize, int maxArrayTextureLayers) {
        this.maxTextureSize = maxTextureSize;
        this.max3DTextureSize = max3DTextureSize;
        this.maxCubeMapTextureSize = maxCubeMapTextureSize;
        this.maxArrayTextureLayers = maxArrayTextureLayers;
    }

    public void record(int target, int level, int internalFormat, int width, int height, int depth, boolean formatSupported) {
        final boolean layersInHeight = layersInHeight(target);
        final boolean layersInDepth = layersInDepth(target);
        final int dimLimit = dimensionLimit(target);
        final int w = levelZeroEquivalentSize(width, level);
        final int h = layersInHeight ? height : levelZeroEquivalentSize(height, level);
        final int d = layersInDepth ? depth : levelZeroEquivalentSize(depth, level);
        final int hLimit = layersInHeight ? maxArrayTextureLayers : dimLimit;
        final int dLimit = layersInDepth ? maxArrayTextureLayers : dimLimit;
        final boolean fits = w >= 1 && w <= dimLimit && h >= 1 && h <= hLimit && d >= 1 && d <= dLimit && formatSupported;
        if (fits) {
            accept(target, w, h, d, internalFormat);
        } else {
            reject(target);
        }
    }

    public void reject(int target) {
        accept(target, 0, 0, 0, 0);
    }

    private void accept(int target, int width, int height, int depth, int internalFormat) {
        this.target = target;
        this.width = width;
        this.height = height;
        this.depth = depth;
        this.internalFormat = internalFormat;
    }

    public int levelParameter(int target, int level, int pname) {
        if (this.target != target) return 0;
        return switch (pname) {
            case GL11.GL_TEXTURE_WIDTH -> TextureInfo.mipLevelSize(width, level);
            case GL11.GL_TEXTURE_HEIGHT -> layersInHeight(target) ? height : TextureInfo.mipLevelSize(height, level);
            case GL12.GL_TEXTURE_DEPTH -> layersInDepth(target) ? depth : TextureInfo.mipLevelSize(depth, level);
            case GL11.GL_TEXTURE_INTERNAL_FORMAT -> internalFormat;
            default -> 0;
        };
    }

    public int dimensionLimit(int target) {
        return switch (target) {
            case GL12.GL_PROXY_TEXTURE_3D -> max3DTextureSize;
            case GL13.GL_PROXY_TEXTURE_CUBE_MAP, GL40.GL_PROXY_TEXTURE_CUBE_MAP_ARRAY -> maxCubeMapTextureSize;
            default -> maxTextureSize;
        };
    }

    public static int levelZeroEquivalentSize(int size, int level) {
        if (size <= 0 || level < 0 || level >= Integer.SIZE - 1) return 0;
        final long scaled = (long) size << level;
        return scaled > Integer.MAX_VALUE ? 0 : (int) scaled;
    }

    private static boolean layersInHeight(int target) {
        return target == GL30.GL_PROXY_TEXTURE_1D_ARRAY;
    }

    private static boolean layersInDepth(int target) {
        return target == GL30.GL_PROXY_TEXTURE_2D_ARRAY || target == GL32.GL_PROXY_TEXTURE_2D_MULTISAMPLE_ARRAY || target == GL40.GL_PROXY_TEXTURE_CUBE_MAP_ARRAY;
    }
}
