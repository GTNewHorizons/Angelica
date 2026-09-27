package com.gtnewhorizons.angelica.sdlgpu.resource;

import com.gtnewhorizons.angelica.glsm.GLTypes;
import com.gtnewhorizons.angelica.sdlgpu.frame.ContextState;
import org.lwjgl.opengl.GL11;
import org.lwjgl.opengl.GL12;
import org.lwjgl.opengl.GL30;
import org.lwjgl.system.MemoryUtil;

import java.nio.ByteBuffer;

import static org.lwjgl.sdl.SDLGPU.*;

public final class PixelOps {
    private PixelOps() {}

    public static int mipLevelSize(int base, int level) {
        if (base <= 0 || level < 0) return 0;
        if (level >= Integer.SIZE - 1) return 1;
        return Math.max(1, base >> level);
    }

    public static int defaultMipLevels(int width, int height) {
        final int max = Math.max(1, Math.max(width, height));
        return Math.max(1, 32 - Integer.numberOfLeadingZeros(max));
    }

    public static boolean isBgraSdlFormat(int sdlFormat) {
        return sdlFormat == SDL_GPU_TEXTUREFORMAT_B8G8R8A8_UNORM || sdlFormat == SDL_GPU_TEXTUREFORMAT_B8G8R8A8_UNORM_SRGB;
    }

    public static boolean isDepthFormat(int sdlFormat) {
        return sdlFormat == SDL_GPU_TEXTUREFORMAT_D16_UNORM
            || sdlFormat == SDL_GPU_TEXTUREFORMAT_D24_UNORM
            || sdlFormat == SDL_GPU_TEXTUREFORMAT_D32_FLOAT
            || sdlFormat == SDL_GPU_TEXTUREFORMAT_D24_UNORM_S8_UINT
            || sdlFormat == SDL_GPU_TEXTUREFORMAT_D32_FLOAT_S8_UINT;
    }

    public static boolean isDepthStencilFormat(int sdlFormat) {
        return sdlFormat == SDL_GPU_TEXTUREFORMAT_D24_UNORM_S8_UINT || sdlFormat == SDL_GPU_TEXTUREFORMAT_D32_FLOAT_S8_UINT;
    }

    public static int depthBits(int sdlFormat) {
        return switch (sdlFormat) {
            case SDL_GPU_TEXTUREFORMAT_D16_UNORM -> 16;
            case SDL_GPU_TEXTUREFORMAT_D24_UNORM, SDL_GPU_TEXTUREFORMAT_D24_UNORM_S8_UINT -> 24;
            case SDL_GPU_TEXTUREFORMAT_D32_FLOAT, SDL_GPU_TEXTUREFORMAT_D32_FLOAT_S8_UINT -> 32;
            default -> 0;
        };
    }

    public static int stencilBits(int sdlFormat) {
        return isDepthStencilFormat(sdlFormat) ? 8 : 0;
    }

    public static int colorChannelBits(int sdlFormat, int channel) {
        if (channel < 0 || channel > 3) return 0;
        return switch (sdlFormat) {
            case SDL_GPU_TEXTUREFORMAT_A8_UNORM -> channel == 3 ? 8 : 0;
            case SDL_GPU_TEXTUREFORMAT_R10G10B10A2_UNORM -> channel == 3 ? 2 : 10;
            case SDL_GPU_TEXTUREFORMAT_R11G11B10_UFLOAT -> switch (channel) {
                case 0, 1 -> 11;
                case 2 -> 10;
                default -> 0;
            };
            case SDL_GPU_TEXTUREFORMAT_B5G6R5_UNORM -> switch (channel) {
                case 0, 2 -> 5;
                case 1 -> 6;
                default -> 0;
            };
            case SDL_GPU_TEXTUREFORMAT_B5G5R5A1_UNORM -> channel == 3 ? 1 : 5;
            case SDL_GPU_TEXTUREFORMAT_B4G4R4A4_UNORM -> 4;
            default -> channel < sdlFormatColorComponents(sdlFormat) ? uniformChannelBits(sdlFormat) : 0;
        };
    }

    public static int colorComponentMask(int sdlFormat) {
        int mask = 0;
        for (int c = 0; c < 4; c++) {
            if (colorChannelBits(sdlFormat, c) > 0) mask |= 1 << c;
        }
        return mask;
    }

