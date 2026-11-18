package com.gtnewhorizons.angelica.sdlgpu.resource;

import com.gtnewhorizons.angelica.glsm.GLTypes;
import org.lwjgl.opengl.GL11;
import org.lwjgl.opengl.GL12;
import org.lwjgl.opengl.GL13;
import org.lwjgl.opengl.GL14;
import org.lwjgl.opengl.GL21;
import org.lwjgl.opengl.GL30;
import org.lwjgl.opengl.GL31;
import org.lwjgl.opengl.GL33;
import org.lwjgl.opengl.GL41;
import org.lwjgl.opengl.ARBTextureFloat;
import org.lwjgl.opengl.EXTTextureInteger;
import org.lwjgl.opengl.EXTTextureSRGBR8;
import org.lwjgl.opengl.EXTTextureSRGBRG8;
import org.lwjgl.opengl.EXTTextureSnorm;
import org.lwjgl.opengl.EXTTextureStorage;
import org.lwjgl.system.MemoryUtil;

import java.nio.ByteOrder;

import static org.lwjgl.sdl.SDLGPU.*;

public final class PixelPack {
    private PixelPack() {}

    private static final boolean LITTLE_ENDIAN = ByteOrder.nativeOrder() == ByteOrder.LITTLE_ENDIAN;

    private static final int K_UNORM = 0;
    private static final int K_SNORM = 1;
    private static final int K_FLOAT = 2;
    private static final int K_UINT = 3;
    private static final int K_SINT = 4;

    private static final int CH_R = 0;
    private static final int CH_G = 1;
    private static final int CH_B = 2;
    private static final int CH_A = 3;
    private static final int CH_L = 4;
    private static final int CH_ZERO = 5;
    private static final int CH_ONE = 6;
    private static final int CH_L_RG = 7;
    private static final int REBASE_IDENTITY = CH_R | (CH_G << 4) | (CH_B << 8) | (CH_A << 12);

    private static final int OK = 0;
    private static final int INVALID_ENUM = 1;
    private static final int INVALID_OPERATION = 2;

    private static final float MAX_RGB9E5 = 65408.0f;

    public static void validate(int format, int type) {
        final int err = checkFormatAndType(format, type);
        if (err == INVALID_ENUM) {
            throw new IllegalArgumentException("GL_INVALID_ENUM: pack format 0x" + Integer.toHexString(format) + " type 0x" + Integer.toHexString(type));
        }
        if (err == INVALID_OPERATION) {
            throw new IllegalArgumentException("GL_INVALID_OPERATION: pack format 0x" + Integer.toHexString(format) + " type 0x" + Integer.toHexString(type));
        }
    }

    public static boolean isDepthFormat(int glFormat) {
        return glFormat == GL11.GL_DEPTH_COMPONENT;
    }

    public static boolean isStencilFormat(int glFormat) {
        return glFormat == GL11.GL_STENCIL_INDEX;
    }

    public static boolean isDepthStencilFormat(int glFormat) {
        return glFormat == GL30.GL_DEPTH_STENCIL;
    }

    public static int rowStride(int format, int type, int width, PackState ps) {
        return PixelOps.alignedRowStride(ps.rowLength > 0 ? ps.rowLength : width, PixelOps.glPixelSize(format, type), ps.alignment);
    }

    public static long imageSize(int format, int type, int width, int height, PackState ps) {
        if (width <= 0 || height <= 0) return 0L;
        final long stride = rowStride(format, type, width, ps);
        final long pixelBytes = PixelOps.glPixelSize(format, type);
        return (ps.skipRows + height - 1L) * stride + (ps.skipPixels + (long) width) * pixelBytes;
    }

    public static void packColor(long src, int srcSdlFormat, int srcBaseFormat, int srcRowBytes, int width, int height, boolean flipRows, boolean readPixels, int format, int type, PackState ps, long dst) {
        validate(format, type);
        if (!isColorFormat(format)) {
            throw new IllegalArgumentException("GL_INVALID_OPERATION: packColor with non-color format 0x" + Integer.toHexString(format));
        }
        final int kind = sourceKind(srcSdlFormat);
        final boolean srcInteger = kind == K_UINT || kind == K_SINT;
        final boolean dstInteger = isIntegerFormat(format);
        if (srcInteger != dstInteger) {
            throw new IllegalArgumentException("GL_INVALID_OPERATION: integer / non-integer mismatch, source 0x" + Integer.toHexString(srcSdlFormat) + " format 0x" + Integer.toHexString(format));
        }
        final int rebase = rebaseSwizzle(srcBaseFormat);
        if (width <= 0 || height <= 0) return;

        final int texelBytes = PixelOps.sdlFormatTexelBytes(srcSdlFormat);
        final int pixelBytes = PixelOps.glPixelSize(format, type);
        final int stride = rowStride(format, type, width, ps);
        final long base = dst + (long) ps.skipRows * stride + (long) ps.skipPixels * pixelBytes;
        final int rowBytes = width * pixelBytes;
        final int swapSize = ps.swapBytes ? swapUnit(type) : 0;
        final boolean lumConversion = isLuminanceFormat(format)
            && (srcBaseFormat == GL30.GL_RG || srcBaseFormat == GL11.GL_RGB || srcBaseFormat == GL11.GL_RGBA);
        final int n = formatComponents(format);
        final int requested = formatSwizzle(format, false);
        final boolean rebaseChanges = rebaseChangesRequested(requested, n, rebase);
        final boolean clamp = !dstInteger && clampNeeded(kind, readPixels, format, type, lumConversion);

        if (!clamp && !lumConversion && !rebaseChanges && layoutMatches(srcSdlFormat, format, type)) {
            for (int j = 0; j < height; j++) {
                final long d = base + (long) j * stride;
                MemoryUtil.memCopy(src + (long) srcRow(j, height, flipRows) * srcRowBytes, d, rowBytes);
                if (swapSize != 0) swapRow(d, rowBytes, swapSize);
            }
            return;
        }

        final int swizzle = applyRebase(formatSwizzle(format, lumConversion && readPixels), n, rebase, srcBaseFormat == GL30.GL_RG);

        if (LITTLE_ENDIAN && !clamp && !lumConversion && !rebaseChanges && isUnorm8x4(srcSdlFormat) && byteShuffleType(format, type)) {
            final boolean reversed = type == GL12.GL_UNSIGNED_INT_8_8_8_8;
            final int o0 = shuffleOffset(srcSdlFormat, swizzle, n, reversed, 0);
            final int o1 = shuffleOffset(srcSdlFormat, swizzle, n, reversed, 1);
            final int o2 = shuffleOffset(srcSdlFormat, swizzle, n, reversed, 2);
            final int o3 = shuffleOffset(srcSdlFormat, swizzle, n, reversed, 3);
            for (int j = 0; j < height; j++) {
                final long s = src + (long) srcRow(j, height, flipRows) * srcRowBytes;
                final long d = base + (long) j * stride;
                for (int x = 0; x < width; x++) {
                    final long t = s + ((long) x << 2);
                    final long o = d + (long) x * n;
                    MemoryUtil.memPutByte(o, MemoryUtil.memGetByte(t + o0));
                    if (n > 1) MemoryUtil.memPutByte(o + 1, MemoryUtil.memGetByte(t + o1));
                    if (n > 2) MemoryUtil.memPutByte(o + 2, MemoryUtil.memGetByte(t + o2));
                    if (n > 3) MemoryUtil.memPutByte(o + 3, MemoryUtil.memGetByte(t + o3));
                }
                if (swapSize != 0) swapRow(d, rowBytes, swapSize);
            }
            return;
        }

        final boolean packed = GLTypes.packedTexelBytes(type) != 0;
        final int compBytes = packed ? 0 : GLTypes.sizeBytes(type);
        final boolean direct = (kind == K_UNORM || kind == K_SNORM) && !clamp && !lumConversion;
        for (int j = 0; j < height; j++) {
            final long s = src + (long) srcRow(j, height, flipRows) * srcRowBytes;
            final long d = base + (long) j * stride;
            for (int x = 0; x < width; x++) {
                final long t = s + (long) x * texelBytes;
                final long o = d + (long) x * pixelBytes;
                if (packed) {
                    putSized(o, pixelBytes, packedTexel(t, srcSdlFormat, kind, swizzle, n, type, direct, clamp, dstInteger));
                } else if (dstInteger) {
                    for (int i = 0; i < n; i++) {
                        putSized(o + (long) i * compBytes, compBytes, clampInteger(integerChannel(t, srcSdlFormat, (swizzle >> (i << 2)) & 15), type));
                    }
                } else {
                    for (int i = 0; i < n; i++) {
                        putSized(o + (long) i * compBytes, compBytes, normComponent(t, srcSdlFormat, kind, (swizzle >> (i << 2)) & 15, type, direct, clamp));
                    }
                }
            }
            if (swapSize != 0) swapRow(d, rowBytes, swapSize);
        }
    }

