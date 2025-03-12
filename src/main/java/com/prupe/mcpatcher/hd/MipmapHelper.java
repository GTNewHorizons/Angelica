package com.prupe.mcpatcher.hd;

import com.prupe.mcpatcher.MCLogger;
import com.prupe.mcpatcher.mal.resource.GLAPI;
import com.prupe.mcpatcher.mal.resource.PropertiesFile;
import com.prupe.mcpatcher.mal.resource.TexturePackAPI;
import jss.notfine.config.MCPatcherForgeConfig;
import net.minecraft.util.ResourceLocation;
import org.lwjgl.opengl.EXTTextureFilterAnisotropic;
import org.lwjgl.opengl.EXTTextureLODBias;
import org.lwjgl.opengl.GL;
import org.lwjgl.opengl.GL11;
import org.lwjgl.opengl.GL12;
import org.lwjgl.util.glu.GLU;

import java.math.BigInteger;
import java.nio.IntBuffer;
import java.util.HashMap;
import java.util.Map;

import static com.gtnewhorizons.angelica.glsm.GLStateManager.glGetFloat;
import static com.gtnewhorizons.angelica.glsm.managers.GLTextureManager.glGetTexParameteri;
import static com.gtnewhorizons.angelica.glsm.managers.GLTextureManager.glTexImage2D;
import static com.gtnewhorizons.angelica.glsm.managers.GLTextureManager.glTexParameterf;
import static com.gtnewhorizons.angelica.glsm.managers.GLTextureManager.glTexParameteri;

public class MipmapHelper {

    private static final MCLogger logger = MCLogger.getLogger(MCLogger.Category.EXTENDED_HD);

    private static final ResourceLocation MIPMAP_PROPERTIES = TexturePackAPI
        .newMCPatcherResourceLocation("mipmap.properties");

    static final int TEX_FORMAT = GL12.GL_BGRA;
    static final int TEX_DATA_TYPE = GL12.GL_UNSIGNED_INT_8_8_8_8_REV;

    private static final boolean mipmapSupported;
    static final boolean mipmapEnabled = MCPatcherForgeConfig.ExtendedHD.mipmap;
    static final int maxMipmapLevel = MCPatcherForgeConfig.ExtendedHD.maxMipMapLevel;
    private static final boolean useMipmap;

    private static final boolean anisoSupported;
    static final int anisoLevel;
    private static final int anisoMax;

    private static final boolean lodSupported;
    private static final int lodBias;

    private static final Map<String, Boolean> mipmapType = new HashMap<>();

    static {
        mipmapSupported = GL.getCapabilities().OpenGL12;
        useMipmap = mipmapSupported && mipmapEnabled && maxMipmapLevel > 0;

        anisoSupported = GL.getCapabilities().GL_EXT_texture_filter_anisotropic;
        if (anisoSupported) {
            anisoMax = (int) glGetFloat(EXTTextureFilterAnisotropic.GL_MAX_TEXTURE_MAX_ANISOTROPY_EXT);
            checkGLError("glGetFloat(GL_MAX_TEXTURE_MAX_ANISOTROPY_EXT)");
            anisoLevel = Math.max(Math.min(MCPatcherForgeConfig.ExtendedHD.anisotropicFiltering, anisoMax), 1);
        } else {
            anisoMax = anisoLevel = 1;
        }
        lodSupported = true;
        lodBias = MCPatcherForgeConfig.ExtendedHD.lodBias;

        logger.config("mipmap: supported=%s, enabled=%s, level=%d", mipmapSupported, mipmapEnabled, maxMipmapLevel);
        logger.config("anisotropic: supported=%s, level=%d, max=%d", anisoSupported, anisoLevel, anisoMax);
        logger.config("lod bias: supported=%s, bias=%d", lodSupported, lodBias);
    }