    private static int uniformChannelBits(int sdlFormat) {
        return switch (sdlFormat) {
            case SDL_GPU_TEXTUREFORMAT_R8_UNORM, SDL_GPU_TEXTUREFORMAT_R8_SNORM,
                 SDL_GPU_TEXTUREFORMAT_R8_UINT, SDL_GPU_TEXTUREFORMAT_R8_INT,
                 SDL_GPU_TEXTUREFORMAT_R8G8_UNORM, SDL_GPU_TEXTUREFORMAT_R8G8_SNORM,
                 SDL_GPU_TEXTUREFORMAT_R8G8_UINT, SDL_GPU_TEXTUREFORMAT_R8G8_INT,
                 SDL_GPU_TEXTUREFORMAT_R8G8B8A8_UNORM, SDL_GPU_TEXTUREFORMAT_R8G8B8A8_SNORM,
                 SDL_GPU_TEXTUREFORMAT_R8G8B8A8_UNORM_SRGB, SDL_GPU_TEXTUREFORMAT_R8G8B8A8_UINT,
                 SDL_GPU_TEXTUREFORMAT_R8G8B8A8_INT, SDL_GPU_TEXTUREFORMAT_B8G8R8A8_UNORM,
                 SDL_GPU_TEXTUREFORMAT_B8G8R8A8_UNORM_SRGB -> 8;
            case SDL_GPU_TEXTUREFORMAT_R16_UNORM, SDL_GPU_TEXTUREFORMAT_R16_SNORM,
                 SDL_GPU_TEXTUREFORMAT_R16_FLOAT, SDL_GPU_TEXTUREFORMAT_R16_UINT,
                 SDL_GPU_TEXTUREFORMAT_R16_INT, SDL_GPU_TEXTUREFORMAT_R16G16_UNORM,
                 SDL_GPU_TEXTUREFORMAT_R16G16_SNORM, SDL_GPU_TEXTUREFORMAT_R16G16_FLOAT,
                 SDL_GPU_TEXTUREFORMAT_R16G16_UINT, SDL_GPU_TEXTUREFORMAT_R16G16_INT,
                 SDL_GPU_TEXTUREFORMAT_R16G16B16A16_UNORM, SDL_GPU_TEXTUREFORMAT_R16G16B16A16_SNORM,
                 SDL_GPU_TEXTUREFORMAT_R16G16B16A16_FLOAT, SDL_GPU_TEXTUREFORMAT_R16G16B16A16_UINT,
                 SDL_GPU_TEXTUREFORMAT_R16G16B16A16_INT -> 16;
            case SDL_GPU_TEXTUREFORMAT_R32_FLOAT, SDL_GPU_TEXTUREFORMAT_R32_UINT,
                 SDL_GPU_TEXTUREFORMAT_R32_INT, SDL_GPU_TEXTUREFORMAT_R32G32_FLOAT,
                 SDL_GPU_TEXTUREFORMAT_R32G32_UINT, SDL_GPU_TEXTUREFORMAT_R32G32_INT,
                 SDL_GPU_TEXTUREFORMAT_R32G32B32A32_FLOAT, SDL_GPU_TEXTUREFORMAT_R32G32B32A32_UINT,
                 SDL_GPU_TEXTUREFORMAT_R32G32B32A32_INT -> 32;
            default -> 0;
        };
    }

    private static int sdlFormatColorComponents(int sdlFormat) {
        return switch (sdlFormat) {
            case SDL_GPU_TEXTUREFORMAT_R8_UNORM, SDL_GPU_TEXTUREFORMAT_R8_SNORM,
                 SDL_GPU_TEXTUREFORMAT_R8_UINT, SDL_GPU_TEXTUREFORMAT_R8_INT,
                 SDL_GPU_TEXTUREFORMAT_R16_UNORM, SDL_GPU_TEXTUREFORMAT_R16_SNORM,
                 SDL_GPU_TEXTUREFORMAT_R16_FLOAT, SDL_GPU_TEXTUREFORMAT_R16_UINT,
                 SDL_GPU_TEXTUREFORMAT_R16_INT, SDL_GPU_TEXTUREFORMAT_R32_FLOAT,
                 SDL_GPU_TEXTUREFORMAT_R32_UINT, SDL_GPU_TEXTUREFORMAT_R32_INT -> 1;
            case SDL_GPU_TEXTUREFORMAT_R8G8_UNORM, SDL_GPU_TEXTUREFORMAT_R8G8_SNORM,
                 SDL_GPU_TEXTUREFORMAT_R8G8_UINT, SDL_GPU_TEXTUREFORMAT_R8G8_INT,
                 SDL_GPU_TEXTUREFORMAT_R16G16_UNORM, SDL_GPU_TEXTUREFORMAT_R16G16_SNORM,
                 SDL_GPU_TEXTUREFORMAT_R16G16_FLOAT, SDL_GPU_TEXTUREFORMAT_R16G16_UINT,
                 SDL_GPU_TEXTUREFORMAT_R16G16_INT, SDL_GPU_TEXTUREFORMAT_R32G32_FLOAT,
                 SDL_GPU_TEXTUREFORMAT_R32G32_UINT, SDL_GPU_TEXTUREFORMAT_R32G32_INT -> 2;
            default -> 4;
        };
    }