    public static void packDepth(long srcR32f, int srcRowBytes, int width, int height, boolean flipRows, int type, PackState ps, long dst) {
        validate(GL11.GL_DEPTH_COMPONENT, type);
        if (width <= 0 || height <= 0) return;
        final int size = GLTypes.sizeBytes(type);
        final int stride = rowStride(GL11.GL_DEPTH_COMPONENT, type, width, ps);
        final long base = dst + (long) ps.skipRows * stride + (long) ps.skipPixels * size;
        for (int j = 0; j < height; j++) {
            final long s = srcR32f + (long) srcRow(j, height, flipRows) * srcRowBytes;
            final long d = base + (long) j * stride;
            for (int x = 0; x < width; x++) {
                putSized(d + (long) x * size, size, encodeDepth(MemoryUtil.memGetFloat(s + ((long) x << 2)), type));
            }
            if (ps.swapBytes && size > 1) swapRow(d, width * size, size);
        }
    }

    public static void packStencil(long srcR8, int srcRowBytes, int width, int height, boolean flipRows, int type, PackState ps, long dst) {
        validate(GL11.GL_STENCIL_INDEX, type);
        if (width <= 0 || height <= 0) return;
        final int size = GLTypes.sizeBytes(type);
        final int stride = rowStride(GL11.GL_STENCIL_INDEX, type, width, ps);
        final long base = dst + (long) ps.skipRows * stride + (long) ps.skipPixels * size;
        for (int j = 0; j < height; j++) {
            final long s = srcR8 + (long) srcRow(j, height, flipRows) * srcRowBytes;
            final long d = base + (long) j * stride;
            for (int x = 0; x < width; x++) {
                putSized(d + (long) x * size, size, encodeStencil(MemoryUtil.memGetByte(s + x) & 0xFF, type));
            }
            if (ps.swapBytes && size > 1) swapRow(d, width * size, size);
        }
    }

    public static void packDepthStencil(long srcR32f, long srcR8, int srcRowBytes32, int srcRowBytes8, int width, int height, boolean flipRows, int type, PackState ps, long dst) {
        validate(GL30.GL_DEPTH_STENCIL, type);
        if (width <= 0 || height <= 0) return;
        final int size = GLTypes.packedTexelBytes(type);
        final int stride = rowStride(GL30.GL_DEPTH_STENCIL, type, width, ps);
        final long base = dst + (long) ps.skipRows * stride + (long) ps.skipPixels * size;
        final boolean float32 = type == GL30.GL_FLOAT_32_UNSIGNED_INT_24_8_REV;
        for (int j = 0; j < height; j++) {
            final int row = srcRow(j, height, flipRows);
            final long sd = srcR32f + (long) row * srcRowBytes32;
            final long ss = srcR8 + (long) row * srcRowBytes8;
            final long d = base + (long) j * stride;
            for (int x = 0; x < width; x++) {
                final float depth = MemoryUtil.memGetFloat(sd + ((long) x << 2));
                final int stencil = MemoryUtil.memGetByte(ss + x) & 0xFF;
                final long o = d + (long) x * size;
                if (float32) {
                    MemoryUtil.memPutFloat(o, depth);
                    MemoryUtil.memPutInt(o + 4, stencil);
                } else {
                    MemoryUtil.memPutInt(o, ((int) (depth * 16777215.0f) << 8) | stencil);
                }
            }
            if (ps.swapBytes) swapRow(d, width * size, 4);
        }
    }

    private static float halfToFloat(int h) {
        final int sign = (h & 0x8000) << 16;
        final int exp = (h >>> 10) & 0x1F;
        final int mant = h & 0x3FF;
        if (exp == 0) {
            final float v = mant * (1.0f / 16777216.0f);
            return sign != 0 ? -v : v;
        }
        if (exp == 31) return Float.intBitsToFloat(sign | 0x7F800000 | (mant << 13));
        return Float.intBitsToFloat(sign | ((exp - 15 + 127) << 23) | (mant << 13));
    }

    private static int floatToHalf(float f) {
        final int x = Float.floatToRawIntBits(f);
        final int sign = (x >>> 16) & 0x8000;
        final int exp = (x >>> 23) & 0xFF;
        int mant = x & 0x7FFFFF;
        if (exp == 0xFF) return sign | 0x7C00 | (mant != 0 ? 0x200 | (mant >>> 13) : 0);
        final int he = exp - 127 + 15;
        if (he >= 0x1F) return sign | 0x7C00;
        if (he <= 0) {
            if (he < -10) return sign;
            mant |= 0x800000;
            final int shift = 14 - he;
            int hm = mant >>> shift;
            final int rem = mant & ((1 << shift) - 1);
            final int half = 1 << (shift - 1);
            if (rem > half || (rem == half && (hm & 1) != 0)) hm++;
            return sign | hm;
        }
        int out = sign | (he << 10) | (mant >>> 13);
        final int rem = mant & 0x1FFF;
        if (rem > 0x1000 || (rem == 0x1000 && (out & 1) != 0)) out++;
        return out;
    }

    private static float uf11ToFloat(int v) {
        return packedUfloatToFloat(v, 6);
    }

    private static float uf10ToFloat(int v) {
        return packedUfloatToFloat(v, 5);
    }

    private static float packedUfloatToFloat(int v, int mantBits) {
        final int exp = (v >>> mantBits) & 0x1F;
        final int mant = v & ((1 << mantBits) - 1);
        if (exp == 0) return mant == 0 ? 0.0f : mant * (1.0f / (1 << 14)) / (1 << mantBits);
        if (exp == 31) return Float.intBitsToFloat(0x7F800000 | mant);
        final int e = exp - 15;
        final float scale = e < 0 ? 1.0f / (1 << -e) : (float) (1 << e);
        return scale * (1.0f + (float) mant / (1 << mantBits));
    }

    private static int floatToUf11(float val) {
        return floatToPackedUfloat(val, 6, 65024.0f);
    }

    private static int floatToUf10(float val) {
        return floatToPackedUfloat(val, 5, 64512.0f);
    }

    private static int floatToPackedUfloat(float val, int mantBits, float maxFinite) {
        final int bits = Float.floatToRawIntBits(val);
        final boolean negative = bits < 0;
        int exponent = ((bits >>> 23) & 0xFF) - 127;
        final int f32Mant = bits & 0x7FFFFF;
        final int maxExponent = 0x1F << mantBits;
        if (exponent == 128) {
            if (f32Mant != 0) return maxExponent | 1;
            return negative ? 0 : maxExponent;
        }
        if (negative) return 0;
        if (val > maxFinite) return (30 << mantBits) | ((1 << mantBits) - 1);
        if (exponent > -15) {
            int mant = (int) Math.rint(Math.scalb((double) val, mantBits - exponent));
            if (mant >= 2 << mantBits) {
                mant >>= 1;
                exponent++;
            }
            mant &= (1 << mantBits) - 1;
            return ((exponent + 15) << mantBits) | mant;
        }
        final int mant = (int) Math.rint(Math.scalb((double) val, mantBits + 14));
        if ((mant >> mantBits) != 0) return 1 << mantBits;
        return mant;
    }

    private static int floatToRgb9e5(float r, float g, float b) {
        final int rc = rgb9e5ClampRange(r);
        final int gc = rgb9e5ClampRange(g);
        final int bc = rgb9e5ClampRange(b);
        int maxrgb = Math.max(rc, Math.max(gc, bc));
        maxrgb += maxrgb & (1 << (23 - 9));
        final int expShared = Math.max(maxrgb >>> 23, -15 - 1 + 127) + 1 + 15 - 127;
        final int revdenomBiasedExp = 127 - (expShared - 15 - 9) + 1;
        final float revdenom = Float.intBitsToFloat(revdenomBiasedExp << 23);
        int rm = (int) (Float.intBitsToFloat(rc) * revdenom);
        int gm = (int) (Float.intBitsToFloat(gc) * revdenom);
        int bm = (int) (Float.intBitsToFloat(bc) * revdenom);
        rm = (rm & 1) + (rm >> 1);
        gm = (gm & 1) + (gm >> 1);
        bm = (bm & 1) + (bm >> 1);
        return (expShared << 27) | (bm << 18) | (gm << 9) | rm;
    }

    private static int rgb9e5ClampRange(float x) {
        final int u = Float.floatToRawIntBits(x);
        final int max = Float.floatToRawIntBits(MAX_RGB9E5);
        if (Integer.compareUnsigned(u, 0x7F800000) > 0) return 0;
        if (Integer.compareUnsigned(u, max) >= 0) return max;
        return u;
    }

