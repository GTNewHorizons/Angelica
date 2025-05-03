package com.gtnewhorizons.angelica.glsm.managers;

import com.gtnewhorizons.angelica.config.AngelicaConfig;
import com.gtnewhorizons.angelica.glsm.GLStateManager;
import com.gtnewhorizons.angelica.glsm.stacks.IntegerStateStack;
import com.gtnewhorizons.angelica.glsm.states.TextureBinding;
import com.gtnewhorizons.angelica.glsm.states.TextureUnitArray;
import com.gtnewhorizons.angelica.glsm.texture.TextureInfo;
import com.gtnewhorizons.angelica.glsm.texture.TextureInfoCache;
import com.gtnewhorizons.angelica.glsm.texture.TextureTracker;
import net.coderbot.iris.texture.pbr.PBRTextureManager;
import org.lwjgl.opengl.ARBMultitexture;
import org.lwjgl.opengl.EXTTextureFilterAnisotropic;
import org.lwjgl.opengl.GL11;
import org.lwjgl.opengl.GL20;
import org.lwjgl.opengl.GL33C;

import java.nio.ByteBuffer;
import java.nio.DoubleBuffer;
import java.nio.FloatBuffer;
import java.nio.IntBuffer;
import java.nio.ShortBuffer;
import java.util.function.IntSupplier;

@SuppressWarnings("unused") // Entrypoint via ASM
public class GLTextureManager {

    // NOTE: These are set as part of static initialization and require a valid active OpenGL context
    public static final int MAX_TEXTURE_STACK_DEPTH = GL11.glGetInteger(GL11.GL_MAX_TEXTURE_STACK_DEPTH);
    public static final int MAX_TEXTURE_UNITS = GL11.glGetInteger(GL20.GL_MAX_TEXTURE_IMAGE_UNITS);

    public static final TextureInfoCache textureCache = new TextureInfoCache();

    public static final TextureUnitArray textures = new TextureUnitArray();
    public static final IntegerStateStack activeTextureUnit = new IntegerStateStack(0);

    public static void reset() {
        textureCache.reset();
    }


    public static void glTexImage2D(int target, int level, int internalformat, int width, int height, int border, int format, int type, IntBuffer pixels) {
        textureCache.onTexImage2D(target, level, internalformat, width, height, border, format, type, pixels);
        GL33C.glTexImage2D(target, level, internalformat, width, height, border, format, type, pixels);
    }

    public static void glTexImage2D(int target, int level, int internalformat, int width, int height, int border, int format, int type, ByteBuffer pixels) {
        textureCache.onTexImage2D(target, level, internalformat, width, height, border, format, type, pixels != null ? pixels.asIntBuffer() : (IntBuffer) null);
        GL33C.glTexImage2D(target, level, internalformat, width, height, border, format, type, pixels);
    }


    public static void glTexImage2D(int target, int level, int internalformat, int width, int height, int border, int format, int type, FloatBuffer pixels) {
        textureCache.onTexImage2D(target, level, internalformat, width, height, border, format, type, pixels);
        GL33C.glTexImage2D(target, level, internalformat, width, height, border, format, type, pixels);
    }

    public static void glTexImage2D(int target, int level, int internalformat, int width, int height, int border, int format, int type, DoubleBuffer pixels) {
        textureCache.onTexImage2D(target, level, internalformat, width, height, border, format, type, pixels);
        GL33C.glTexImage2D(target, level, internalformat, width, height, border, format, type, pixels);
    }

    public static void glTexImage2D(int target, int level, int internalformat, int width, int height, int border, int format, int type, long pixels_buffer_offset) {
        textureCache.onTexImage2D(target, level, internalformat, width, height, border, format, type, pixels_buffer_offset);
        GL33C.glTexImage2D(target, level, internalformat, width, height, border, format, type, pixels_buffer_offset);
    }


    public static void glDeleteTextures(int id) {
        onDeleteTexture(id);

        GL33C.glDeleteTextures(id);
    }

    public static void glDeleteTextures(IntBuffer ids) {
        for(int i = 0; i < ids.remaining(); i++) {
            onDeleteTexture(ids.get(i));
        }

        GL33C.glDeleteTextures(ids);
    }

    // Iris Functions
    private static void onDeleteTexture(int id) {
        TextureTracker.INSTANCE.onDeleteTexture(id);
        textureCache.onDeleteTexture(id);
        if (AngelicaConfig.enableIris) {
            PBRTextureManager.INSTANCE.onDeleteTexture(id);
        }

        for(int i = 0; i < MAX_TEXTURE_UNITS; i++) {
            if(textures.getTextureUnitBindings(i).getBinding() == id) {
                textures.getTextureUnitBindings(i).setBinding(0);
            }
        }
    }

    // Textures
    public static void glActiveTexture(int texture) {
        final int newTexture = texture - GL33C.GL_TEXTURE0;
        if (GLStateManager.shouldBypassCache() || GLStateManager.getActiveTextureUnit() != newTexture) {
            activeTextureUnit.setValue(newTexture);
            GL33C.glActiveTexture(texture);
        }
    }