    static void setupTexture(int width, int height, boolean blur, boolean clamp, String textureName) {
        int mipmaps = useMipmapsForTexture(textureName) ? getMipmapLevels(width, height, 1) : 0;
        logger.finer("setupTexture(%s) %dx%d %d mipmaps", textureName, width, height, mipmaps);
        int magFilter = blur ? GL11.GL_LINEAR : GL11.GL_NEAREST;
        int minFilter = mipmaps > 0 ? GL11.GL_NEAREST_MIPMAP_LINEAR : magFilter;
        int wrap = clamp ? GL11.GL_CLAMP : GL11.GL_REPEAT;
        if (mipmaps > 0) {
            glTexParameteri(GL11.GL_TEXTURE_2D, GL12.GL_TEXTURE_MAX_LEVEL, mipmaps);
            checkGLError("%s: set GL_TEXTURE_MAX_LEVEL = %d", textureName, mipmaps);
            if (anisoSupported && anisoLevel > 1) {
                glTexParameterf(
                    GL11.GL_TEXTURE_2D,
                    EXTTextureFilterAnisotropic.GL_TEXTURE_MAX_ANISOTROPY_EXT,
                    anisoLevel);
                checkGLError("%s: set GL_TEXTURE_MAX_ANISOTROPY_EXT = %f", textureName, anisoLevel);
            }
            if (lodSupported) {
                GL11.glTexEnvi(
                    EXTTextureLODBias.GL_TEXTURE_FILTER_CONTROL_EXT,
                    EXTTextureLODBias.GL_TEXTURE_LOD_BIAS_EXT,
                    lodBias);
                checkGLError("%s: set GL_TEXTURE_LOD_BIAS_EXT = %d", textureName, lodBias);
            }
        }
        glTexParameteri(GL11.GL_TEXTURE_2D, GL11.GL_TEXTURE_MIN_FILTER, minFilter);
        glTexParameteri(GL11.GL_TEXTURE_2D, GL11.GL_TEXTURE_MAG_FILTER, magFilter);
        glTexParameteri(GL11.GL_TEXTURE_2D, GL11.GL_TEXTURE_WRAP_S, wrap);
        glTexParameteri(GL11.GL_TEXTURE_2D, GL11.GL_TEXTURE_WRAP_T, wrap);
        for (int level = 0; level <= mipmaps; level++) {
            glTexImage2D(
                GL11.GL_TEXTURE_2D,
                level,
                GL11.GL_RGBA,
                width,
                height,
                0,
                TEX_FORMAT,
                TEX_DATA_TYPE,
                (IntBuffer) null);
            checkGLError("%s: glTexImage2D %dx%d level %d", textureName, width, height, level);
            width >>= 1;
            height >>= 1;
        }
    }

    public static void setupTexture(int glTexture, int width, int height, String textureName) {
        GLAPI.glBindTexture(glTexture);
        logger.finer("setupTexture(tilesheet %s, %d, %dx%d)", textureName, glTexture, width, height);
        setupTexture(width, height, false, false, textureName);
    }

    static void reset() {
        mipmapType.clear();
        mipmapType.put("terrain", true);
        mipmapType.put("items", false);
        PropertiesFile properties = PropertiesFile.get(logger, MIPMAP_PROPERTIES);
        if (properties != null) {
            for (Map.Entry<String, String> entry : properties.entrySet()) {
                String key = entry.getKey()
                    .trim();
                boolean value = Boolean.parseBoolean(
                    entry.getValue()
                        .trim()
                        .toLowerCase());
                if (key.endsWith(".png")) {
                    mipmapType.put(key, value);
                }
            }
        }
    }

    static boolean useMipmapsForTexture(String texture) {
        if (!useMipmap || texture == null) {
            return false;
        } else if (mipmapType.containsKey(texture)) {
            return mipmapType.get(texture);
        } else return !texture.contains("item") && !texture.startsWith("textures/colormap/")
            && !texture.startsWith("textures/environment/")
            && !texture.startsWith("textures/font/")
            && !texture.startsWith("textures/gui/")
            && !texture.startsWith("textures/map/")
            && !texture.startsWith("textures/misc/")
            && !texture.startsWith(TexturePackAPI.MCPATCHER_SUBDIR + "colormap/")
            && !texture.startsWith(TexturePackAPI.MCPATCHER_SUBDIR + "cit/")
            && !texture.startsWith(TexturePackAPI.MCPATCHER_SUBDIR + "dial/")
            && !texture.startsWith(TexturePackAPI.MCPATCHER_SUBDIR + "font/")
            && !texture.startsWith(TexturePackAPI.MCPATCHER_SUBDIR + "lightmap/")
            && !texture.startsWith(TexturePackAPI.MCPATCHER_SUBDIR + "sky/")
            &&
            // 1.5 stuff
            !texture.startsWith("%")
            && !texture.startsWith("##")
            && !texture.startsWith("/achievement/")
            && !texture.startsWith("/environment/")
            && !texture.startsWith("/font/")
            && !texture.startsWith("/gui/")
            && !texture.startsWith("/misc/")
            && !texture.startsWith("/terrain/")
            && !texture.startsWith("/title/");
    }