    private static int srcRow(int j, int height, boolean flipRows) {
        return flipRows ? height - 1 - j : j;
    }

    private static int swapUnit(int type) {
        final int packed = GLTypes.packedTexelBytes(type);
        final int size = packed != 0 ? packed : GLTypes.sizeBytes(type);
        return (size == 2 || size == 4) ? size : 0;
    }

    private static void swapRow(long p, int bytes, int size) {
        if (size == 2) {
            for (int i = 0; i + 1 < bytes; i += 2) {
                MemoryUtil.memPutShort(p + i, Short.reverseBytes(MemoryUtil.memGetShort(p + i)));
            }
        } else if (size == 4) {
            for (int i = 0; i + 3 < bytes; i += 4) {
                MemoryUtil.memPutInt(p + i, Integer.reverseBytes(MemoryUtil.memGetInt(p + i)));
            }
        }
    }

    private static void putSized(long p, int size, int v) {
        switch (size) {
            case 1 -> MemoryUtil.memPutByte(p, (byte) v);
            case 2 -> MemoryUtil.memPutShort(p, (short) v);
            default -> MemoryUtil.memPutInt(p, v);
        }
    }

    private static boolean clampNeeded(int kind, boolean readPixels, int format, int type, boolean lumConversion) {
        if (readPixels) {
            final boolean clampRead = kind == K_UNORM || kind == K_SNORM;
            final boolean floatType = type == GL11.GL_FLOAT || type == GL30.GL_HALF_FLOAT || type == GL30.GL_UNSIGNED_INT_10F_11F_11F_REV;
            final boolean clamp = clampRead || !floatType;
            return clamp && !(kind == K_UNORM && !lumConversion);
        }
        final boolean typeNeedsClamping = switch (type) {
            case GL11.GL_BYTE, GL11.GL_SHORT, GL11.GL_INT, GL11.GL_FLOAT, GL30.GL_HALF_FLOAT,
                 GL30.GL_UNSIGNED_INT_10F_11F_11F_REV, GL30.GL_UNSIGNED_INT_5_9_9_9_REV -> false;
            default -> true;
        };
        return typeNeedsClamping && (kind == K_FLOAT || kind == K_SNORM || isLuminanceFormat(format));
    }

    private static int packedTexel(long t, int fmt, int kind, int swizzle, int n, int type, boolean direct, boolean clamp, boolean dstInteger) {
        if (type == GL30.GL_UNSIGNED_INT_10F_11F_11F_REV) {
            return floatToUf11(channelFloat(t, fmt, kind, swizzle & 15, clamp))
                | (floatToUf11(channelFloat(t, fmt, kind, (swizzle >> 4) & 15, clamp)) << 11)
                | (floatToUf10(channelFloat(t, fmt, kind, (swizzle >> 8) & 15, clamp)) << 22);
        }
        if (type == GL30.GL_UNSIGNED_INT_5_9_9_9_REV) {
            return floatToRgb9e5(channelFloat(t, fmt, kind, swizzle & 15, clamp),
                channelFloat(t, fmt, kind, (swizzle >> 4) & 15, clamp),
                channelFloat(t, fmt, kind, (swizzle >> 8) & 15, clamp));
        }
        int word = 0;
        for (int i = 0; i < n; i++) {
            final int ch = (swizzle >> (i << 2)) & 15;
            final int bits = packedBits(type, i);
            final int v;
            if (dstInteger) {
                v = (int) clampRange(integerChannel(t, fmt, ch), 0L, (1L << bits) - 1L);
            } else {
                v = normField(t, fmt, kind, ch, bits, direct, clamp);
            }
            word |= (v & ((1 << bits) - 1)) << packedShift(type, i);
        }
        return word;
    }

    private static int normField(long t, int fmt, int kind, int ch, int dstBits, boolean direct, boolean clamp) {
        final int bits = PixelOps.colorChannelBits(fmt, ch);
        if (bits == 0) return floatToUnorm((ch == CH_A || ch == CH_ONE) ? 1.0f : 0.0f, dstBits);
        if (direct) {
            final long raw = rawChannel(t, fmt, ch);
            return (int) (kind == K_SNORM ? snormToUnorm(raw, bits, dstBits) : unormToUnorm(raw, bits, dstBits));
        }
        float f = floatChannel(t, fmt, kind, ch, bits);
        if (clamp) f = clamp01(f);
        return floatToUnorm(f, dstBits);
    }

    private static int normComponent(long t, int fmt, int kind, int ch, int type, boolean direct, boolean clamp) {
        if (ch == CH_L || ch == CH_L_RG) return encodeFloat(channelFloat(t, fmt, kind, ch, clamp), type);
        final int bits = PixelOps.colorChannelBits(fmt, ch);
        if (bits == 0) return encodeFloat((ch == CH_A || ch == CH_ONE) ? 1.0f : 0.0f, type);
        if (direct) {
            return encodeDirect(rawChannel(t, fmt, ch), bits, kind == K_SNORM, type);
        }
        float f = floatChannel(t, fmt, kind, ch, bits);
        if (clamp) f = clamp01(f);
        return encodeFloat(f, type);
    }

    private static float channelFloat(long t, int fmt, int kind, int ch, boolean clamp) {
        if (ch == CH_L || ch == CH_L_RG) {
            float sum = channelFloat(t, fmt, kind, CH_R, clamp) + channelFloat(t, fmt, kind, CH_G, clamp);
            if (ch == CH_L) sum += channelFloat(t, fmt, kind, CH_B, clamp);
            return clamp ? clamp01(sum) : sum;
        }
        final int bits = PixelOps.colorChannelBits(fmt, ch);
        if (bits == 0) return (ch == CH_A || ch == CH_ONE) ? 1.0f : 0.0f;
        final float f = floatChannel(t, fmt, kind, ch, bits);
        return clamp ? clamp01(f) : f;
    }

    private static float clamp01(float f) {
        return f < 0.0f ? 0.0f : (f > 1.0f ? 1.0f : f);
    }

    private static long integerChannel(long t, int fmt, int ch) {
        if (PixelOps.colorChannelBits(fmt, ch) == 0) return (ch == CH_A || ch == CH_ONE) ? 1L : 0L;
        return rawChannel(t, fmt, ch);
    }

    private static int clampInteger(long v, int type) {
        return switch (type) {
            case GL11.GL_UNSIGNED_BYTE -> (int) clampRange(v, 0L, 0xFFL);
            case GL11.GL_BYTE -> (int) clampRange(v, Byte.MIN_VALUE, Byte.MAX_VALUE);
            case GL11.GL_UNSIGNED_SHORT -> (int) clampRange(v, 0L, 0xFFFFL);
            case GL11.GL_SHORT -> (int) clampRange(v, Short.MIN_VALUE, Short.MAX_VALUE);
            case GL11.GL_UNSIGNED_INT -> (int) clampRange(v, 0L, 0xFFFFFFFFL);
            default -> (int) clampRange(v, Integer.MIN_VALUE, Integer.MAX_VALUE);
        };
    }

    private static long clampRange(long v, long lo, long hi) {
        return v < lo ? lo : (v > hi ? hi : v);
    }

    private static int encodeFloat(float f, int type) {
        return switch (type) {
            case GL11.GL_UNSIGNED_BYTE -> floatToUnorm(f, 8);
            case GL11.GL_UNSIGNED_SHORT -> floatToUnorm(f, 16);
            case GL11.GL_UNSIGNED_INT -> floatToUnorm(f, 32);
            case GL11.GL_BYTE -> floatToSnorm(f, 8);
            case GL11.GL_SHORT -> floatToSnorm(f, 16);
            case GL11.GL_INT -> floatToSnorm(f, 32);
            case GL30.GL_HALF_FLOAT -> floatToHalf(f);
            default -> Float.floatToRawIntBits(f);
        };
    }

    private static int encodeDirect(long raw, int bits, boolean signed, int type) {
        return switch (type) {
            case GL11.GL_UNSIGNED_BYTE -> (int) (signed ? snormToUnorm(raw, bits, 8) : unormToUnorm(raw, bits, 8));
            case GL11.GL_UNSIGNED_SHORT -> (int) (signed ? snormToUnorm(raw, bits, 16) : unormToUnorm(raw, bits, 16));
            case GL11.GL_UNSIGNED_INT -> (int) (signed ? snormToUnorm(raw, bits, 32) : unormToUnorm(raw, bits, 32));
            case GL11.GL_BYTE -> (int) (signed ? snormToSnorm(raw, bits, 8) : unormToUnorm(raw, bits, 7));
            case GL11.GL_SHORT -> (int) (signed ? snormToSnorm(raw, bits, 16) : unormToUnorm(raw, bits, 15));
            case GL11.GL_INT -> (int) (signed ? snormToSnorm(raw, bits, 32) : unormToUnorm(raw, bits, 31));
            default -> encodeFloat(signed ? snormToFloat(raw, bits) : unormToFloat(raw, bits), type);
        };
    }