    public static void glActiveTextureARB(int texture) {
        final int newTexture = texture - GL33C.GL_TEXTURE0;
        if (GLStateManager.shouldBypassCache() || GLStateManager.getActiveTextureUnit() != newTexture) {
            activeTextureUnit.setValue(newTexture);
            ARBMultitexture.glActiveTextureARB(texture);
        }
    }

    public static int getBoundTexture() {
        return getBoundTexture(activeTextureUnit.getValue());
    }

    public static int getBoundTexture(int unit) {
        return textures.getTextureUnitBindings(unit).getBinding();
    }

    public static void glBindTexture(int target, int texture) {
        if(target != GL33C.GL_TEXTURE_2D) {
            // We're only supporting 2D textures for now
            GL33C.glBindTexture(target, texture);
            return;
        }

        final TextureBinding textureUnit = textures.getTextureUnitBindings(activeTextureUnit.getValue());

        if (GLStateManager.shouldBypassCache() || textureUnit.getBinding() != texture) {
            GL33C.glBindTexture(target, texture);
            textureUnit.setBinding(texture);
            TextureTracker.INSTANCE.onBindTexture(texture);
        }
    }

    public static boolean updateTexParameteriCache(int target, int texture, int pname, int param) {
        if (target != GL33C.GL_TEXTURE_2D) {
            return true;
        }
        final TextureInfo info = textureCache.getInfo(texture);
        if (info == null) {
            return true;
        }
        switch (pname) {
            case GL33C.GL_TEXTURE_MIN_FILTER -> {
                if(info.getMinFilter() == param && !GLStateManager.shouldBypassCache()) return false;
                info.setMinFilter(param);
            }
            case GL33C.GL_TEXTURE_MAG_FILTER -> {
                if(info.getMagFilter() == param && !GLStateManager.shouldBypassCache()) return false;
                info.setMagFilter(param);
            }
            case GL33C.GL_TEXTURE_WRAP_S -> {
                if(info.getWrapS() == param && !GLStateManager.shouldBypassCache()) return false;
                info.setWrapS(param);
            }
            case GL33C.GL_TEXTURE_WRAP_T -> {
                if(info.getWrapT() == param && !GLStateManager.shouldBypassCache()) return false;
                info.setWrapT(param);
            }
            case GL33C.GL_TEXTURE_MAX_LEVEL -> {
                if(info.getMaxLevel() == param && !GLStateManager.shouldBypassCache()) return false;
                info.setMaxLevel(param);
            }
            case GL33C.GL_TEXTURE_MIN_LOD -> {
                if(info.getMinLod() == param && !GLStateManager.shouldBypassCache()) return false;
                info.setMinLod(param);
            }
            case GL33C.GL_TEXTURE_MAX_LOD -> {
                if(info.getMaxLod() == param && !GLStateManager.shouldBypassCache()) return false;
                info.setMaxLod(param);
            }
        }
        return true;
    }

    public static void glTexParameter(int target, int pname, IntBuffer params) {
        GLTextureManager.glTexParameteriv(target, pname, params);
    }

    public static void glTexParameteriv(int target, int pname, IntBuffer params) {
        if (target != GL33C.GL_TEXTURE_2D || params.remaining() != 1 ) {
            GL33C.glTexParameteriv(target, pname, params);
            return;
        }
        if (!updateTexParameteriCache(target, getBoundTexture(), pname, params.get(0))) return;

        GL33C.glTexParameteriv(target, pname, params);
    }

    public static void glTexParameter(int target, int pname, FloatBuffer params) {
        GLTextureManager.glTexParameterfv(target, pname, params);
    }

    public static void glTexParameterfv(int target, int pname, FloatBuffer params) {
        if (target != GL33C.GL_TEXTURE_2D || params.remaining() != 1 ) {
            GL33C.glTexParameterfv(target, pname, params);
            return;
        }
        if(!updateTexParameterfCache(target, getBoundTexture(), pname, params.get(0))) return;

        GL33C.glTexParameterfv(target, pname, params);
    }

    public static void glTexParameteri(int target, int pname, int param) {
        if (target != GL33C.GL_TEXTURE_2D) {
            GL33C.glTexParameteri(target, pname, param);
            return;
        }
        if(!updateTexParameteriCache(target, getBoundTexture(), pname, param)) return;

        GL33C.glTexParameteri(target, pname, param);
    }

    public static boolean updateTexParameterfCache(int target, int texture, int pname, float param) {
        if (target != GL33C.GL_TEXTURE_2D) {
            return true;
        }
        final TextureInfo info = textureCache.getInfo(texture);
        if (info == null) {
            return true;
        }
        switch (pname) {
            case EXTTextureFilterAnisotropic.GL_TEXTURE_MAX_ANISOTROPY_EXT -> {
                if(info.getMaxAnisotropy() == param && !GLStateManager.shouldBypassCache()) return false;
                info.setMaxAnisotropy(param);
            }
            case GL33C.GL_TEXTURE_LOD_BIAS -> {
                if(info.getLodBias() == param && !GLStateManager.shouldBypassCache()) return false;
                info.setLodBias(param);
            }
        }
        return true;
    }