    public static int glPixelSize(int format, int type) {
        final int packed = GLTypes.packedTexelBytes(type);
        if (packed != 0) return packed;
        final int components = switch (format) {
            case GL11.GL_RED, GL11.GL_GREEN, GL11.GL_BLUE, GL11.GL_ALPHA, GL11.GL_LUMINANCE,
                 GL30.GL_RED_INTEGER, GL30.GL_GREEN_INTEGER, GL30.GL_BLUE_INTEGER, GL30.GL_ALPHA_INTEGER,
                 GL11.GL_DEPTH_COMPONENT, GL11.GL_STENCIL_INDEX -> 1;
            case GL11.GL_LUMINANCE_ALPHA, GL30.GL_RG, GL30.GL_RG_INTEGER -> 2;
            case GL11.GL_RGB, GL12.GL_BGR, GL30.GL_RGB_INTEGER, GL30.GL_BGR_INTEGER -> 3;
            default -> 4;
        };
        return components * GLTypes.sizeBytes(type);
    }

    public static int alignedRowStride(int rowPixels, int pixelBytes, int alignment) {
        final int unaligned = rowPixels * pixelBytes;
        final int align = Math.max(1, alignment);
        return ((unaligned + align - 1) / align) * align;
    }

    public static int sdlFormatTexelBytes(int sdlFormat) {
        return switch (sdlFormat) {
            case SDL_GPU_TEXTUREFORMAT_R8_UNORM, SDL_GPU_TEXTUREFORMAT_R8_SNORM,
                 SDL_GPU_TEXTUREFORMAT_R8_UINT, SDL_GPU_TEXTUREFORMAT_R8_INT,
                 SDL_GPU_TEXTUREFORMAT_A8_UNORM -> 1;
            case SDL_GPU_TEXTUREFORMAT_R8G8_UNORM, SDL_GPU_TEXTUREFORMAT_R8G8_SNORM,
                 SDL_GPU_TEXTUREFORMAT_R16_UNORM, SDL_GPU_TEXTUREFORMAT_R16_SNORM,
                 SDL_GPU_TEXTUREFORMAT_R16_FLOAT, SDL_GPU_TEXTUREFORMAT_R16_UINT,
                 SDL_GPU_TEXTUREFORMAT_R16_INT, SDL_GPU_TEXTUREFORMAT_R8G8_UINT,
                 SDL_GPU_TEXTUREFORMAT_R8G8_INT, SDL_GPU_TEXTUREFORMAT_D16_UNORM -> 2;
            case SDL_GPU_TEXTUREFORMAT_R8G8B8A8_UNORM, SDL_GPU_TEXTUREFORMAT_R8G8B8A8_SNORM,
                 SDL_GPU_TEXTUREFORMAT_R8G8B8A8_UNORM_SRGB, SDL_GPU_TEXTUREFORMAT_B8G8R8A8_UNORM,
                 SDL_GPU_TEXTUREFORMAT_B8G8R8A8_UNORM_SRGB, SDL_GPU_TEXTUREFORMAT_R32_FLOAT,
                 SDL_GPU_TEXTUREFORMAT_R32_UINT, SDL_GPU_TEXTUREFORMAT_R32_INT,
                 SDL_GPU_TEXTUREFORMAT_R16G16_UNORM, SDL_GPU_TEXTUREFORMAT_R16G16_SNORM,
                 SDL_GPU_TEXTUREFORMAT_R16G16_FLOAT, SDL_GPU_TEXTUREFORMAT_R16G16_UINT,
                 SDL_GPU_TEXTUREFORMAT_R16G16_INT, SDL_GPU_TEXTUREFORMAT_R8G8B8A8_UINT,
                 SDL_GPU_TEXTUREFORMAT_R8G8B8A8_INT, SDL_GPU_TEXTUREFORMAT_R10G10B10A2_UNORM,
                 SDL_GPU_TEXTUREFORMAT_R11G11B10_UFLOAT,
                 SDL_GPU_TEXTUREFORMAT_D24_UNORM, SDL_GPU_TEXTUREFORMAT_D32_FLOAT,
                 SDL_GPU_TEXTUREFORMAT_D24_UNORM_S8_UINT -> 4;
            case SDL_GPU_TEXTUREFORMAT_R16G16B16A16_UNORM, SDL_GPU_TEXTUREFORMAT_R16G16B16A16_SNORM,
                 SDL_GPU_TEXTUREFORMAT_R16G16B16A16_FLOAT, SDL_GPU_TEXTUREFORMAT_R16G16B16A16_UINT,
                 SDL_GPU_TEXTUREFORMAT_R16G16B16A16_INT, SDL_GPU_TEXTUREFORMAT_R32G32_FLOAT,
                 SDL_GPU_TEXTUREFORMAT_R32G32_UINT, SDL_GPU_TEXTUREFORMAT_R32G32_INT,
                 SDL_GPU_TEXTUREFORMAT_D32_FLOAT_S8_UINT -> 8;
            case SDL_GPU_TEXTUREFORMAT_R32G32B32A32_FLOAT, SDL_GPU_TEXTUREFORMAT_R32G32B32A32_UINT,
                 SDL_GPU_TEXTUREFORMAT_R32G32B32A32_INT -> 16;
            default -> 4;
        };
    }