    private static long maxUnsigned(int bits) {
        return (1L << bits) - 1L;
    }

    private static long unormToUnorm(long x, int srcBits, int dstBits) {
        if (srcBits == dstBits) return x;
        final long srcMax = maxUnsigned(srcBits);
        return (x * maxUnsigned(dstBits) * 2L + srcMax) / (srcMax * 2L);
    }

    private static long snormToUnorm(long x, int srcBits, int dstBits) {
        return x < 0 ? 0L : unormToUnorm(x, srcBits - 1, dstBits);
    }

    private static long snormToSnorm(long x, int srcBits, int dstBits) {
        if (srcBits == dstBits) return x;
        final long srcMax = maxUnsigned(srcBits - 1);
        final long dstMax = maxUnsigned(dstBits - 1);
        if (x <= -srcMax) return -dstMax;
        final long magnitude = ((x < 0 ? -x : x) * dstMax * 2L + srcMax) / (srcMax * 2L);
        return x < 0 ? -magnitude : magnitude;
    }

    private static float unormToFloat(long x, int bits) {
        return x * (1.0f / (float) maxUnsigned(bits));
    }

    private static float snormToFloat(long x, int bits) {
        final long max = maxUnsigned(bits - 1);
        if (x <= -max) return -1.0f;
        return x * (1.0f / (float) max);
    }

    private static int floatToUnorm(float x, int bits) {
        if (x < 0.0f) return 0;
        if (bits >= 32) {
            if (x > 1.0f) return -1;
            return (int) (long) Math.rint(x * 4294967295.0);
        }
        final long max = maxUnsigned(bits);
        if (x > 1.0f) return (int) max;
        return (int) (long) Math.rint(x * (float) max);
    }

    private static int floatToSnorm(float x, int bits) {
        final long max = maxUnsigned(bits - 1);
        if (x < -1.0f) return (int) -max;
        if (x > 1.0f) return (int) max;
        if (bits >= 32) return (int) (long) Math.rint(x * 2147483647.0);
        return (int) (long) Math.rint(x * (float) max);
    }

    private static int encodeDepth(float z, int type) {
        return switch (type) {
            case GL11.GL_UNSIGNED_BYTE -> (int) (z * 255.0f);
            case GL11.GL_BYTE -> (((int) (255.0f * z)) - 1) / 2;
            case GL11.GL_UNSIGNED_SHORT -> (int) (long) Math.rint(z * 65535.0f);
            case GL11.GL_SHORT -> (((int) (65535.0f * z)) - 1) / 2;
            case GL11.GL_UNSIGNED_INT -> (int) (long) (z * 4294967295.0);
            case GL11.GL_INT -> (int) (2147483647.0 * z);
            case GL30.GL_HALF_FLOAT -> floatToHalf(z);
            default -> Float.floatToRawIntBits(z);
        };
    }

    private static int encodeStencil(int s, int type) {
        return switch (type) {
            case GL11.GL_BYTE -> s & 0x7F;
            case GL11.GL_FLOAT -> Float.floatToRawIntBits((float) s);
            case GL30.GL_HALF_FLOAT -> floatToHalf((float) s);
            default -> s;
        };
    }

    private static int sourceKind(int fmt) {
        return switch (fmt) {
            case SDL_GPU_TEXTUREFORMAT_R8_UNORM, SDL_GPU_TEXTUREFORMAT_R8G8_UNORM, SDL_GPU_TEXTUREFORMAT_R8G8B8A8_UNORM,
                 SDL_GPU_TEXTUREFORMAT_R8G8B8A8_UNORM_SRGB, SDL_GPU_TEXTUREFORMAT_B8G8R8A8_UNORM,
                 SDL_GPU_TEXTUREFORMAT_B8G8R8A8_UNORM_SRGB, SDL_GPU_TEXTUREFORMAT_A8_UNORM,
                 SDL_GPU_TEXTUREFORMAT_R16_UNORM, SDL_GPU_TEXTUREFORMAT_R16G16_UNORM,
                 SDL_GPU_TEXTUREFORMAT_R16G16B16A16_UNORM, SDL_GPU_TEXTUREFORMAT_R10G10B10A2_UNORM,
                 SDL_GPU_TEXTUREFORMAT_B5G6R5_UNORM, SDL_GPU_TEXTUREFORMAT_B5G5R5A1_UNORM,
                 SDL_GPU_TEXTUREFORMAT_B4G4R4A4_UNORM -> K_UNORM;
            case SDL_GPU_TEXTUREFORMAT_R8_SNORM, SDL_GPU_TEXTUREFORMAT_R8G8_SNORM, SDL_GPU_TEXTUREFORMAT_R8G8B8A8_SNORM,
                 SDL_GPU_TEXTUREFORMAT_R16_SNORM, SDL_GPU_TEXTUREFORMAT_R16G16_SNORM,
                 SDL_GPU_TEXTUREFORMAT_R16G16B16A16_SNORM -> K_SNORM;
            case SDL_GPU_TEXTUREFORMAT_R16_FLOAT, SDL_GPU_TEXTUREFORMAT_R16G16_FLOAT, SDL_GPU_TEXTUREFORMAT_R16G16B16A16_FLOAT,
                 SDL_GPU_TEXTUREFORMAT_R32_FLOAT, SDL_GPU_TEXTUREFORMAT_R32G32_FLOAT, SDL_GPU_TEXTUREFORMAT_R32G32B32A32_FLOAT,
                 SDL_GPU_TEXTUREFORMAT_R11G11B10_UFLOAT -> K_FLOAT;
            case SDL_GPU_TEXTUREFORMAT_R8_UINT, SDL_GPU_TEXTUREFORMAT_R8G8_UINT, SDL_GPU_TEXTUREFORMAT_R8G8B8A8_UINT,
                 SDL_GPU_TEXTUREFORMAT_R16_UINT, SDL_GPU_TEXTUREFORMAT_R16G16_UINT, SDL_GPU_TEXTUREFORMAT_R16G16B16A16_UINT,
                 SDL_GPU_TEXTUREFORMAT_R32_UINT, SDL_GPU_TEXTUREFORMAT_R32G32_UINT, SDL_GPU_TEXTUREFORMAT_R32G32B32A32_UINT -> K_UINT;
            case SDL_GPU_TEXTUREFORMAT_R8_INT, SDL_GPU_TEXTUREFORMAT_R8G8_INT, SDL_GPU_TEXTUREFORMAT_R8G8B8A8_INT,
                 SDL_GPU_TEXTUREFORMAT_R16_INT, SDL_GPU_TEXTUREFORMAT_R16G16_INT, SDL_GPU_TEXTUREFORMAT_R16G16B16A16_INT,
                 SDL_GPU_TEXTUREFORMAT_R32_INT, SDL_GPU_TEXTUREFORMAT_R32G32_INT, SDL_GPU_TEXTUREFORMAT_R32G32B32A32_INT -> K_SINT;
            default -> throw new IllegalArgumentException("Unsupported color readback source format " + fmt);
        };
    }