    static int getMipmapLevelsForCurrentTexture() {
        int filter = glGetTexParameteri(GL11.GL_TEXTURE_2D, GL11.GL_TEXTURE_MIN_FILTER);
        if (filter != GL11.GL_NEAREST_MIPMAP_LINEAR && filter != GL11.GL_NEAREST_MIPMAP_NEAREST) {
            return 0;
        }
        return Math.min(maxMipmapLevel, glGetTexParameteri(GL11.GL_TEXTURE_2D, GL12.GL_TEXTURE_MAX_LEVEL));
    }

    private static int gcd(int a, int b) {
        return BigInteger.valueOf(a)
            .gcd(BigInteger.valueOf(b))
            .intValue();
    }

    private static int getMipmapLevels(int width, int height, int minSize) {
        int size = gcd(width, height);
        int mipmap;
        for (mipmap = 0; size >= minSize && ((size & 1) == 0) && mipmap < maxMipmapLevel; size >>= 1, mipmap++) {}
        return mipmap;
    }

    static void scaleHalf(IntBuffer in, int w, int h, IntBuffer out, int rotate) {
        for (int i = 0; i < w / 2; i++) {
            for (int j = 0; j < h / 2; j++) {
                int k = w * 2 * j + 2 * i;
                int pixel00 = in.get(k);
                int pixel01 = in.get(k + 1);
                int pixel10 = in.get(k + w);
                int pixel11 = in.get(k + w + 1);
                if (rotate != 0) {
                    pixel00 = Integer.rotateLeft(pixel00, rotate);
                    pixel01 = Integer.rotateLeft(pixel01, rotate);
                    pixel10 = Integer.rotateLeft(pixel10, rotate);
                    pixel11 = Integer.rotateLeft(pixel11, rotate);
                }
                int pixel = average4RGBA(pixel00, pixel01, pixel10, pixel11);
                if (rotate != 0) {
                    pixel = Integer.rotateRight(pixel, rotate);
                }
                out.put(w / 2 * j + i, pixel);
            }
        }
    }

    private static int average4RGBA(int pixel00, int pixel01, int pixel10, int pixel11) {
        int a00 = pixel00 & 0xff;
        int a01 = pixel01 & 0xff;
        int a10 = pixel10 & 0xff;
        int a11 = pixel11 & 0xff;
        switch ((a00 << 24) | (a01 << 16) | (a10 << 8) | a11) {
            case 0xff000000:
                return pixel00;

            case 0x00ff0000:
                return pixel01;

            case 0x0000ff00:
                return pixel10;

            case 0x000000ff:
                return pixel11;

            case 0xffff0000:
                return average2RGBA(pixel00, pixel01);

            case 0xff00ff00:
                return average2RGBA(pixel00, pixel10);

            case 0xff0000ff:
                return average2RGBA(pixel00, pixel11);

            case 0x00ffff00:
                return average2RGBA(pixel01, pixel10);

            case 0x00ff00ff:
                return average2RGBA(pixel01, pixel11);

            case 0x0000ffff:
                return average2RGBA(pixel10, pixel11);

            case 0x00000000:
            case 0xffffffff:
                return average2RGBA(average2RGBA(pixel00, pixel11), average2RGBA(pixel01, pixel10));

            default:
                int a = a00 + a01 + a10 + a11;
                int pixel = a >> 2;
                for (int i = 8; i < 32; i += 8) {
                    int average = (a00 * ((pixel00 >> i) & 0xff) + a01 * ((pixel01 >> i) & 0xff)
                        + a10 * ((pixel10 >> i) & 0xff)
                        + a11 * ((pixel11 >> i) & 0xff)) / a;
                    pixel |= (average << i);
                }
                return pixel;
        }
    }

    private static int average2RGBA(int a, int b) {
        return (((a & 0xfefefefe) >>> 1) + ((b & 0xfefefefe) >>> 1)) | (a & b & 0x01010101);
    }

    private static void checkGLError(String format, Object... params) {
        int error = GL11.glGetError();
        if (error != 0) {
            String message = GLU.gluErrorString(error) + ": " + String.format(format, params);
            new RuntimeException(message).printStackTrace();
        }
    }
}