    public static boolean isSdlFormatUnsignedInteger(int sdlFormat) {
        return switch (sdlFormat) {
            case SDL_GPU_TEXTUREFORMAT_R8_UINT, SDL_GPU_TEXTUREFORMAT_R16_UINT, SDL_GPU_TEXTUREFORMAT_R32_UINT,
                 SDL_GPU_TEXTUREFORMAT_R8G8_UINT, SDL_GPU_TEXTUREFORMAT_R16G16_UINT,
                 SDL_GPU_TEXTUREFORMAT_R32G32_UINT, SDL_GPU_TEXTUREFORMAT_R8G8B8A8_UINT,
                 SDL_GPU_TEXTUREFORMAT_R16G16B16A16_UINT, SDL_GPU_TEXTUREFORMAT_R32G32B32A32_UINT -> true;
            default -> false;
        };
    }

    public static boolean isSdlFormatInteger(int sdlFormat) {
        if (isSdlFormatUnsignedInteger(sdlFormat)) return true;
        return switch (sdlFormat) {
            case SDL_GPU_TEXTUREFORMAT_R8_INT, SDL_GPU_TEXTUREFORMAT_R8G8_INT, SDL_GPU_TEXTUREFORMAT_R8G8B8A8_INT,
                 SDL_GPU_TEXTUREFORMAT_R16_INT, SDL_GPU_TEXTUREFORMAT_R16G16_INT, SDL_GPU_TEXTUREFORMAT_R16G16B16A16_INT,
                 SDL_GPU_TEXTUREFORMAT_R32_INT, SDL_GPU_TEXTUREFORMAT_R32G32_INT, SDL_GPU_TEXTUREFORMAT_R32G32B32A32_INT -> true;
            default -> false;
        };
    }

    public static ByteBuffer expandToRGBA(int format, ByteBuffer src, int width, int height) {
        if (src == null) return null;
        final int pixelCount = width * height;
        return switch (format) {
            case GL11.GL_ALPHA -> {
                final ByteBuffer dst = MemoryUtil.memAlloc(pixelCount * 4);
                for (int i = 0; i < pixelCount; i++) {
                    final byte a = src.get(src.position() + i);
                    dst.put((byte) 0xFF).put((byte) 0xFF).put((byte) 0xFF).put(a);
                }
                dst.flip();
                yield dst;
            }
            case GL11.GL_LUMINANCE -> {
                final ByteBuffer dst = MemoryUtil.memAlloc(pixelCount * 4);
                for (int i = 0; i < pixelCount; i++) {
                    final byte l = src.get(src.position() + i);
                    dst.put(l).put(l).put(l).put((byte) 0xFF);
                }
                dst.flip();
                yield dst;
            }
            case GL11.GL_LUMINANCE_ALPHA -> {
                final ByteBuffer dst = MemoryUtil.memAlloc(pixelCount * 4);
                for (int i = 0; i < pixelCount; i++) {
                    final byte l = src.get(src.position() + i * 2);
                    final byte a = src.get(src.position() + i * 2 + 1);
                    dst.put(l).put(l).put(l).put(a);
                }
                dst.flip();
                yield dst;
            }
            default -> null;
        };
    }