    private static long rawChannel(long t, int fmt, int ch) {
        return switch (fmt) {
            case SDL_GPU_TEXTUREFORMAT_R8_UNORM, SDL_GPU_TEXTUREFORMAT_R8G8_UNORM, SDL_GPU_TEXTUREFORMAT_R8G8B8A8_UNORM,
                 SDL_GPU_TEXTUREFORMAT_R8G8B8A8_UNORM_SRGB, SDL_GPU_TEXTUREFORMAT_R8_UINT, SDL_GPU_TEXTUREFORMAT_R8G8_UINT,
                 SDL_GPU_TEXTUREFORMAT_R8G8B8A8_UINT -> MemoryUtil.memGetByte(t + ch) & 0xFFL;
            case SDL_GPU_TEXTUREFORMAT_R8_SNORM, SDL_GPU_TEXTUREFORMAT_R8G8_SNORM, SDL_GPU_TEXTUREFORMAT_R8G8B8A8_SNORM,
                 SDL_GPU_TEXTUREFORMAT_R8_INT, SDL_GPU_TEXTUREFORMAT_R8G8_INT, SDL_GPU_TEXTUREFORMAT_R8G8B8A8_INT -> MemoryUtil.memGetByte(t + ch);
            case SDL_GPU_TEXTUREFORMAT_B8G8R8A8_UNORM, SDL_GPU_TEXTUREFORMAT_B8G8R8A8_UNORM_SRGB -> MemoryUtil.memGetByte(t + bgraOffset(ch)) & 0xFFL;
            case SDL_GPU_TEXTUREFORMAT_A8_UNORM -> MemoryUtil.memGetByte(t) & 0xFFL;
            case SDL_GPU_TEXTUREFORMAT_R16_UNORM, SDL_GPU_TEXTUREFORMAT_R16G16_UNORM, SDL_GPU_TEXTUREFORMAT_R16G16B16A16_UNORM,
                 SDL_GPU_TEXTUREFORMAT_R16_UINT, SDL_GPU_TEXTUREFORMAT_R16G16_UINT,
                 SDL_GPU_TEXTUREFORMAT_R16G16B16A16_UINT -> MemoryUtil.memGetShort(t + ((long) ch << 1)) & 0xFFFFL;
            case SDL_GPU_TEXTUREFORMAT_R16_SNORM, SDL_GPU_TEXTUREFORMAT_R16G16_SNORM, SDL_GPU_TEXTUREFORMAT_R16G16B16A16_SNORM,
                 SDL_GPU_TEXTUREFORMAT_R16_INT, SDL_GPU_TEXTUREFORMAT_R16G16_INT,
                 SDL_GPU_TEXTUREFORMAT_R16G16B16A16_INT -> MemoryUtil.memGetShort(t + ((long) ch << 1));
            case SDL_GPU_TEXTUREFORMAT_R32_UINT, SDL_GPU_TEXTUREFORMAT_R32G32_UINT,
                 SDL_GPU_TEXTUREFORMAT_R32G32B32A32_UINT -> MemoryUtil.memGetInt(t + ((long) ch << 2)) & 0xFFFFFFFFL;
            case SDL_GPU_TEXTUREFORMAT_R32_INT, SDL_GPU_TEXTUREFORMAT_R32G32_INT,
                 SDL_GPU_TEXTUREFORMAT_R32G32B32A32_INT -> MemoryUtil.memGetInt(t + ((long) ch << 2));
            case SDL_GPU_TEXTUREFORMAT_R10G10B10A2_UNORM -> {
                final int w = MemoryUtil.memGetInt(t);
                yield ch == CH_A ? (w >>> 30) : ((w >>> (10 * ch)) & 0x3FF);
            }
            case SDL_GPU_TEXTUREFORMAT_B5G6R5_UNORM -> {
                final int w = MemoryUtil.memGetShort(t) & 0xFFFF;
                yield switch (ch) {
                    case CH_R -> (w >>> 11) & 0x1F;
                    case CH_G -> (w >>> 5) & 0x3F;
                    default -> w & 0x1F;
                };
            }
            case SDL_GPU_TEXTUREFORMAT_B5G5R5A1_UNORM -> {
                final int w = MemoryUtil.memGetShort(t) & 0xFFFF;
                yield switch (ch) {
                    case CH_R -> (w >>> 10) & 0x1F;
                    case CH_G -> (w >>> 5) & 0x1F;
                    case CH_B -> w & 0x1F;
                    default -> (w >>> 15) & 1;
                };
            }
            case SDL_GPU_TEXTUREFORMAT_B4G4R4A4_UNORM -> {
                final int w = MemoryUtil.memGetShort(t) & 0xFFFF;
                yield switch (ch) {
                    case CH_R -> (w >>> 8) & 0xF;
                    case CH_G -> (w >>> 4) & 0xF;
                    case CH_B -> w & 0xF;
                    default -> (w >>> 12) & 0xF;
                };
            }
            default -> throw new IllegalArgumentException("Unsupported color readback source format " + fmt);
        };
    }

    private static float floatChannel(long t, int fmt, int kind, int ch, int bits) {
        if (kind == K_UNORM) return unormToFloat(rawChannel(t, fmt, ch), bits);
        if (kind == K_SNORM) return snormToFloat(rawChannel(t, fmt, ch), bits);
        return switch (fmt) {
            case SDL_GPU_TEXTUREFORMAT_R16_FLOAT, SDL_GPU_TEXTUREFORMAT_R16G16_FLOAT,
                 SDL_GPU_TEXTUREFORMAT_R16G16B16A16_FLOAT -> halfToFloat(MemoryUtil.memGetShort(t + ((long) ch << 1)) & 0xFFFF);
            case SDL_GPU_TEXTUREFORMAT_R11G11B10_UFLOAT -> {
                final int w = MemoryUtil.memGetInt(t);
                yield switch (ch) {
                    case CH_R -> uf11ToFloat(w & 0x7FF);
                    case CH_G -> uf11ToFloat((w >>> 11) & 0x7FF);
                    default -> uf10ToFloat((w >>> 22) & 0x3FF);
                };
            }
            default -> MemoryUtil.memGetFloat(t + ((long) ch << 2));
        };
    }

    private static int bgraOffset(int ch) {
        return ch == CH_R ? 2 : (ch == CH_B ? 0 : ch);
    }

    private static boolean isUnorm8x4(int fmt) {
        return fmt == SDL_GPU_TEXTUREFORMAT_R8G8B8A8_UNORM || fmt == SDL_GPU_TEXTUREFORMAT_R8G8B8A8_UNORM_SRGB
            || fmt == SDL_GPU_TEXTUREFORMAT_B8G8R8A8_UNORM || fmt == SDL_GPU_TEXTUREFORMAT_B8G8R8A8_UNORM_SRGB;
    }

    private static boolean byteShuffleType(int format, int type) {
        if (type == GL11.GL_UNSIGNED_BYTE) return !isLuminanceFormat(format);
        return (type == GL12.GL_UNSIGNED_INT_8_8_8_8 || type == GL12.GL_UNSIGNED_INT_8_8_8_8_REV)
            && (format == GL11.GL_RGBA || format == GL12.GL_BGRA);
    }

    private static int shuffleOffset(int fmt, int swizzle, int n, boolean reversed, int outByte) {
        if (outByte >= n) return 0;
        final int comp = reversed ? 3 - outByte : outByte;
        final int ch = (swizzle >> (comp << 2)) & 15;
        final boolean bgra = fmt == SDL_GPU_TEXTUREFORMAT_B8G8R8A8_UNORM || fmt == SDL_GPU_TEXTUREFORMAT_B8G8R8A8_UNORM_SRGB;
        return bgra ? bgraOffset(ch) : ch;
    }