    public static void glTexParameterf(int target, int pname, float param) {
        if (target != GL33C.GL_TEXTURE_2D) {
            GL33C.glTexParameterf(target, pname, param);
            return;
        }
        if(!updateTexParameterfCache(GLStateManager.getActiveTextureUnit(), target, pname, param)) return;

        GL33C.glTexParameterf(target, pname, param);
    }

    public static int getTexParameterOrDefault(int texture, int pname, IntSupplier defaultSupplier) {
        final TextureInfo info = textureCache.getInfo(texture);
        if (info == null) {
            return defaultSupplier.getAsInt();
        }
        return switch (pname) {
            case GL33C.GL_TEXTURE_MIN_FILTER -> info.getMinFilter();
            case GL33C.GL_TEXTURE_MAG_FILTER -> info.getMagFilter();
            case GL33C.GL_TEXTURE_WRAP_S -> info.getWrapS();
            case GL33C.GL_TEXTURE_WRAP_T -> info.getWrapT();
            case GL33C.GL_TEXTURE_MAX_LEVEL -> info.getMaxLevel();
            case GL33C.GL_TEXTURE_MIN_LOD -> info.getMinLod();
            case GL33C.GL_TEXTURE_MAX_LOD -> info.getMaxLod();
            default -> defaultSupplier.getAsInt();
        };
    }

    public static int glGetTexParameteri(int target, int pname) {
        if (target != GL33C.GL_TEXTURE_2D || GLStateManager.shouldBypassCache()) {
            return GL33C.glGetTexParameteri(target, pname);
        }
        return getTexParameterOrDefault(getBoundTexture(), pname, () -> GL33C.glGetTexParameteri(target, pname));
    }

    public static float glGetTexParameterf(int target, int pname) {
        if (target != GL33C.GL_TEXTURE_2D || GLStateManager.shouldBypassCache()) {
            return GL33C.glGetTexParameterf(target, pname);
        }
        final TextureInfo info = textureCache.getInfo(getBoundTexture());
        if(info == null) {
            return GL33C.glGetTexParameterf(target, pname);
        }

        return switch (pname) {
            case EXTTextureFilterAnisotropic.GL_TEXTURE_MAX_ANISOTROPY_EXT -> info.getMaxAnisotropy();
            case GL33C.GL_TEXTURE_LOD_BIAS -> info.getLodBias();
            default -> GL33C.glGetTexParameterf(target, pname);
        };
    }

    public static int glGetTexLevelParameteri(int target, int level, int pname) {
        if (target != GL33C.GL_TEXTURE_2D || GLStateManager.shouldBypassCache()) {
            return GL33C.glGetTexLevelParameteri(target, level, pname);
        }
        final TextureInfo info = textureCache.getInfo(getBoundTexture());
        if (info == null) {
            return GL33C.glGetTexLevelParameteri(target, level, pname);
        }
        return switch (pname) {
            case GL33C.GL_TEXTURE_WIDTH -> info.getWidth();
            case GL33C.GL_TEXTURE_HEIGHT -> info.getHeight();
            case GL33C.GL_TEXTURE_INTERNAL_FORMAT -> info.getInternalFormat();
            default -> GL33C.glGetTexLevelParameteri(target, level, pname);
        };
    }

    /*
     * Legacy FFP functions that need to be emulated
     */

    public static void glTexCoordPointer(int size, int type, int stride, long pointer_buffer_offset) {
        GL11.glTexCoordPointer(size, type, stride, pointer_buffer_offset);
    }

    public static void glTexCoordPointer(int size, int type, int stride, ByteBuffer pointer) {
        GL11.glTexCoordPointer(size, type, stride, pointer);
    }

    public static void glTexCoordPointer(int size, int type, int stride, FloatBuffer pointer) {
        GL11.glTexCoordPointer(size, type, stride, pointer);
    }

    public static void glTexCoordPointer(int size, int type, int stride, IntBuffer pointer) {
        GL11.glTexCoordPointer(size, type, stride, pointer);
    }

    public static void glTexCoordPointer(int size, int type, int stride, ShortBuffer pointer) {
        GL11.glTexCoordPointer(size, type, stride, pointer);
    }

    public static void glTexCoord1f(float s) {
        GL11.glTexCoord1f(s);
    }

    public static void glTexCoord1d(double s) {
        GL11.glTexCoord1d(s);
    }

    public static void glTexCoord2f(float s, float t) {
        GL11.glTexCoord2f(s, t);
    }

    public static void glTexCoord2d(double s, double t) {
        GL11.glTexCoord2d(s, t);
    }

    public static void glTexCoord3f(float s, float t, float r) {
        GL11.glTexCoord3f(s, t, r);
    }

    public static void glTexCoord3d(double s, double t, double r) {
        GL11.glTexCoord3d(s, t, r);
    }

    public static void glTexCoord4f(float s, float t, float r, float q) {
        GL11.glTexCoord4f(s, t, r, q);
    }

    public static void glTexCoord4d(double s, double t, double r, double q) {
        GL11.glTexCoord4d(s, t, r, q);
    }

}