    public static ByteBuffer swapRedBlueCopy(ByteBuffer src, int width, int height) {
        final int pixelCount = width * height;
        final ByteBuffer dst = MemoryUtil.memAlloc(pixelCount * 4);
        final int srcPos = src.position();
        for (int i = 0; i < pixelCount; i++) {
            final int o = i * 4;
            final byte c0 = src.get(srcPos + o);
            final byte c1 = src.get(srcPos + o + 1);
            final byte c2 = src.get(srcPos + o + 2);
            final byte c3 = src.get(srcPos + o + 3);
            dst.put(c2).put(c1).put(c0).put(c3);
        }
        dst.flip();
        return dst;
    }

    public static void swapRedBlueIfNeeded(ByteBuffer pixels, int width, int height, boolean swap) {
        if (!swap) return;
        final int pos = pixels.position();
        final int pixelCount = width * height;
        for (int i = 0; i < pixelCount; i++) {
            final int offset = pos + i * 4;
            final byte r = pixels.get(offset);
            final byte b = pixels.get(offset + 2);
            pixels.put(offset, b);
            pixels.put(offset + 2, r);
        }
        pixels.position(pos);
    }

    public static ByteBuffer prepareUploadBuffer(int format, int type, int dstSdlFormat, ByteBuffer src, int width, int height) {
        if (src == null) return null;
        if (dstSdlFormat == SDL_GPU_TEXTUREFORMAT_R10G10B10A2_UNORM) return prepareRgb10A2Upload(format, type, src, width, height);
        final boolean storageBgra = isBgraSdlFormat(dstSdlFormat);
        final ByteBuffer expanded = expandToRGBA(format, src, width, height);
        if (expanded != null) {
            swapRedBlueIfNeeded(expanded, width, height, storageBgra);
            return expanded;
        }
        final boolean sourceBgra = (format == GL12.GL_BGRA);
        if (sourceBgra != storageBgra) {
            return swapRedBlueCopy(src, width, height);
        }
        return src;
    }

    private static ByteBuffer prepareRgb10A2Upload(int format, int type, ByteBuffer src, int width, int height) {
        if (format == GL11.GL_RGBA && type == GL12.GL_UNSIGNED_INT_2_10_10_10_REV) return src;
        if ((format == GL11.GL_RGBA || format == GL12.GL_BGRA) && type == GL11.GL_UNSIGNED_BYTE) {
            final int pixelCount = width * height;
            final ByteBuffer dst = MemoryUtil.memAlloc(pixelCount * 4);
            final int srcPos = src.position();
            final int rOff = format == GL12.GL_BGRA ? 2 : 0;
            final int bOff = 2 - rOff;
            for (int i = 0; i < pixelCount; i++) {
                final int o = i * 4;
                final int r = src.get(srcPos + o + rOff) & 0xFF;
                final int g = src.get(srcPos + o + 1) & 0xFF;
                final int b = src.get(srcPos + o + bOff) & 0xFF;
                final int a = src.get(srcPos + o + 3) & 0xFF;
                dst.putInt(o, unorm8ToBits(r, 1023) | unorm8ToBits(g, 1023) << 10 | unorm8ToBits(b, 1023) << 20 | unorm8ToBits(a, 3) << 30);
            }
            dst.position(0).limit(pixelCount * 4);
            return dst;
        }
        throw new UnsupportedOperationException("GL_RGB10_A2 upload from format 0x" + Integer.toHexString(format) + " type 0x" + Integer.toHexString(type) + " is not supported on SDL-GPU");
    }

    private static int unorm8ToBits(int v, int max) {
        return (v * max + 127) / 255;
    }

    public static ByteBuffer applyUnpackPixelStore(ByteBuffer src, int width, int height, int format, int type, ContextState.PixelStoreState ps) {
        if (src == null || ps == null || ps.isDefault()) return src;
        final int pixelBytes = glPixelSize(format, type);
        if (pixelBytes <= 0) return src;
        final int srcStride = alignedRowStride(ps.unpackRowLength > 0 ? ps.unpackRowLength : width, pixelBytes, ps.unpackAlignment);
        final int dstStride = width * pixelBytes;
        final int totalDst = dstStride * height;
        final ByteBuffer dst = MemoryUtil.memAlloc(totalDst);
        final int basePos = src.position()
            + ps.unpackSkipRows * srcStride
            + ps.unpackSkipPixels * pixelBytes;
        for (int row = 0; row < height; row++) {
            final int srcRowPos = basePos + row * srcStride;
            for (int b = 0; b < dstStride; b++) {
                dst.put(row * dstStride + b, src.get(srcRowPos + b));
            }
        }
        dst.position(0).limit(totalDst);
        return dst;
    }
}