    private static boolean layoutMatches(int fmt, int format, int type) {
        return switch (fmt) {
            case SDL_GPU_TEXTUREFORMAT_R8_UNORM -> format == GL11.GL_RED && type == GL11.GL_UNSIGNED_BYTE;
            case SDL_GPU_TEXTUREFORMAT_R8G8_UNORM -> format == GL30.GL_RG && type == GL11.GL_UNSIGNED_BYTE;
            case SDL_GPU_TEXTUREFORMAT_R8G8B8A8_UNORM, SDL_GPU_TEXTUREFORMAT_R8G8B8A8_UNORM_SRGB ->
                format == GL11.GL_RGBA && (type == GL11.GL_UNSIGNED_BYTE || (LITTLE_ENDIAN && type == GL12.GL_UNSIGNED_INT_8_8_8_8_REV));
            case SDL_GPU_TEXTUREFORMAT_B8G8R8A8_UNORM, SDL_GPU_TEXTUREFORMAT_B8G8R8A8_UNORM_SRGB ->
                format == GL12.GL_BGRA && (type == GL11.GL_UNSIGNED_BYTE || (LITTLE_ENDIAN && type == GL12.GL_UNSIGNED_INT_8_8_8_8_REV));
            case SDL_GPU_TEXTUREFORMAT_A8_UNORM -> format == GL11.GL_ALPHA && type == GL11.GL_UNSIGNED_BYTE;
            case SDL_GPU_TEXTUREFORMAT_R16_UNORM -> format == GL11.GL_RED && type == GL11.GL_UNSIGNED_SHORT;
            case SDL_GPU_TEXTUREFORMAT_R16G16_UNORM -> format == GL30.GL_RG && type == GL11.GL_UNSIGNED_SHORT;
            case SDL_GPU_TEXTUREFORMAT_R16G16B16A16_UNORM -> format == GL11.GL_RGBA && type == GL11.GL_UNSIGNED_SHORT;
            case SDL_GPU_TEXTUREFORMAT_R8_SNORM -> format == GL11.GL_RED && type == GL11.GL_BYTE;
            case SDL_GPU_TEXTUREFORMAT_R8G8_SNORM -> format == GL30.GL_RG && type == GL11.GL_BYTE;
            case SDL_GPU_TEXTUREFORMAT_R8G8B8A8_SNORM -> format == GL11.GL_RGBA && type == GL11.GL_BYTE;
            case SDL_GPU_TEXTUREFORMAT_R16_SNORM -> format == GL11.GL_RED && type == GL11.GL_SHORT;
            case SDL_GPU_TEXTUREFORMAT_R16G16_SNORM -> format == GL30.GL_RG && type == GL11.GL_SHORT;
            case SDL_GPU_TEXTUREFORMAT_R16G16B16A16_SNORM -> format == GL11.GL_RGBA && type == GL11.GL_SHORT;
            case SDL_GPU_TEXTUREFORMAT_R16_FLOAT -> format == GL11.GL_RED && type == GL30.GL_HALF_FLOAT;
            case SDL_GPU_TEXTUREFORMAT_R16G16_FLOAT -> format == GL30.GL_RG && type == GL30.GL_HALF_FLOAT;
            case SDL_GPU_TEXTUREFORMAT_R16G16B16A16_FLOAT -> format == GL11.GL_RGBA && type == GL30.GL_HALF_FLOAT;
            case SDL_GPU_TEXTUREFORMAT_R32_FLOAT -> format == GL11.GL_RED && type == GL11.GL_FLOAT;
            case SDL_GPU_TEXTUREFORMAT_R32G32_FLOAT -> format == GL30.GL_RG && type == GL11.GL_FLOAT;
            case SDL_GPU_TEXTUREFORMAT_R32G32B32A32_FLOAT -> format == GL11.GL_RGBA && type == GL11.GL_FLOAT;
            case SDL_GPU_TEXTUREFORMAT_R11G11B10_UFLOAT -> format == GL11.GL_RGB && type == GL30.GL_UNSIGNED_INT_10F_11F_11F_REV;
            case SDL_GPU_TEXTUREFORMAT_R10G10B10A2_UNORM -> format == GL11.GL_RGBA && type == GL12.GL_UNSIGNED_INT_2_10_10_10_REV;
            case SDL_GPU_TEXTUREFORMAT_B5G6R5_UNORM -> format == GL11.GL_RGB && type == GL12.GL_UNSIGNED_SHORT_5_6_5;
            case SDL_GPU_TEXTUREFORMAT_B5G5R5A1_UNORM -> format == GL12.GL_BGRA && type == GL12.GL_UNSIGNED_SHORT_1_5_5_5_REV;
            case SDL_GPU_TEXTUREFORMAT_B4G4R4A4_UNORM -> format == GL12.GL_BGRA && type == GL12.GL_UNSIGNED_SHORT_4_4_4_4_REV;
            case SDL_GPU_TEXTUREFORMAT_R8_UINT -> format == GL30.GL_RED_INTEGER && type == GL11.GL_UNSIGNED_BYTE;
            case SDL_GPU_TEXTUREFORMAT_R8G8_UINT -> format == GL30.GL_RG_INTEGER && type == GL11.GL_UNSIGNED_BYTE;
            case SDL_GPU_TEXTUREFORMAT_R8G8B8A8_UINT -> format == GL30.GL_RGBA_INTEGER && type == GL11.GL_UNSIGNED_BYTE;
            case SDL_GPU_TEXTUREFORMAT_R16_UINT -> format == GL30.GL_RED_INTEGER && type == GL11.GL_UNSIGNED_SHORT;
            case SDL_GPU_TEXTUREFORMAT_R16G16_UINT -> format == GL30.GL_RG_INTEGER && type == GL11.GL_UNSIGNED_SHORT;
            case SDL_GPU_TEXTUREFORMAT_R16G16B16A16_UINT -> format == GL30.GL_RGBA_INTEGER && type == GL11.GL_UNSIGNED_SHORT;
            case SDL_GPU_TEXTUREFORMAT_R32_UINT -> format == GL30.GL_RED_INTEGER && type == GL11.GL_UNSIGNED_INT;
            case SDL_GPU_TEXTUREFORMAT_R32G32_UINT -> format == GL30.GL_RG_INTEGER && type == GL11.GL_UNSIGNED_INT;
            case SDL_GPU_TEXTUREFORMAT_R32G32B32A32_UINT -> format == GL30.GL_RGBA_INTEGER && type == GL11.GL_UNSIGNED_INT;
            case SDL_GPU_TEXTUREFORMAT_R8_INT -> format == GL30.GL_RED_INTEGER && type == GL11.GL_BYTE;
            case SDL_GPU_TEXTUREFORMAT_R8G8_INT -> format == GL30.GL_RG_INTEGER && type == GL11.GL_BYTE;
            case SDL_GPU_TEXTUREFORMAT_R8G8B8A8_INT -> format == GL30.GL_RGBA_INTEGER && type == GL11.GL_BYTE;
            case SDL_GPU_TEXTUREFORMAT_R16_INT -> format == GL30.GL_RED_INTEGER && type == GL11.GL_SHORT;
            case SDL_GPU_TEXTUREFORMAT_R16G16_INT -> format == GL30.GL_RG_INTEGER && type == GL11.GL_SHORT;
            case SDL_GPU_TEXTUREFORMAT_R16G16B16A16_INT -> format == GL30.GL_RGBA_INTEGER && type == GL11.GL_SHORT;
            case SDL_GPU_TEXTUREFORMAT_R32_INT -> format == GL30.GL_RED_INTEGER && type == GL11.GL_INT;
            case SDL_GPU_TEXTUREFORMAT_R32G32_INT -> format == GL30.GL_RG_INTEGER && type == GL11.GL_INT;
            case SDL_GPU_TEXTUREFORMAT_R32G32B32A32_INT -> format == GL30.GL_RGBA_INTEGER && type == GL11.GL_INT;
            default -> false;
        };
    }

    private static int packedBits(int type, int i) {
        return switch (type) {
            case GL12.GL_UNSIGNED_BYTE_3_3_2 -> i == 2 ? 2 : 3;
            case GL12.GL_UNSIGNED_BYTE_2_3_3_REV -> i == 2 ? 2 : 3;
            case GL12.GL_UNSIGNED_SHORT_5_6_5, GL12.GL_UNSIGNED_SHORT_5_6_5_REV -> i == 1 ? 6 : 5;
            case GL12.GL_UNSIGNED_SHORT_4_4_4_4, GL12.GL_UNSIGNED_SHORT_4_4_4_4_REV -> 4;
            case GL12.GL_UNSIGNED_SHORT_5_5_5_1, GL12.GL_UNSIGNED_SHORT_1_5_5_5_REV -> i == 3 ? 1 : 5;
            case GL12.GL_UNSIGNED_INT_8_8_8_8, GL12.GL_UNSIGNED_INT_8_8_8_8_REV -> 8;
            default -> i == 3 ? 2 : 10;
        };
    }

    private static int packedShift(int type, int i) {
        return switch (type) {
            case GL12.GL_UNSIGNED_BYTE_3_3_2 -> i == 0 ? 5 : (i == 1 ? 2 : 0);
            case GL12.GL_UNSIGNED_BYTE_2_3_3_REV -> i * 3;
            case GL12.GL_UNSIGNED_SHORT_5_6_5 -> i == 0 ? 11 : (i == 1 ? 5 : 0);
            case GL12.GL_UNSIGNED_SHORT_5_6_5_REV -> i == 0 ? 0 : (i == 1 ? 5 : 11);
            case GL12.GL_UNSIGNED_SHORT_4_4_4_4 -> 12 - 4 * i;
            case GL12.GL_UNSIGNED_SHORT_4_4_4_4_REV -> 4 * i;
            case GL12.GL_UNSIGNED_SHORT_5_5_5_1 -> i == 3 ? 0 : 11 - 5 * i;
            case GL12.GL_UNSIGNED_SHORT_1_5_5_5_REV -> 5 * i;
            case GL12.GL_UNSIGNED_INT_8_8_8_8 -> 24 - 8 * i;
            case GL12.GL_UNSIGNED_INT_8_8_8_8_REV -> 8 * i;
            case GL12.GL_UNSIGNED_INT_10_10_10_2 -> i == 3 ? 0 : 22 - 10 * i;
            default -> 10 * i;
        };
    }

    private static boolean isIntegerFormat(int format) {
        return switch (format) {
            case GL30.GL_RED_INTEGER, GL30.GL_GREEN_INTEGER, GL30.GL_BLUE_INTEGER, GL30.GL_ALPHA_INTEGER,
                 GL30.GL_RG_INTEGER, GL30.GL_RGB_INTEGER, GL30.GL_BGR_INTEGER, GL30.GL_RGBA_INTEGER,
                 GL30.GL_BGRA_INTEGER -> true;
            default -> false;
        };
    }

    private static boolean isLuminanceFormat(int format) {
        return format == GL11.GL_LUMINANCE || format == GL11.GL_LUMINANCE_ALPHA;
    }

