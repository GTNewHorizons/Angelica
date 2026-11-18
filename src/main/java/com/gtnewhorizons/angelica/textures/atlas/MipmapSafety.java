package com.gtnewhorizons.angelica.textures.atlas;

import net.minecraft.client.renderer.texture.TextureAtlasSprite;

import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.util.List;

final class MipmapSafety {

    private static final Method SET_FRAMES = resolveSetFrames();

    private static final ClassValue<Boolean> CACHE = new ClassValue<Boolean>() {

        @Override
        protected Boolean computeValue(Class<?> type) {
            if (SET_FRAMES == null) {
                return false;
            }
            for (Class<?> c = type; c != null && c != TextureAtlasSprite.class; c = c.getSuperclass()) {
                for (Method m : c.getDeclaredMethods()) {
                    final String name = m.getName();
                    final Class<?>[] params = m.getParameterTypes();
                    if ((name.equals("generateMipmaps") || name.equals("func_147963_d")) && params.length == 1
                        && params[0] == int.class) {
                        return false;
                    }
                    if (name.equals(SET_FRAMES.getName()) && params.length == 1 && params[0] == List.class) {
                        return false;
                    }
                }
            }
            return true;
        }
    };

    private MipmapSafety() {
    }

    public static boolean isSafe(Class<?> spriteClass) {
        if (spriteClass == TextureAtlasSprite.class) {
            return true;
        }
        return CACHE.get(spriteClass);
    }

    private static Method resolveSetFrames() {
        Method found = null;
        for (Method m : TextureAtlasSprite.class.getDeclaredMethods()) {
            final Class<?>[] params = m.getParameterTypes();
            if (m.getReturnType() == void.class && params.length == 1 && params[0] == List.class
                && !Modifier.isStatic(m.getModifiers())) {
                if (found != null) {
                    return null;
                }
                found = m;
            }
        }
        return found;
    }
}