    private static boolean isColorFormat(int format) {
        return format != GL11.GL_DEPTH_COMPONENT && format != GL11.GL_STENCIL_INDEX && format != GL30.GL_DEPTH_STENCIL;
    }

    private static int formatComponents(int format) {
        return switch (format) {
            case GL30.GL_RG, GL30.GL_RG_INTEGER, GL11.GL_LUMINANCE_ALPHA -> 2;
            case GL11.GL_RGB, GL12.GL_BGR, GL30.GL_RGB_INTEGER, GL30.GL_BGR_INTEGER -> 3;
            case GL11.GL_RGBA, GL12.GL_BGRA, GL30.GL_RGBA_INTEGER, GL30.GL_BGRA_INTEGER -> 4;
            default -> 1;
        };
    }

    public static int glBaseFormat(int internalFormat) {
        return switch (internalFormat) {
            case GL11.GL_ALPHA, GL11.GL_ALPHA4, GL11.GL_ALPHA8, GL11.GL_ALPHA12, GL11.GL_ALPHA16,
                 GL13.GL_COMPRESSED_ALPHA, ARBTextureFloat.GL_ALPHA16F_ARB, ARBTextureFloat.GL_ALPHA32F_ARB,
                 EXTTextureSnorm.GL_ALPHA_SNORM, EXTTextureSnorm.GL_ALPHA8_SNORM, EXTTextureSnorm.GL_ALPHA16_SNORM,
                 EXTTextureInteger.GL_ALPHA8UI_EXT, EXTTextureInteger.GL_ALPHA16UI_EXT, EXTTextureInteger.GL_ALPHA32UI_EXT,
                 EXTTextureInteger.GL_ALPHA8I_EXT, EXTTextureInteger.GL_ALPHA16I_EXT, EXTTextureInteger.GL_ALPHA32I_EXT -> GL11.GL_ALPHA;
            case 1, GL11.GL_LUMINANCE, GL11.GL_LUMINANCE4, GL11.GL_LUMINANCE8, GL11.GL_LUMINANCE12, GL11.GL_LUMINANCE16,
                 GL13.GL_COMPRESSED_LUMINANCE, ARBTextureFloat.GL_LUMINANCE16F_ARB, ARBTextureFloat.GL_LUMINANCE32F_ARB,
                 EXTTextureSnorm.GL_LUMINANCE_SNORM, EXTTextureSnorm.GL_LUMINANCE8_SNORM, EXTTextureSnorm.GL_LUMINANCE16_SNORM,
                 GL21.GL_SLUMINANCE, GL21.GL_SLUMINANCE8, GL21.GL_COMPRESSED_SLUMINANCE,
                 EXTTextureInteger.GL_LUMINANCE8UI_EXT, EXTTextureInteger.GL_LUMINANCE16UI_EXT, EXTTextureInteger.GL_LUMINANCE32UI_EXT,
                 EXTTextureInteger.GL_LUMINANCE8I_EXT, EXTTextureInteger.GL_LUMINANCE16I_EXT, EXTTextureInteger.GL_LUMINANCE32I_EXT -> GL11.GL_LUMINANCE;
            case 2, GL11.GL_LUMINANCE_ALPHA, GL11.GL_LUMINANCE4_ALPHA4, GL11.GL_LUMINANCE6_ALPHA2, GL11.GL_LUMINANCE8_ALPHA8,
                 GL11.GL_LUMINANCE12_ALPHA4, GL11.GL_LUMINANCE12_ALPHA12, GL11.GL_LUMINANCE16_ALPHA16,
                 GL13.GL_COMPRESSED_LUMINANCE_ALPHA, ARBTextureFloat.GL_LUMINANCE_ALPHA16F_ARB, ARBTextureFloat.GL_LUMINANCE_ALPHA32F_ARB,
                 EXTTextureSnorm.GL_LUMINANCE_ALPHA_SNORM, EXTTextureSnorm.GL_LUMINANCE8_ALPHA8_SNORM, EXTTextureSnorm.GL_LUMINANCE16_ALPHA16_SNORM,
                 GL21.GL_SLUMINANCE_ALPHA, GL21.GL_SLUMINANCE8_ALPHA8, GL21.GL_COMPRESSED_SLUMINANCE_ALPHA,
                 EXTTextureInteger.GL_LUMINANCE_ALPHA8UI_EXT, EXTTextureInteger.GL_LUMINANCE_ALPHA16UI_EXT, EXTTextureInteger.GL_LUMINANCE_ALPHA32UI_EXT,
                 EXTTextureInteger.GL_LUMINANCE_ALPHA8I_EXT, EXTTextureInteger.GL_LUMINANCE_ALPHA16I_EXT, EXTTextureInteger.GL_LUMINANCE_ALPHA32I_EXT -> GL11.GL_LUMINANCE_ALPHA;
            case GL11.GL_INTENSITY, GL11.GL_INTENSITY4, GL11.GL_INTENSITY8, GL11.GL_INTENSITY12, GL11.GL_INTENSITY16,
                 GL13.GL_COMPRESSED_INTENSITY, ARBTextureFloat.GL_INTENSITY16F_ARB, ARBTextureFloat.GL_INTENSITY32F_ARB,
                 EXTTextureSnorm.GL_INTENSITY_SNORM, EXTTextureSnorm.GL_INTENSITY8_SNORM, EXTTextureSnorm.GL_INTENSITY16_SNORM,
                 EXTTextureInteger.GL_INTENSITY8UI_EXT, EXTTextureInteger.GL_INTENSITY16UI_EXT, EXTTextureInteger.GL_INTENSITY32UI_EXT,
                 EXTTextureInteger.GL_INTENSITY8I_EXT, EXTTextureInteger.GL_INTENSITY16I_EXT, EXTTextureInteger.GL_INTENSITY32I_EXT -> GL11.GL_INTENSITY;
            case 3, GL11.GL_RGB, GL11.GL_R3_G3_B2, GL11.GL_RGB4, GL11.GL_RGB5, GL11.GL_RGB8, GL11.GL_RGB10, GL11.GL_RGB12, GL11.GL_RGB16,
                 GL41.GL_RGB565, GL13.GL_COMPRESSED_RGB, GL21.GL_SRGB, GL21.GL_SRGB8, GL21.GL_COMPRESSED_SRGB,
                 GL30.GL_RGB16F, GL30.GL_RGB32F, EXTTextureSnorm.GL_RGB_SNORM, GL31.GL_RGB8_SNORM, GL31.GL_RGB16_SNORM,
                 GL30.GL_RGB8UI, GL30.GL_RGB16UI, GL30.GL_RGB32UI, GL30.GL_RGB8I, GL30.GL_RGB16I, GL30.GL_RGB32I,
                 GL30.GL_RGB9_E5, GL30.GL_R11F_G11F_B10F -> GL11.GL_RGB;
            case 4, GL11.GL_RGBA, GL11.GL_RGBA2, GL11.GL_RGBA4, GL11.GL_RGB5_A1, GL11.GL_RGBA8, GL11.GL_RGB10_A2, GL11.GL_RGBA12, GL11.GL_RGBA16,
                 GL12.GL_BGRA, EXTTextureStorage.GL_BGRA8_EXT, GL13.GL_COMPRESSED_RGBA,
                 GL21.GL_SRGB_ALPHA, GL21.GL_SRGB8_ALPHA8, GL21.GL_COMPRESSED_SRGB_ALPHA,
                 GL30.GL_RGBA16F, GL30.GL_RGBA32F, EXTTextureSnorm.GL_RGBA_SNORM, GL31.GL_RGBA8_SNORM, GL31.GL_RGBA16_SNORM,
                 GL30.GL_RGBA8UI, GL30.GL_RGBA16UI, GL30.GL_RGBA32UI, GL30.GL_RGBA8I, GL30.GL_RGBA16I, GL30.GL_RGBA32I,
                 GL33.GL_RGB10_A2UI -> GL11.GL_RGBA;
            case GL11.GL_RED, GL30.GL_R8, GL30.GL_R16, GL30.GL_R16F, GL30.GL_R32F, GL30.GL_COMPRESSED_RED,
                 GL30.GL_R8I, GL30.GL_R8UI, GL30.GL_R16I, GL30.GL_R16UI, GL30.GL_R32I, GL30.GL_R32UI,
                 EXTTextureSnorm.GL_RED_SNORM, GL31.GL_R8_SNORM, GL31.GL_R16_SNORM, EXTTextureSRGBR8.GL_SR8_EXT -> GL11.GL_RED;
            case GL30.GL_RG, GL30.GL_RG8, GL30.GL_RG16, GL30.GL_RG16F, GL30.GL_RG32F, GL30.GL_COMPRESSED_RG,
                 GL30.GL_RG8I, GL30.GL_RG8UI, GL30.GL_RG16I, GL30.GL_RG16UI, GL30.GL_RG32I, GL30.GL_RG32UI,
                 EXTTextureSnorm.GL_RG_SNORM, GL31.GL_RG8_SNORM, GL31.GL_RG16_SNORM, EXTTextureSRGBRG8.GL_SRG8_EXT -> GL30.GL_RG;
            case GL11.GL_DEPTH_COMPONENT, GL14.GL_DEPTH_COMPONENT16, GL14.GL_DEPTH_COMPONENT24, GL14.GL_DEPTH_COMPONENT32,
                 GL30.GL_DEPTH_COMPONENT32F -> GL11.GL_DEPTH_COMPONENT;
            case GL30.GL_DEPTH_STENCIL, GL30.GL_DEPTH24_STENCIL8, GL30.GL_DEPTH32F_STENCIL8 -> GL30.GL_DEPTH_STENCIL;
            case GL11.GL_STENCIL_INDEX, GL30.GL_STENCIL_INDEX1, GL30.GL_STENCIL_INDEX4, GL30.GL_STENCIL_INDEX8,
                 GL30.GL_STENCIL_INDEX16 -> GL11.GL_STENCIL_INDEX;
            default -> -1;
        };
    }

    private static int rebaseSwizzle(int baseFormat) {
        return switch (baseFormat) {
            case GL11.GL_RGBA -> REBASE_IDENTITY;
            case GL11.GL_RGB -> CH_R | (CH_G << 4) | (CH_B << 8) | (CH_ONE << 12);
            case GL30.GL_RG -> CH_R | (CH_G << 4) | (CH_ZERO << 8) | (CH_ONE << 12);
            case GL11.GL_RED, GL11.GL_LUMINANCE, GL11.GL_INTENSITY -> CH_R | (CH_ZERO << 4) | (CH_ZERO << 8) | (CH_ONE << 12);
            case GL11.GL_GREEN -> CH_ZERO | (CH_G << 4) | (CH_ZERO << 8) | (CH_ONE << 12);
            case GL11.GL_BLUE -> CH_ZERO | (CH_ZERO << 4) | (CH_B << 8) | (CH_ONE << 12);
            case GL11.GL_ALPHA -> CH_ZERO | (CH_ZERO << 4) | (CH_ZERO << 8) | (CH_A << 12);
            case GL11.GL_LUMINANCE_ALPHA -> CH_R | (CH_ZERO << 4) | (CH_ZERO << 8) | (CH_A << 12);
            default -> throw new IllegalArgumentException("GL_INVALID_OPERATION: color readback from base format 0x" + Integer.toHexString(baseFormat));
        };
    }

    private static boolean rebaseChangesRequested(int swizzle, int n, int rebase) {
        for (int i = 0; i < n; i++) {
            final int ch = (swizzle >> (i << 2)) & 15;
            if (ch <= CH_A && ((rebase >> (ch << 2)) & 15) != ch) return true;
        }
        return false;
    }

    private static int applyRebase(int swizzle, int n, int rebase, boolean rgBase) {
        int out = 0;
        for (int i = 0; i < n; i++) {
            int ch = (swizzle >> (i << 2)) & 15;
            if (ch <= CH_A) ch = (rebase >> (ch << 2)) & 15;
            else if (ch == CH_L && rgBase) ch = CH_L_RG;
            out |= ch << (i << 2);
        }
        return out;
    }

    private static int formatSwizzle(int format, boolean luminanceSum) {
        final int l = luminanceSum ? CH_L : CH_R;
        return switch (format) {
            case GL11.GL_GREEN, GL30.GL_GREEN_INTEGER -> CH_G;
            case GL11.GL_BLUE, GL30.GL_BLUE_INTEGER -> CH_B;
            case GL11.GL_ALPHA, GL30.GL_ALPHA_INTEGER -> CH_A;
            case GL11.GL_LUMINANCE -> l;
            case GL11.GL_LUMINANCE_ALPHA -> l | (CH_A << 4);
            case GL30.GL_RG, GL30.GL_RG_INTEGER -> CH_R | (CH_G << 4);
            case GL11.GL_RGB, GL30.GL_RGB_INTEGER -> CH_R | (CH_G << 4) | (CH_B << 8);
            case GL12.GL_BGR, GL30.GL_BGR_INTEGER -> CH_B | (CH_G << 4) | (CH_R << 8);
            case GL11.GL_RGBA, GL30.GL_RGBA_INTEGER -> CH_R | (CH_G << 4) | (CH_B << 8) | (CH_A << 12);
            case GL12.GL_BGRA, GL30.GL_BGRA_INTEGER -> CH_B | (CH_G << 4) | (CH_R << 8) | (CH_A << 12);
            default -> CH_R;
        };
    }

    private static boolean isBaseType(int type) {
        return switch (type) {
            case GL11.GL_BYTE, GL11.GL_UNSIGNED_BYTE, GL11.GL_SHORT, GL11.GL_UNSIGNED_SHORT,
                 GL11.GL_INT, GL11.GL_UNSIGNED_INT, GL11.GL_FLOAT, GL30.GL_HALF_FLOAT -> true;
            default -> false;
        };
    }

    private static boolean isIntegerBaseType(int type) {
        return isBaseType(type) && type != GL11.GL_FLOAT && type != GL30.GL_HALF_FLOAT;
    }

    private static boolean isKnownFormat(int format) {
        return switch (format) {
            case GL11.GL_RED, GL11.GL_GREEN, GL11.GL_BLUE, GL11.GL_ALPHA, GL30.GL_RG, GL11.GL_RGB, GL12.GL_BGR,
                 GL11.GL_RGBA, GL12.GL_BGRA, GL11.GL_LUMINANCE, GL11.GL_LUMINANCE_ALPHA,
                 GL11.GL_DEPTH_COMPONENT, GL11.GL_STENCIL_INDEX, GL30.GL_DEPTH_STENCIL -> true;
            default -> isIntegerFormat(format);
        };
    }

    private static boolean isKnownType(int type) {
        return isBaseType(type) || GLTypes.packedTexelBytes(type) != 0;
    }

    private static int checkFormatAndType(int format, int type) {
        if (!isKnownFormat(format) || !isKnownType(type)) return INVALID_ENUM;
        if (format == GL30.GL_DEPTH_STENCIL && type != GL30.GL_UNSIGNED_INT_24_8 && type != GL30.GL_FLOAT_32_UNSIGNED_INT_24_8_REV) {
            return INVALID_ENUM;
        }
        switch (type) {
            case GL12.GL_UNSIGNED_BYTE_3_3_2, GL12.GL_UNSIGNED_BYTE_2_3_3_REV,
                 GL12.GL_UNSIGNED_SHORT_5_6_5, GL12.GL_UNSIGNED_SHORT_5_6_5_REV -> {
                return (format == GL11.GL_RGB || format == GL30.GL_RGB_INTEGER) ? OK : INVALID_OPERATION;
            }
            case GL12.GL_UNSIGNED_SHORT_4_4_4_4, GL12.GL_UNSIGNED_SHORT_4_4_4_4_REV,
                 GL12.GL_UNSIGNED_INT_8_8_8_8, GL12.GL_UNSIGNED_INT_8_8_8_8_REV,
                 GL12.GL_UNSIGNED_SHORT_5_5_5_1, GL12.GL_UNSIGNED_SHORT_1_5_5_5_REV,
                 GL12.GL_UNSIGNED_INT_10_10_10_2, GL12.GL_UNSIGNED_INT_2_10_10_10_REV -> {
                return (format == GL11.GL_RGBA || format == GL12.GL_BGRA
                    || format == GL30.GL_RGBA_INTEGER || format == GL30.GL_BGRA_INTEGER) ? OK : INVALID_OPERATION;
            }
            case GL30.GL_UNSIGNED_INT_24_8, GL30.GL_FLOAT_32_UNSIGNED_INT_24_8_REV -> {
                return format == GL30.GL_DEPTH_STENCIL ? OK : INVALID_OPERATION;
            }
            case GL30.GL_UNSIGNED_INT_10F_11F_11F_REV -> {
                return format == GL11.GL_RGB ? OK : INVALID_OPERATION;
            }
            case GL30.GL_UNSIGNED_INT_5_9_9_9_REV -> {
                return format == GL11.GL_RGB ? OK : INVALID_ENUM;
            }
            default -> {
                if (isIntegerFormat(format)) return isIntegerBaseType(type) ? OK : INVALID_ENUM;
                return isBaseType(type) ? OK : INVALID_ENUM;
            }
        }
    }
}
